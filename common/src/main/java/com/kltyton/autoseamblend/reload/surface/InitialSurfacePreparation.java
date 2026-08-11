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

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.MultiVariant;
import net.minecraft.client.renderer.block.model.Variant;
import net.minecraft.client.renderer.block.model.multipart.MultiPart;
import net.minecraft.client.renderer.block.model.multipart.Selector;
import net.minecraft.client.resources.model.BlockStateModelLoader;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

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
        CompletableFuture<Map<ResourceLocation, BlockModel>> blockModels =
                ModelManagerInvoker.autoseamblend$loadBlockModels(resources, executor)
                        .thenApply(InitialSurfacePreparation::normalizeModelKeys);
        CompletableFuture<Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>>> blockStates =
                ModelManagerInvoker.autoseamblend$loadBlockStates(resources, executor);
        CompletableFuture<Snapshot> atlas =
                CompletableFuture.supplyAsync(
                        () -> com.kltyton.autoseamblend.texture.atlas.InitialBlockAtlasResources.capture(
                                resources,
                                generatedSourcePredicate),
                        executor);
        return blockModels
                .thenCombine(blockStates, Models::new)
                .thenCombine(
                        atlas,
                        ResolutionInputs::new)
                .thenComposeAsync(
                        inputs -> resolveAsync(
                                inputs.models().blockModels(),
                                inputs.models().blockStateJson(),
                                inputs.atlas(),
                                executor),
                        executor);
    }

    /**
     * 中文：1.21.1 的 ModelManager.loadBlockModels 直接使用 FileToIdConverter.listMatchingResources
     * 的原始键（如 "minecraft:models/block/cube_all.json"），而本域后续全部按模型 ID
     * （"minecraft:block/cube_all"）查询，因此在这里统一转换为 ID 键。
     *
     * English: In 1.21.1 ModelManager.loadBlockModels keeps FileToIdConverter.listMatchingResources
     * raw keys (e.g. "minecraft:models/block/cube_all.json"), while this domain queries models by
     * model ID ("minecraft:block/cube_all"), so keys are normalized to IDs here.
     */
    private static Map<ResourceLocation, BlockModel> normalizeModelKeys(
            Map<ResourceLocation, BlockModel> raw) {
        Objects.requireNonNull(raw, "raw");
        if (raw.isEmpty()) {
            return raw;
        }
        Map<ResourceLocation, BlockModel> normalized =
                new HashMap<>(raw.size());
        for (Map.Entry<ResourceLocation, BlockModel> entry
                : raw.entrySet()) {
            normalized.put(
                    ModelBakery.MODEL_LISTER.fileToId(
                            entry.getKey()),
                    entry.getValue());
        }
        return normalized;
    }

    private static CompletableFuture<Result> resolveAsync(
            Map<ResourceLocation, BlockModel> blockModels,
            Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>> blockStateJson,
            Snapshot atlas,
            Executor executor) {
        List<StateModelEntry> entries = resolveStateModels(
                blockModels,
                blockStateJson);
        resolveModelParents(blockModels);
        if (entries.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new Result(List.of(), List.of(), atlas, List.of()));
        }
        // 中文：单次 resolveAsync 边界内 blockModels 与 atlas 均已冻结：同一 dependency 恒
        // 映射同一 BlockModel 实例与同一 atlas 快照，因此以 dependency 为键即可复现
        // inspectGeometry 的全部输出；该 map 是本调用局部变量，随 resolveAsync 结束即弃，
        // 绝不跨 reload 复用。
        // English: Inside one resolveAsync boundary both blockModels and atlas are frozen: the
        // same dependency always maps to the same BlockModel instance and the same atlas
        // snapshot, so dependency alone reproduces every inspectGeometry output. The map is
        // local to this call and is discarded when resolveAsync returns; it is never reused
        // across reloads.
        ConcurrentMap<ResourceLocation, ModelGeometryInspector.Result> geometryResults =
                new ConcurrentHashMap<>();
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
                            blockModels,
                            atlas,
                            geometryResults),
                    executor));
        }
        return CompletableFuture
                .allOf(tasks.toArray(CompletableFuture<?>[]::new))
                .thenApply(ignored -> merge(tasks, atlas));
    }

    private static InspectionBatch inspectBatch(
            List<StateModelEntry> entries,
            int from,
            int to,
            Map<ResourceLocation, BlockModel> blockModels,
            Snapshot atlas,
            ConcurrentMap<ResourceLocation, ModelGeometryInspector.Result> geometryResults) {
        ArrayList<Surface> surfaces = new ArrayList<>();
        ArrayList<StateCandidate> candidates =
                new ArrayList<>(to - from);
        ArrayList<String> diagnostics = new ArrayList<>();
        for (int index = from; index < to; index++) {
            StateModelEntry entry =
                    entries.get(index);
            StateInspection inspection = inspectState(
                    entry.state(),
                    entry.models(),
                    blockModels,
                    atlas,
                    geometryResults);
            surfaces.addAll(inspection.surfaces());
            candidates.add(inspection.candidate());
            inspection.diagnostics().forEach(reason ->
                    diagnostics.add("INITIAL_SURFACE_EVIDENCE:"
                            + entry.state()
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
            List<ResourceLocation> modelLocations,
            Map<ResourceLocation, BlockModel> blockModels,
            Snapshot atlas,
            ConcurrentMap<ResourceLocation, ModelGeometryInspector.Result> geometryResults) {
        ArrayList<String> evidence = new ArrayList<>();
        if (modelLocations.isEmpty()) {
            evidence.add("BLOCKSTATE_ROOT_HAS_NO_MODEL_DEPENDENCIES");
            return StateInspection.unresolved(state, evidence);
        }
        ArrayList<Draft> drafts = new ArrayList<>();
        boolean completeModelGeometry = true;
        boolean completeSurfaceEvidence = true;
        LinkedHashSet<ResourceLocation> visited = new LinkedHashSet<>();
        for (ResourceLocation dependency : modelLocations) {
            if (!visited.add(dependency)) {
                continue;
            }
            BlockModel model = blockModels.get(dependency);
            if (model == null) {
                completeModelGeometry = false;
                completeSurfaceEvidence = false;
                evidence.add("UNRESOLVED_MODEL_SKIPPED:" + dependency);
                continue;
            }
            if (model.getElements().isEmpty()) {
                completeModelGeometry = false;
                completeSurfaceEvidence = false;
                evidence.add("CUSTOM_MODEL_DEFERRED_TO_BAKED_SURFACE:" + dependency);
                continue;
            }
            GeometryInspection inspected = inspectGeometry(
                    dependency,
                    model,
                    atlas,
                    geometryResults);
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

    static GeometryInspection inspectGeometry(
            ResourceLocation dependency,
            BlockModel model,
            Snapshot atlas,
            ConcurrentMap<ResourceLocation, ModelGeometryInspector.Result> geometryResults) {
        Objects.requireNonNull(geometryResults, "geometryResults");
        // 中文：同一 dependency 的检查只执行一次；computeIfAbsent 的映射函数捕获的 model
        // 与 atlas 在该 resolveAsync 边界内恒定（见 resolveAsync 处注释），因此按
        // dependency 单键缓存即可复现原输出，且并发批次间共享同一不可变 Result。
        // English: The inspection for one dependency runs exactly once; the model and atlas
        // captured by the mapping function are constant inside this resolveAsync boundary
        // (see the comment at resolveAsync), so the single dependency key reproduces the
        // original output and concurrent batches share one immutable Result.
        ModelGeometryInspector.Result inspected =
                geometryResults.computeIfAbsent(
                        dependency,
                        key -> ModelGeometryInspector.inspect(
                                key,
                                model,
                                atlas));
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

    /**
     * 中文：用原版 1.21.1 BlockStateModelLoader 把每个方块状态解析成其模型 JSON 引用列表，
     * 再在加载器回调中收集实际 BlockModel。
     *
     * English: Uses vanilla 1.21.1 BlockStateModelLoader to resolve every block state to its
     * model references, then collects the concrete BlockModels from the loader callback.
     */
    private static List<StateModelEntry> resolveStateModels(
            Map<ResourceLocation, BlockModel> blockModels,
            Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>> blockStateJson) {
        LinkedHashMap<BlockState, LinkedHashSet<ResourceLocation>> modelIdsByState =
                new LinkedHashMap<>();
        Map<ModelResourceLocation, BlockState> locationToState =
                new HashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                modelIdsByState.put(
                        state,
                        new LinkedHashSet<>());
                locationToState.put(
                        BlockModelShaper.stateToModelLocation(state),
                        state);
            }
        }
        BlockStateModelLoader loader = new BlockStateModelLoader(
                blockStateJson,
                InactiveProfiler.INSTANCE,
                BlockModel.fromString(ModelBakery.MISSING_MODEL_MESH),
                Minecraft.getInstance().getBlockColors(),
                (location, unbaked) -> {
                    BlockState state = locationToState.get(location);
                    if (state == null) {
                        return;
                    }
                    LinkedHashSet<ResourceLocation> ids =
                            modelIdsByState.get(state);
                    if (ids == null) {
                        return;
                    }
                    collectModelLocations(
                            unbaked,
                            state,
                            ids);
                });
        loader.loadAllBlockStates();
        StateModelIndex.publish(
                StateModelIndex.invert(modelIdsByState));
        ArrayList<StateModelEntry> entries = new ArrayList<>();
        for (Map.Entry<BlockState, LinkedHashSet<ResourceLocation>> entry
                : modelIdsByState.entrySet()) {
            BlockState state = entry.getKey();
            LinkedHashSet<ResourceLocation> ids = entry.getValue();
            List<ResourceLocation> resolved = ids.stream()
                    .filter(blockModels::containsKey)
                    .toList();
            if (resolved.isEmpty()) {
                entries.add(new StateModelEntry(
                        state,
                        List.of()));
                continue;
            }
            entries.add(new StateModelEntry(
                    state,
                    resolved));
        }
        return entries;
    }

    /**
     * 中文：预先解析所有 BlockModel 的 parent 链，使 getElements()/getMaterial() 在检查时
     * 与烘焙语义一致。
     *
     * English: Resolves every BlockModel parent chain up front so getElements()/getMaterial()
     * match baking semantics during inspection.
     */
    private static void resolveModelParents(
            Map<ResourceLocation, BlockModel> blockModels) {
        // 中文：原版烘焙用 ModelBakery.getModel 把缺失父模型回退到 builtin/missing；
        // 直接 map.get 返回 null 会让 BlockModel.resolveParents 抛出
        // "BlockModel parent has to be a block model."（例如 item/generated 等特殊父模型）。
        // English: Vanilla baking falls back to the builtin missing model for unresolved
        // parents; a bare map.get returning null makes BlockModel.resolveParents throw
        // "BlockModel parent has to be a block model." (e.g. special parents like
        // item/generated), so mirror the vanilla fallback here.
        BlockModel missing = BlockModel.fromString(
                ModelBakery.MISSING_MODEL_MESH);
        Map<ResourceLocation, BlockModel> resolvable =
                new HashMap<>(blockModels);
        resolvable.put(
                ModelBakery.MISSING_MODEL_LOCATION,
                missing);
        java.util.function.Function<ResourceLocation, UnbakedModel> resolver =
                resolvable::get;
        for (BlockModel model : blockModels.values()) {
            model.resolveParents(resolver);
        }
    }

    private static void collectModelLocations(
            UnbakedModel unbaked,
            BlockState state,
            LinkedHashSet<ResourceLocation> output) {
        if (unbaked instanceof MultiVariant multiVariant) {
            for (Variant variant : multiVariant.getVariants()) {
                output.add(variant.getModelLocation());
            }
        } else if (unbaked instanceof MultiPart multiPart) {
            for (Selector selector : multiPart.getSelectors()) {
                if (selector.getPredicate(
                        state.getBlock().getStateDefinition()).test(state)) {
                    for (Variant variant
                            : selector.getVariant().getVariants()) {
                        output.add(variant.getModelLocation());
                    }
                }
            }
        }
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
            Map<ResourceLocation, BlockModel> blockModels,
            Map<ResourceLocation, List<BlockStateModelLoader.LoadedJson>> blockStateJson) {
        private Models {
            blockModels = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(blockModels, "blockModels")));
            blockStateJson = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(blockStateJson, "blockStateJson")));
        }
    }

    private record StateModelEntry(
            BlockState state,
            List<ResourceLocation> models) {
        private StateModelEntry {
            Objects.requireNonNull(state, "state");
            models = List.copyOf(Objects.requireNonNull(models, "models"));
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
