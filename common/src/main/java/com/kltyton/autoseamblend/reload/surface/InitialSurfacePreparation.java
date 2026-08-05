package com.kltyton.autoseamblend.reload.surface;

import com.kltyton.autoseamblend.inference.InferenceFacts;
import com.kltyton.autoseamblend.mixin.minecraft.ModelManagerInvoker;
import com.kltyton.autoseamblend.runtime.surface.SurfacePreparationDomain;
import com.kltyton.autoseamblend.runtime.surface.SurfacePreparationDomain.FaceInput;
import com.kltyton.autoseamblend.runtime.surface.SurfacePreparationDomain.StateInput;
import com.kltyton.autoseamblend.runtime.surface.SurfaceSourceSnapshot;
import com.kltyton.autoseamblend.runtime.surface.ModelGeometryInspector;
import com.kltyton.autoseamblend.engine.query.SurfaceFace;
import com.kltyton.autoseamblend.texture.atlas.InitialBlockAtlasResources.Snapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.client.resources.model.ModelDiscovery;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.cuboid.MissingCuboidModel;
import net.minecraft.client.resources.model.cuboid.UnbakedCuboidGeometry;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：在 Atlas 缝合前从原始资源解析源精灵和完整推断事实。
 * <p>
 * English:
 * Resolves source sprites and complete inference facts from raw resources before atlas stitching.
 */
public final class InitialSurfacePreparation {
    private static final int MIN_STATES_PER_TASK = 512;
    private static final int MAX_INSPECTION_TASKS = 8;
    private static final int INSPECTION_PARALLELISM = Math.max(
            1,
            Math.min(
                    MAX_INSPECTION_TASKS,
                    Runtime.getRuntime().availableProcessors()));

    private InitialSurfacePreparation() {
    }

    public static CompletableFuture<Result> prepare(
            ResourceManager resources,
            Executor executor,
            Predicate<SpriteSource> generatedSourcePredicate) {
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(generatedSourcePredicate, "generatedSourcePredicate");
        CompletableFuture<BlockStateModelLoader.LoadedModels> blockStates =
                BlockStateModelLoader.loadBlockStates(resources, executor);
        CompletableFuture<Map<Identifier, UnbakedModel>> blockModels =
                ModelManagerInvoker.autoseamblend$loadBlockModels(resources, executor);
        CompletableFuture<Snapshot> atlas =
                CompletableFuture.supplyAsync(
                        () -> com.kltyton.autoseamblend.texture.atlas.InitialBlockAtlasResources.capture(
                                resources,
                                generatedSourcePredicate),
                        executor);
        return blockStates
                .thenCombine(blockModels, Models::new)
                .thenCombine(
                        atlas,
                        ResolutionInputs::new)
                .thenComposeAsync(
                        inputs -> resolveAsync(
                                inputs.models().blockStates(),
                                inputs.models().blockModels(),
                                inputs.atlas(),
                                executor),
                        executor);
    }

    private static CompletableFuture<Result> resolveAsync(
            BlockStateModelLoader.LoadedModels blockStates,
            Map<Identifier, UnbakedModel> blockModels,
            Snapshot atlas,
            Executor executor) {
        ModelDiscovery discovery =
                new ModelDiscovery(blockModels, MissingCuboidModel.missingModel());
        blockStates.models().values().forEach(discovery::addRoot);
        Map<Identifier, ResolvedModel> resolvedModels = discovery.resolve();
        List<Map.Entry<BlockState, BlockStateModel.UnbakedRoot>> entries =
                List.copyOf(blockStates.models().entrySet());
        if (entries.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new Result(List.of(), List.of(), atlas, List.of()));
        }
        int taskCount = Math.min(
                INSPECTION_PARALLELISM,
                Math.max(
                        1,
                        (entries.size() + MIN_STATES_PER_TASK - 1)
                                / MIN_STATES_PER_TASK));
        int batchSize = (entries.size() + taskCount - 1)
                / taskCount;
        ArrayList<CompletableFuture<InspectionBatch>> tasks =
                new ArrayList<>(taskCount);
        for (int start = 0; start < entries.size(); start += batchSize) {
            int from = start;
            int to = Math.min(entries.size(), start + batchSize);
            tasks.add(CompletableFuture.supplyAsync(
                    () -> inspectBatch(
                            entries,
                            from,
                            to,
                            resolvedModels,
                            atlas),
                    executor));
        }
        return CompletableFuture
                .allOf(tasks.toArray(CompletableFuture<?>[]::new))
                .thenApply(ignored -> merge(tasks, atlas));
    }

    private static InspectionBatch inspectBatch(
            List<Map.Entry<BlockState, BlockStateModel.UnbakedRoot>> entries,
            int from,
            int to,
            Map<Identifier, ResolvedModel> resolvedModels,
            Snapshot atlas) {
        ArrayList<Surface> surfaces = new ArrayList<>();
        ArrayList<StateCandidate> candidates =
                new ArrayList<>(to - from);
        ArrayList<String> diagnostics = new ArrayList<>();
        for (int index = from; index < to; index++) {
            Map.Entry<BlockState, BlockStateModel.UnbakedRoot> entry =
                    entries.get(index);
            StateInspection inspection = inspectState(
                    entry.getKey(),
                    entry.getValue(),
                    resolvedModels,
                    atlas);
            surfaces.addAll(inspection.surfaces());
            candidates.add(inspection.candidate());
            inspection.diagnostics().forEach(reason ->
                    diagnostics.add("INITIAL_SURFACE_EVIDENCE:"
                            + entry.getKey()
                            + ':'
                            + reason));
        }
        return new InspectionBatch(
                surfaces,
                candidates,
                diagnostics);
    }

    private static Result merge(
            List<CompletableFuture<InspectionBatch>> tasks,
            Snapshot atlas) {
        ArrayList<Surface> surfaces = new ArrayList<>();
        ArrayList<StateCandidate> candidates = new ArrayList<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        for (CompletableFuture<InspectionBatch> task : tasks) {
            InspectionBatch batch = task.join();
            surfaces.addAll(batch.surfaces());
            candidates.addAll(batch.candidates());
            diagnostics.addAll(batch.diagnostics());
        }
        return new Result(
                surfaces,
                candidates,
                atlas,
                diagnostics);
    }

    private static StateInspection inspectState(
            BlockState state,
            BlockStateModel.UnbakedRoot root,
            Map<Identifier, ResolvedModel> resolvedModels,
            Snapshot atlas) {
        LinkedHashSet<Identifier> dependencies = new LinkedHashSet<>();
        root.resolveDependencies(dependencies::add);
        ArrayList<String> evidence = new ArrayList<>();
        if (dependencies.isEmpty()) {
            evidence.add("BLOCKSTATE_ROOT_HAS_NO_MODEL_DEPENDENCIES");
            return StateInspection.unresolved(state, evidence);
        }
        ArrayList<Draft> drafts = new ArrayList<>();
        boolean completeModelGeometry = true;
        boolean completeSurfaceEvidence = true;
        for (Identifier dependency : dependencies) {
            ResolvedModel resolved = resolvedModels.get(dependency);
            if (resolved == null) {
                completeModelGeometry = false;
                completeSurfaceEvidence = false;
                evidence.add("UNRESOLVED_MODEL_SKIPPED:" + dependency);
                continue;
            }
            if (!(resolved.getTopGeometry() instanceof UnbakedCuboidGeometry geometry)) {
                completeModelGeometry = false;
                completeSurfaceEvidence = false;
                evidence.add("CUSTOM_MODEL_DEFERRED_TO_BAKED_SURFACE:" + dependency);
                continue;
            }
            GeometryInspection inspected = inspectGeometry(
                    dependency,
                    resolved.getTopTextureSlots(),
                    geometry,
                    atlas);
            drafts.addAll(inspected.drafts());
            evidence.addAll(inspected.diagnostics());
            completeSurfaceEvidence &= inspected.complete();
        }
        if (drafts.isEmpty()) {
            evidence.add("NO_RESOLVED_CUBOID_SURFACES");
            return StateInspection.unresolved(state, evidence);
        }
        List<FaceInput> faces = drafts.stream()
                .map(draft -> {
                    return new FaceInput(
                            SurfaceFace.valueOf(draft.direction().name()),
                            draft.source(),
                            draft.fullBlock(),
                            draft.axisAligned(),
                            draft.fullFace(),
                            draft.validUv(),
                            draft.tintIndex());
                })
                .toList();
        SurfacePreparationDomain.StateInspection inspected =
                SurfacePreparationDomain.inspect(new StateInput(
                        state.toString(),
                        state.toString(),
                        faces,
                        completeModelGeometry,
                        completeSurfaceEvidence,
                        evidence));
        List<Surface> surfaces = inspected.surfaces().stream()
                .map(surface -> new Surface(
                        state,
                        Direction.valueOf(surface.face().name()),
                        surface.source(),
                        surface.inferenceFacts()))
                .toList();
        CandidateStatus status = CandidateStatus.valueOf(
                inspected.candidate().status().name());
        return new StateInspection(
                surfaces,
                new StateCandidate(
                        state,
                        status,
                        inspected.candidate().evidence()),
                inspected.diagnostics());
    }

    private static GeometryInspection inspectGeometry(
            Identifier dependency,
            TextureSlots textures,
            UnbakedCuboidGeometry geometry,
            Snapshot atlas) {
        ModelGeometryInspector.Result inspected = ModelGeometryInspector.inspect(
                dependency,
                textures,
                geometry,
                atlas);
        List<Draft> drafts = inspected.faces().stream()
                .map(face -> new Draft(
                        face.direction(),
                        face.source(),
                        face.fullBlock(),
                        face.axisAligned(),
                        face.fullFace(),
                        face.validUv(),
                        face.tintIndex()))
                .toList();
        return new GeometryInspection(
                drafts,
                inspected.complete(),
                inspected.diagnostics());
    }

    public record Result(
            List<Surface> surfaces,
            List<StateCandidate> candidates,
            Snapshot atlas,
            List<String> diagnostics) {
        public Result {
            surfaces = List.copyOf(Objects.requireNonNull(surfaces, "surfaces"));
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
            Objects.requireNonNull(atlas, "atlas");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    /**
     * 中文：标准方块状态模型表中的候选即使只能部分解析，也作为显式证据保留。
     * <p>
     * English:
     * Candidates from the standard block-state model table remain explicit even when only partially
     * resolvable before stitching.
     */
    public record StateCandidate(
            BlockState state,
            CandidateStatus status,
            List<String> evidence) {
        public StateCandidate {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(status, "status");
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
            if (evidence.isEmpty()) {
                throw new IllegalArgumentException(
                        "surface candidate must retain evidence");
            }
        }
    }

    public enum CandidateStatus {
        PREPARED,
        PARTIAL,
        UNRESOLVED
    }

    public record Surface(
            BlockState state,
            Direction direction,
            SurfaceSourceSnapshot source,
            InferenceFacts inferenceFacts) {
        public Surface {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(inferenceFacts, "inferenceFacts");
        }
    }

    private record Models(
            BlockStateModelLoader.LoadedModels blockStates,
            Map<Identifier, UnbakedModel> blockModels) {
        private Models {
            Objects.requireNonNull(blockStates, "blockStates");
            blockModels = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(blockModels, "blockModels")));
        }
    }

    private record ResolutionInputs(
            Models models,
            Snapshot atlas) {
        private ResolutionInputs {
            Objects.requireNonNull(models, "models");
            Objects.requireNonNull(atlas, "atlas");
        }
    }

    private record InspectionBatch(
            List<Surface> surfaces,
            List<StateCandidate> candidates,
            List<String> diagnostics) {
        private InspectionBatch {
            Objects.requireNonNull(surfaces, "surfaces");
            Objects.requireNonNull(candidates, "candidates");
            Objects.requireNonNull(diagnostics, "diagnostics");
        }
    }

    private record StateInspection(
            List<Surface> surfaces,
            StateCandidate candidate,
            List<String> diagnostics) {
        private StateInspection {
            surfaces = List.copyOf(Objects.requireNonNull(surfaces, "surfaces"));
            Objects.requireNonNull(candidate, "candidate");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }

        private static StateInspection unresolved(
                BlockState state,
                List<String> evidence) {
            return new StateInspection(
                    List.of(),
                    new StateCandidate(
                            state,
                            CandidateStatus.UNRESOLVED,
                            evidence),
                    evidence);
        }
    }

    private record GeometryInspection(
            List<Draft> drafts,
            boolean complete,
            List<String> diagnostics) {
        private GeometryInspection {
            drafts = List.copyOf(Objects.requireNonNull(drafts, "drafts"));
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    private record Draft(
            Direction direction,
            SurfaceSourceSnapshot source,
            boolean fullBlock,
            boolean axisAligned,
            boolean fullFace,
            boolean validUv,
            int tintIndex) {
        private Draft {
            if (tintIndex < -1) {
                throw new IllegalArgumentException("tintIndex must be -1 or non-negative");
            }
        }
    }

}
