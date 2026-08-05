package com.kltyton.autoseamblend.authoring.export;

import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.authoring.export.ExportDraft;
import com.kltyton.autoseamblend.authoring.export.ExportDraftPlanning;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringRule;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringDraft;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringProjectDrafts;
import com.kltyton.autoseamblend.authoring.export.NativeDocumentSnapshot;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.authoring.materialize.TextureSourceSnapshot;
import com.kltyton.autoseamblend.engine.registry.EngineRegistryRuntimeState;
import com.kltyton.autoseamblend.engine.routing.query.EngineQueryRouterCore;
import com.kltyton.autoseamblend.engine.routing.query.MinecraftEngineQueryContext;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolution;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.reload.rule.ManagedRule;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

/** 中文：仅在 GUI 或命令显式触发导出时捕获一个已选表面。 / English: Captures one selected surface only when GUI or command export is explicitly invoked. */
public final class ExportDrafts {
    private ExportDrafts() {}

    /**
     * 中文：解析客户端准星当前选中表面的引擎查询；未选中或空气时回退到已安装引擎摘要。
     *
     * English: Resolves the engine query for the client crosshair-selected
     * surface, falling back to the installed-engine summary when nothing is
     * selected or the block is air.
     */
    public static Optional<EngineQuerySelection> current(
            Minecraft minecraft,
            EngineRegistryRuntimeState engines) {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(engines, "engines");
        return ReloadPublication.read(runtime -> {
            if (minecraft.level == null
                    || !(minecraft.hitResult
                            instanceof BlockHitResult hit)) {
                return EngineQueryRouterCore.fallback(
                        engines,
                        runtime);
            }
            BlockState state = minecraft.level
                    .getBlockState(hit.getBlockPos());
            if (state.isAir()) {
                return EngineQueryRouterCore.fallback(
                        engines,
                        runtime);
            }
            Optional<EngineQuerySelection> exact = runtime.surfaces()
                    .preferredFace(
                            state,
                            hit.getDirection())
                    .flatMap(surface -> EngineQueryRouterCore.exact(
                            engines,
                            runtime,
                            state,
                            minecraft.level,
                            hit.getBlockPos(),
                            surface.representativeQuad(),
                            surface.sprite(),
                            MinecraftEngineQueryContext::new));
            return exact
                    .or(() -> EngineQueryRouterCore.summary(
                            engines,
                            runtime,
                            state,
                            false))
                    .or(() -> EngineQueryRouterCore.fallback(
                            engines,
                            runtime));
        });
    }

    public static Optional<ExportDraft> currentSelection(
            Minecraft minecraft,
            EngineFamily family,
            EngineRegistryRuntimeState engines) throws IOException {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(engines, "engines");
        if (minecraft.level == null
                || !(minecraft.hitResult
                        instanceof BlockHitResult hit)) {
            return Optional.empty();
        }
        BlockState state = minecraft.level.getBlockState(
                hit.getBlockPos());
        if (state.isAir()) {
            return Optional.empty();
        }
        ReloadPublication.Generation runtime =
                ReloadPublication.current();
        RuleRuntime.Snapshot rules =
                runtime.selectors();
        MinecraftSurfaceCatalog.Snapshot surfaces =
                runtime.surfaces();
        Optional<FaceSurface> selected =
                surfaces.preferredFace(
                        state,
                        hit.getDirection());
        Optional<ManagedAuthoringDraft>
                fields =
                        ManagedAuthoringProjectDrafts
                                .currentSelection(
                                        minecraft,
                                        family,
                                        runtime);
        if (selected.isEmpty()
                || fields.isEmpty()) {
            return Optional.empty();
        }
        FaceSurface surface =
                selected.orElseThrow();
        Optional<EngineQuerySelection>
                routed =
                        EngineQueryRouterCore.exact(
                                engines,
                                runtime,
                                state,
                                minecraft.level,
                                hit.getBlockPos(),
                                surface.representativeQuad(),
                                surface.sprite(),
                                MinecraftEngineQueryContext::new)
                                .filter(value ->
                                        value.family()
                                                == family);
        if (routed.isEmpty()) {
            return Optional.empty();
        }
        ManagedAuthoringDraft draft =
                ExportDraftPlanning.canonicalDraft(
                        fields.orElseThrow(),
                        routed.orElseThrow(),
                        state,
                        surface);
        String renderedTexture = surface.sprite()
                .contents()
                .name()
                .toString();
        if (!renderedTexture.equals(
                draft.sourceTextureId())) {
            return Optional.empty();
        }
        ManagedAuthoringRule rule =
                ManagedAuthoringProjectDrafts
                        .createRule(draft);
        TextureSourceSnapshot source =
                TextureSourceSnapshot.capture(
                        surface.sprite()
                                .contents(),
                        minecraft.getResourceManager());
        Optional<TextureSourceSnapshot>
                topSource = captureTopSource(
                        minecraft,
                        surfaces,
                        state,
                        rule,
                        surface,
                        source);
        return Optional.of(new ExportDraft(rule,
        ExportDraftPlanning.surfaceSnapshot(
                surface,
                routed.orElseThrow().family(),
                rules.rules(),
                surfaces,
                ExportDrafts::overlayReceiverBlockIds),
        source,
        topSource,
        rules.generation(),
        surfaces.generation()));
    }

    /**
     * 中文：显式捕获 Managed 与配置目标；每个目标采用与运行时相同的查询级引擎路由，并在各原生格式分区内保持顺序。
     *
     * English:
     * Explicitly captures Managed and configured targets. Each target uses the
     * same query-level engine routing as runtime, while order is preserved
     * inside every native-format partition.
     */
    public static Map<String, List<ExportDraft>>
            configuredTargets(
                    Minecraft minecraft,
                    EngineRegistryRuntimeState engines)
                    throws IOException {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(engines, "engines");
        ReloadPublication.Generation runtime =
                ReloadPublication.current();
        RuleRuntime.Snapshot rules =
                runtime.selectors();
        MinecraftSurfaceCatalog.Snapshot surfaces =
                runtime.surfaces();
        IdentityHashMap<
                        SpriteContents,
                        TextureSourceSnapshot>
                sources = new IdentityHashMap<>();
        LinkedHashMap<
                        String,
                        List<ExportDraft>>
                partitions = new LinkedHashMap<>();
        LinkedHashMap<String, Block> orderedTargets =
                new LinkedHashMap<>();
        runtime.managedRules()
                .rules()
                .values()
                .stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparingInt(
                        ManagedRule::order))
                .forEach(rule -> {
                    net.minecraft.resources.Identifier id =
                            net.minecraft.resources.Identifier
                                    .tryParse(
                                            rule.targetBlockId());
                    if (id == null
                            || !BuiltInRegistries.BLOCK
                                    .containsKey(id)) {
                        return;
                    }
                    Block block =
                            BuiltInRegistries.BLOCK
                                    .getValue(id);
                    if (block != null) {
                        orderedTargets.putIfAbsent(
                                rule.targetBlockId(),
                                block);
                    }
                });
        for (ConnectionRuleSet.Target<Block> target
                : rules.rules().targets()) {
            orderedTargets.putIfAbsent(
                    BuiltInRegistries.BLOCK
                            .getKey(target.value())
                            .toString(),
                    target.value());
        }
        for (Block block : orderedTargets.values()) {
            Optional<Map.Entry<
                            BlockState,
                            MinecraftSurfaceCatalog.StateSurface>>
                    stateSurface = surfaces.states()
                            .entrySet()
                            .stream()
                            .filter(entry ->
                                    entry.getKey()
                                                    .getBlock()
                                            == block)
                            .sorted(Comparator
                                    .comparingInt((
                                            Map.Entry<
                                                    BlockState,
                                                    MinecraftSurfaceCatalog.StateSurface>
                                                    entry) ->
                                            entry.getKey()
                                                            .equals(block
                                                                    .defaultBlockState())
                                                    ? 0
                                                    : 1)
                                    .thenComparing(entry ->
                                            entry.getKey()
                                                    .toString()))
                            .filter(entry ->
                                    ExportDraftPlanning.configuredRepresentative(
                                                    entry.getValue())
                                            .isPresent())
                            .findFirst();
            if (stateSurface.isEmpty()) {
                Constants.LOG.error(
                        "EXPORT_CONFIGURED_SURFACE_MISSING: {}",
                        BuiltInRegistries.BLOCK
                                .getKey(block));
                continue;
            }
            BlockState state =
                    stateSurface.orElseThrow()
                            .getKey();
            FaceSurface surface =
                    ExportDraftPlanning.configuredRepresentative(
                                    stateSurface.orElseThrow()
                                            .getValue())
                            .orElseThrow();
            EngineQuerySelection
                    selected =
                            EngineQueryRouterCore.summary(
                                    engines,
                                    runtime,
                                    state,
                                    false)
                                    .orElseThrow(() ->
                                            new IllegalStateException(
                                                    "ENGINE_REQUIRED"));
            ManagedAuthoringDraft fields =
                    ManagedAuthoringProjectDrafts.forSurface(
                             state,
                             surface,
                             selected.family(),
                             runtime);
            fields = ExportDraftPlanning.canonicalDraft(
                    fields,
                    selected,
                    state,
                    surface);
            ManagedAuthoringRule rule =
                    ManagedAuthoringProjectDrafts
                            .createRule(fields);
            SpriteContents contents =
                    surface.sprite().contents();
            TextureSourceSnapshot source =
                    capture(
                            minecraft,
                            sources,
                            contents);
            Optional<TextureSourceSnapshot>
                    topSource = captureTopSource(
                            minecraft,
                            surfaces,
                            state,
                            rule,
                            surface,
                            source,
                            sources);
            partitions
                    .computeIfAbsent(
                            selected.engineId(),
                            ignored ->
                                    new ArrayList<>())
                    .add(new ExportDraft(rule,
                    ExportDraftPlanning.surfaceSnapshot(
                            surface,
                            selected.family(),
                            rules.rules(),
                            surfaces,
                            ExportDrafts::overlayReceiverBlockIds),
                    source,
                    topSource,
                    rules.generation(),
                    surfaces.generation()));
        }
        return ExportDraftPlanning.freezePartitions(
                partitions,
                engineId -> engines.family(engineId)
                        .stableOrder());
    }

    /**
     * 中文：在客户端线程把可视化工作区的当前修订与尚未保存的绘画像素冻结为原生导出分区。
     *
     * English:
     * Freezes the current visual-workspace revision and unsaved paint pixels
     * into native export partitions on the client thread.
     */
    public static Map<String, List<ExportDraft>>
            workspaceTargets(
                    Minecraft minecraft,
                    EngineRegistryRuntimeState engines,
                    List<ExportDraftPlanning.WorkspaceTarget>
                            workspace,
                    Map<String, TextureSourceSnapshot>
                            sourceOverrides)
                    throws IOException {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(engines, "engines");
        workspace = ExportDraftPlanning.mergedNativeDocuments(
                List.copyOf(
                        Objects.requireNonNull(
                                workspace,
                                "workspace")));
        Map<String, TextureSourceSnapshot>
                overrides = Map.copyOf(
                Objects.requireNonNull(
                        sourceOverrides,
                        "sourceOverrides"));
        ReloadPublication.Generation runtime =
                ReloadPublication.current();
        RuleRuntime.Snapshot rules =
                runtime.selectors();
        MinecraftSurfaceCatalog.Snapshot surfaces =
                runtime.surfaces();
        IdentityHashMap<
                        SpriteContents,
                        TextureSourceSnapshot>
                captured = new IdentityHashMap<>();
        LinkedHashMap<
                        String,
                        List<ExportDraft>>
                partitions = new LinkedHashMap<>();
        for (ExportDraftPlanning.WorkspaceTarget target : workspace) {
            if (target.draft().isEmpty()) {
                NativeDocumentSnapshot document =
                        target.nativeDocument()
                                .orElseThrow();
                String engineId = targetlessEngine(
                        document.family(),
                        engines);
                partitions.computeIfAbsent(
                                engineId,
                                ignored ->
                                        new ArrayList<>())
                        .add(ExportDraft.targetless(document,
                        rules.generation(),
                        surfaces.generation()));
                continue;
            }
            ManagedAuthoringDraft draft =
                    target.draft().orElseThrow();
            net.minecraft.resources.Identifier id =
                    net.minecraft.resources.Identifier
                            .tryParse(
                                    draft.targetBlockId());
            if (id == null
                    || !BuiltInRegistries.BLOCK
                            .containsKey(id)) {
                throw new IllegalArgumentException(
                        "EXPORT_WORKSPACE_TARGET_UNKNOWN:"
                                + draft.targetBlockId());
            }
            Block block =
                    BuiltInRegistries.BLOCK
                            .getValue(id);
            Map.Entry<
                            BlockState,
                            MinecraftSurfaceCatalog.StateSurface>
                    stateSurface =
                            surfaces.states()
                                    .entrySet()
                                    .stream()
                                    .filter(entry ->
                                            entry.getKey()
                                                            .getBlock()
                                                    == block)
                                    .sorted(Comparator
                                            .comparingInt((Map.Entry<
                                                    BlockState,
                                                    MinecraftSurfaceCatalog.StateSurface>
                                                    entry) ->
                                                    entry.getKey()
                                                                    .equals(block
                                                                            .defaultBlockState())
                                                            ? 0
                                                            : 1)
                                            .thenComparing(entry ->
                                                    entry.getKey()
                                                            .toString()))
                                    .filter(entry ->
                                            ExportDraftPlanning.representativeForSource(
                                                            entry.getValue(),
                                                            draft.sourceTextureId())
                                                    .isPresent())
                                    .findFirst()
                                    .orElseThrow(() ->
                                            new IllegalStateException(
                                                    "EXPORT_WORKSPACE_SURFACE_MISSING:"
                                                            + draft.targetBlockId()));
            BlockState state =
                    stateSurface.getKey();
            FaceSurface surface =
                    ExportDraftPlanning.representativeForSource(
                                    stateSurface.getValue(),
                                    draft.sourceTextureId())
                            .orElseThrow();
            EngineQuerySelection
                    selected =
                            EngineQueryRouterCore.summary(
                                    engines,
                                    runtime,
                                    state,
                                    false)
                                    .orElseThrow(() ->
                                            new IllegalStateException(
                                                    "ENGINE_REQUIRED"));
            if (target.nativeDocument()
                    .filter(document ->
                            document.family()
                                    != selected.family())
                    .isPresent()) {
                throw new IllegalStateException(
                        "EXPORT_NATIVE_DOCUMENT_ENGINE_CHANGED:"
                                + draft.targetBlockId());
            }
            ManagedAuthoringRule rule =
                    ManagedAuthoringProjectDrafts
                            .createRule(draft);
            String renderedTexture =
                    surface.sprite()
                            .contents()
                            .name()
                            .toString();
            if (!renderedTexture.equals(
                    rule.sourceTextureId())) {
                throw new IllegalStateException(
                        "EXPORT_WORKSPACE_SOURCE_CHANGED:"
                                + draft.targetBlockId());
            }
            TextureSourceSnapshot source =
                    overrides.get(
                            renderedTexture);
            if (source == null) {
                source = capture(
                        minecraft,
                        captured,
                        surface.sprite()
                                .contents());
            }
            Optional<TextureSourceSnapshot>
                    topSource = captureTopSource(
                            minecraft,
                            surfaces,
                            state,
                            rule,
                            surface,
                            source,
                            captured)
                            .map(value ->
                                    overrides
                                            .getOrDefault(
                                                    value.sourceTextureId(),
                                                    value));
            partitions.computeIfAbsent(
                            selected.engineId(),
                            ignored ->
                                    new ArrayList<>())
                    .add(new ExportDraft(rule,
                    ExportDraftPlanning.surfaceSnapshot(
                            surface,
                            selected.family(),
                            rules.rules(),
                            surfaces,
                            ExportDrafts::overlayReceiverBlockIds),
                    source,
                    topSource,
                    target.nativeDocument(),
                    rules.generation(),
                    surfaces.generation()));
        }
        return ExportDraftPlanning.freezePartitions(
                partitions,
                engineId -> engines.family(engineId)
                        .stableOrder());
    }

    private static String targetlessEngine(
            EngineFamily family,
            EngineRegistryRuntimeState engines) {
        return engines
                .readyEngineIds()
                .stream()
                .filter(engineId ->
                        engines.family(engineId)
                                == family)
                .filter(NativeExportRuntime
                        ::available)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "ENGINE_REQUIRED:" + family));
    }

    private static Optional<TextureSourceSnapshot>
            captureTopSource(
                    Minecraft minecraft,
                    MinecraftSurfaceCatalog.Snapshot
                            surfaces,
                    BlockState state,
                    ManagedAuthoringRule rule,
                    FaceSurface selected,
                    TextureSourceSnapshot source)
                    throws IOException {
        return captureTopSource(
                minecraft,
                surfaces,
                state,
                rule,
                selected,
                source,
                new IdentityHashMap<>());
    }

    private static Optional<TextureSourceSnapshot>
            captureTopSource(
                    Minecraft minecraft,
                    MinecraftSurfaceCatalog.Snapshot
                            surfaces,
                    BlockState state,
                    ManagedAuthoringRule rule,
                    FaceSurface selected,
                    TextureSourceSnapshot source,
                    IdentityHashMap<
                                    SpriteContents,
                                    TextureSourceSnapshot>
                            sources)
                    throws IOException {
        if (rule.resolvedMethod()
                != ConnectionMethod.TOP) {
            return Optional.empty();
        }
        Direction.Axis axis = state.hasProperty(
                        BlockStateProperties.AXIS)
                ? state.getValue(
                        BlockStateProperties.AXIS)
                : Direction.Axis.Y;
        Direction top =
                Direction.fromAxisAndDirection(
                        axis,
                        Direction.AxisDirection.POSITIVE);
        Optional<FaceSurface> topSurface =
                surfaces.preferredFace(
                        state,
                        top);
        if (topSurface.isEmpty()) {
            return Optional.empty();
        }
        SpriteContents contents =
                topSurface.orElseThrow()
                        .sprite()
                        .contents();
        if (contents == selected.sprite()
                .contents()) {
            return Optional.of(source);
        }
        return Optional.of(capture(
                minecraft,
                sources,
                contents));
    }

    private static TextureSourceSnapshot
            capture(
                    Minecraft minecraft,
                    IdentityHashMap<
                                    SpriteContents,
                                    TextureSourceSnapshot>
                            sources,
                    SpriteContents contents)
                    throws IOException {
        TextureSourceSnapshot source =
                sources.get(contents);
        if (source == null) {
            source =
                    TextureSourceSnapshot
                            .capture(
                                    contents,
                                    minecraft.getResourceManager());
            sources.put(
                    contents,
                    source);
        }
        return source;
    }

    /**
     * 中文：baked 原生规则从同一冻结表面代次导出所有可接收 overlay 的方块，不把供体 ID 冒充接收方白名单。
     *
     * English:
     * Exports every overlay-capable receiver from the same frozen surface
     * generation instead of impersonating the donor ID as a receiver
     * whitelist.
     */
    private static List<String> overlayReceiverBlockIds(
            EngineFamily family,
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        return surfaces.blockRepresentatives()
                .entrySet()
                .stream()
                .filter(entry ->
                        OverlayDonorResolution
                                .resolveMethod(
                                        family,
                                        entry.getValue().state(),
                                        entry.getValue().surface(),
                                        rules,
                                        surfaces)
                                .overlayCapable())
                .map(entry ->
                        BuiltInRegistries.BLOCK
                                .getKey(entry.getKey())
                                .toString())
                .sorted()
                .toList();
    }

    public static boolean isCurrent(
            ExportDraft draft) {
        return ExportDraftPlanning.isCurrent(
                draft,
                ReloadPublication.current());
    }

}
