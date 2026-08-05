package com.kltyton.autoseamblend.authoring.export;

import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringDraft;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToIntFunction;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：导出草稿的公共排序、身份与表面规划；Loader 只注入引擎路由和 overlay 接收方事实。
 * English: Common export-draft ordering, identity, and surface planning; the Loader injects only
 * engine routing and overlay-receiver facts.
 */
public final class ExportDraftPlanning {
    private ExportDraftPlanning() {}

    public static ManagedAuthoringDraft canonicalDraft(
            ManagedAuthoringDraft draft,
            EngineQuerySelection selection,
            BlockState state,
            MinecraftSurfaceCatalog.FaceSurface surface) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(surface, "surface");
        ConnectionMethod requested = selection.resolution()
                .map(value -> value.method().requestedMethod())
                .orElse(selection.method());
        ConnectionMethod resolved = selection.resolveMethod(
                state,
                surface.direction(),
                surface.sprite().contents().name());
        return new ManagedAuthoringDraft(
                draft.targetBlockId(),
                draft.sourceTextureId(),
                draft.originalModelId(),
                requested,
                resolved,
                draft.compatibility(),
                draft.pane());
    }

    public static Optional<MinecraftSurfaceCatalog.FaceSurface>
            configuredRepresentative(
                    MinecraftSurfaceCatalog.StateSurface surface) {
        Objects.requireNonNull(surface, "surface");
        return surface.faces().values().stream()
                .flatMap(List::stream)
                .sorted(Comparator
                        .<MinecraftSurfaceCatalog.FaceSurface>comparingInt(value ->
                                value.facts().framedAlpha().isTrue() ? 0 : 1)
                        .thenComparingInt(value -> value.fullFace() ? 0 : 1)
                        .thenComparingInt(value -> value.tintIndex() >= 0 ? 0 : 1)
                        .thenComparingInt(value -> directionPriority(value.direction()))
                        .thenComparing(value ->
                                value.sprite().contents().name().toString()))
                .findFirst();
    }

    public static Optional<MinecraftSurfaceCatalog.FaceSurface>
            representativeForSource(
                    MinecraftSurfaceCatalog.StateSurface surface,
                    String sourceTextureId) {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(sourceTextureId, "sourceTextureId");
        return surface.faces().values().stream()
                .flatMap(List::stream)
                .filter(value -> value.sprite().contents().name().toString()
                        .equals(sourceTextureId))
                .sorted(Comparator
                        .<MinecraftSurfaceCatalog.FaceSurface>comparingInt(value ->
                                value.facts().framedAlpha().isTrue() ? 0 : 1)
                        .thenComparingInt(value -> value.fullFace() ? 0 : 1)
                        .thenComparingInt(value -> value.tintIndex() >= 0 ? 0 : 1)
                        .thenComparingInt(value -> directionPriority(value.direction())))
                .findFirst();
    }

    public static int directionPriority(Direction direction) {
        return switch (Objects.requireNonNull(direction, "direction")) {
            case UP -> 0;
            case NORTH, SOUTH, WEST, EAST -> 1;
            case DOWN -> 2;
        };
    }

    public static ExportSurfaceSnapshot surfaceSnapshot(
            MinecraftSurfaceCatalog.FaceSurface surface,
            EngineFamily family,
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces,
            OverlayReceiverIds overlayReceiverIds) {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(surfaces, "surfaces");
        Objects.requireNonNull(overlayReceiverIds, "overlayReceiverIds");
        return new ExportSurfaceSnapshot(
                surface.sprite().contents().name().toString(),
                surface.frameProfile(),
                surface.overlayProfile(),
                overlayReceiverIds.resolve(family, rules, surfaces));
    }

    public static List<WorkspaceTarget> mergedNativeDocuments(
            List<WorkspaceTarget> workspace) {
        Objects.requireNonNull(workspace, "workspace");
        LinkedHashMap<NativeDocumentKey, List<NativeDocumentSnapshot>> documents =
                new LinkedHashMap<>();
        for (WorkspaceTarget target : List.copyOf(workspace)) {
            target.nativeDocument().ifPresent(document ->
                    documents.computeIfAbsent(
                            new NativeDocumentKey(
                                    document.family(),
                                    document.documentPath()),
                            ignored -> new ArrayList<>())
                            .add(document));
        }
        LinkedHashMap<NativeDocumentKey, Optional<com.kltyton.autoseamblend.authoring.property.NativePropertyPatch>> merged =
                new LinkedHashMap<>();
        documents.forEach((key, values) ->
                merged.put(key, NativeDocumentSnapshot.mergePropertyPatches(values)));
        return List.copyOf(workspace).stream()
                .map(target -> new WorkspaceTarget(
                        target.draft(),
                        target.nativeDocument().map(document ->
                                document.withPropertyPatch(
                                        merged.get(new NativeDocumentKey(
                                                document.family(),
                                                document.documentPath()))))))
                .toList();
    }

    /**
     * 中文：按引擎稳定顺序冻结导出分区，避免各 Loader 重复实现排序与不可变复制。
     * English: Freezes export partitions in stable engine order so Loaders do not duplicate
     * sorting and immutable-copy semantics.
     */
    public static Map<String, List<ExportDraft>> freezePartitions(
            Map<String, ? extends List<ExportDraft>> partitions,
            ToIntFunction<String> stableOrder) {
        Objects.requireNonNull(partitions, "partitions");
        Objects.requireNonNull(stableOrder, "stableOrder");
        LinkedHashMap<String, List<ExportDraft>> frozen = new LinkedHashMap<>();
        partitions.entrySet().stream()
                .sorted(Comparator.comparingInt(entry ->
                        stableOrder.applyAsInt(entry.getKey())))
                .forEach(entry -> frozen.put(
                        Objects.requireNonNull(entry.getKey(), "engineId"),
                        List.copyOf(Objects.requireNonNull(entry.getValue(), "drafts"))));
        return Collections.unmodifiableMap(frozen);
    }

    public static boolean isCurrent(
            ExportDraft draft,
            ReloadPublication.Generation runtime) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(runtime, "runtime");
        return runtime.selectors().generation() == draft.ruleGeneration()
                && runtime.surfaces().generation() == draft.surfaceGeneration();
    }

    @FunctionalInterface
    public interface OverlayReceiverIds {
        List<String> resolve(
                EngineFamily family,
                ConnectionRuleSet<Block> rules,
                MinecraftSurfaceCatalog.Snapshot surfaces);
    }

    public record WorkspaceTarget(
            Optional<ManagedAuthoringDraft> draft,
            Optional<NativeDocumentSnapshot> nativeDocument) {
        public WorkspaceTarget {
            draft = Objects.requireNonNull(draft, "draft");
            nativeDocument = Objects.requireNonNull(nativeDocument, "nativeDocument");
            if (draft.isEmpty() && nativeDocument.isEmpty()) {
                throw new IllegalArgumentException(
                        "workspace target requires a draft or native document");
            }
        }
    }

    private record NativeDocumentKey(
            EngineFamily family,
            String documentPath) {
        private NativeDocumentKey {
            Objects.requireNonNull(family, "family");
            Objects.requireNonNull(documentPath, "documentPath");
        }
    }
}
