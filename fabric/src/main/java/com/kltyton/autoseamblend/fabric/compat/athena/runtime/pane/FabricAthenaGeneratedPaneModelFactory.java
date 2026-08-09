package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

import com.kltyton.autoseamblend.compat.athena.authoring.AthenaPaneTilePlan.Role;
import com.kltyton.autoseamblend.compat.athena.runtime.pane.AthenaPaneSurfaceRoles;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.fabric.reload.lifecycle.FabricModelCapture;
import com.kltyton.autoseamblend.fabric.runtime.culling.FabricGlassPaneSeamCulling;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.StateSurface;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import earth.terrarium.athena.api.client.fabric.AthenaBakedModel;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.FabricMaterialBaker;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：把已规划的 CTM 状态精灵装入 Athena 4.7.3 Fabric 原生玻璃板模型生命周期；语义
 * 逐段移植已验收 26.1.2 NeoForge AthenaGeneratedPaneModelFactory（IronBars/非原生模型/
 * 代次对齐门控、body/edge 角色材质、PaneConnectedBlockModel connectCorners=true、
 * PaneTopUvModel 顶臂 UV 修正），并以 1.21.1 ce33d6c FabricAthenaGeneratedPaneModelFactory
 * 为边界（首烤表面回退、sibling cap 借用、FabricGlassPaneSeamCulling 外层包装）。
 *
 * <p>English: Installs planned CTM state sprites into Athena 4.7.3's native Fabric pane-model
 * lifecycle; semantics are ported stage by stage from the accepted 26.1.2 NeoForge
 * AthenaGeneratedPaneModelFactory (IronBars/non-native-model/generation-alignment gates,
 * body/edge role materials, PaneConnectedBlockModel connectCorners=true, PaneTopUvModel
 * top-arm UV correction) bounded by the 1.21.1 ce33d6c FabricAthenaGeneratedPaneModelFactory
 * (first-bake surface fallback, sibling cap borrowing, outer FabricGlassPaneSeamCulling
 * wrap).
 */
public final class FabricAthenaGeneratedPaneModelFactory {
    private FabricAthenaGeneratedPaneModelFactory() {}

    /**
     * 中文：生产入口；调用方（核心 owner 的 FabricAthenaModelLifecycle）必须在包装阶段以
     * 与 surfaces 同代次的 generation 调用，顺序与 1.21.1 wrap 一致：
     * pendingPreparation().filter(gen == surfaces.gen).orElseGet(current)。首烤时 surfaces
     * 为空（bootstrap 代次），工厂从同代基础模型收集表面并允许 selectors 为 bootstrap 空
     * 规则（仅同方块身份连接），重载后的后续烘焙使用完整选择器。
     *
     * <p>English: Production entry; the caller (the core owner's FabricAthenaModelLifecycle)
     * must pass a generation aligned with the surfaces snapshot at the wrapping phase, in the
     * same order as the 1.21.1 wrap:
     * pendingPreparation().filter(gen == surfaces.gen).orElseGet(current). On the first bake
     * the surfaces are empty (bootstrap generation), so the factory collects surfaces from
     * the same-generation base model and tolerates the bootstrap empty selectors
     * (same-block identity only); later reload bakes use the full selectors.
     */
    public static Optional<BlockStateModel> create(
            MaterialBaker materials,
            ReloadPublication.Generation generation,
            MinecraftSurfaceCatalog.Snapshot surfaces,
            BlockState state,
            BlockStateModel currentModel) {
        Objects.requireNonNull(
                materials,
                "materials");
        Objects.requireNonNull(
                generation,
                "generation");
        Objects.requireNonNull(
                surfaces,
                "surfaces");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(
                currentModel,
                "currentModel");
        if (!(state.getBlock()
                instanceof IronBarsBlock)) {
            return Optional.empty();
        }
        if (currentModel
                instanceof AthenaBakedModel) {
            return Optional.empty();
        }
        if (generation.generation()
                != surfaces.generation()) {
            return Optional.empty();
        }
        return create(
                materials,
                generation,
                surfaces,
                state,
                currentModel,
                EngineQueryRouter.select(
                        state,
                        generation));
    }

    /**
     * 中文：纯决策 seam：路由结果与材质烘焙器由调用方提供，单测/集成可注入稳定 ATHENA
     * 选择而不依赖全局引擎注册表；生产入口委托本方法。
     *
     * <p>English: Pure-decision seam: the caller supplies the routed selection and the
     * material baker so unit/integration tests can inject a stable ATHENA selection without
     * the global engine registry; the production entry delegates here.
     */
    static Optional<BlockStateModel> create(
            MaterialBaker materials,
            ReloadPublication.Generation generation,
            MinecraftSurfaceCatalog.Snapshot surfaces,
            BlockState state,
            BlockStateModel currentModel,
            Optional<EngineQuerySelection> routed) {
        Objects.requireNonNull(
                materials,
                "materials");
        Objects.requireNonNull(
                generation,
                "generation");
        Objects.requireNonNull(
                surfaces,
                "surfaces");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(
                currentModel,
                "currentModel");
        Objects.requireNonNull(routed, "routed");
        if (!(state.getBlock()
                instanceof IronBarsBlock)) {
            return Optional.empty();
        }
        if (currentModel
                instanceof AthenaBakedModel) {
            return Optional.empty();
        }
        if (generation.generation()
                != surfaces.generation()) {
            return Optional.empty();
        }
        if (routed.isEmpty()
                || routed.orElseThrow().family()
                != EngineFamily.ATHENA
                || !routed.orElseThrow()
                .runsAutoBlend()) {
            return Optional.empty();
        }
        Optional<PaneSources> sources = Optional
                .ofNullable(
                        surfaces.states().get(state))
                .flatMap(
                        AthenaPaneSurfaceRoles
                                ::select)
                .map(roles -> new PaneSources(
                        roles.body(),
                        roles.edge()))
                .or(() -> firstBakePaneSources(
                        state,
                        currentModel,
                        sameBlockSiblings(
                                state,
                                surfaces)));
        if (sources.isEmpty()) {
            return Optional.empty();
        }

        EngineQuerySelection selection =
                routed.orElseThrow();
        PaneSources pane = sources.orElseThrow();
        // 中文：经 pane 专用 helper 解析运行时方法：configured=AUTO 且推断为 NONE 时
        // 降级为 CTM（通用几何可能因全臂 pane 状态误判 NONE），显式方法绝不被改写。
        // English: Resolves the runtime method through the pane-specific helper:
        // configured=AUTO with an inferred NONE degrades to CTM (generic geometry may
        // misjudge full-arm pane states as NONE) while explicit methods are never rewritten.
        ConnectionMethod inferred = selection.resolveMethod(
                state,
                pane.body().direction(),
                pane.body().sprite()
                        .contents()
                        .name());
        ConnectionMethod method =
                FabricPaneMaterialPlan
                        .resolveRuntimeMethod(
                                selection.method(),
                                inferred);
        if (method != ConnectionMethod.CTM) {
            return Optional.empty();
        }

        boolean bodyTranslucent = translucent(
                pane.body().representativeQuad());
        boolean edgeTranslucent = translucent(
                pane.edge().representativeQuad());
        Identifier bodyId = pane.body()
                .sprite()
                .contents()
                .name();
        Identifier edgeId = pane.edge()
                .sprite()
                .contents()
                .name();
        // 中文：局部材质表必须与 MaterialBaker 形参区分命名，否则局部变量遮蔽形参，
        // 后续 baker 的 materials.get(Material, ModelDebugName) 无法解析接收者。
        // English: The local material table must use a distinct name from the MaterialBaker
        // parameter, otherwise the local shadows the parameter and the baker's
        // materials.get(Material, ModelDebugName) receiver cannot resolve.
        Int2ObjectMap<Material> paneMaterials =
                new Int2ObjectArrayMap<>();
        paneMaterials.put(
                Role.PARTICLE.nativeIndex(),
                FabricPaneMaterialPlan.blockMaterial(
                        bodyId,
                        bodyTranslucent));
        paneMaterials.put(
                Role.EMPTY.nativeIndex(),
                FabricPaneMaterialPlan.generatedMaterial(
                        bodyId,
                        Role.EMPTY,
                        bodyTranslucent));
        paneMaterials.put(
                Role.CENTER.nativeIndex(),
                FabricPaneMaterialPlan.generatedMaterial(
                        bodyId,
                        Role.CENTER,
                        bodyTranslucent));
        paneMaterials.put(
                Role.VERTICAL.nativeIndex(),
                FabricPaneMaterialPlan.generatedMaterial(
                        bodyId,
                        Role.VERTICAL,
                        bodyTranslucent));
        paneMaterials.put(
                Role.HORIZONTAL.nativeIndex(),
                FabricPaneMaterialPlan.generatedMaterial(
                        bodyId,
                        Role.HORIZONTAL,
                        bodyTranslucent));
        paneMaterials.put(
                Role.EDGE.nativeIndex(),
                FabricPaneMaterialPlan.blockMaterial(
                        edgeId,
                        edgeTranslucent));
        // 中文：自动规则没有原生 side_edge 声明；窄边按 Athena 工厂合同回退到 particle。
        // English: Automatic rules have no native side_edge declaration; match Athena's
        // particle fallback.
        paneMaterials.put(
                Role.SIDE_EDGE.nativeIndex(),
                FabricPaneMaterialPlan.blockMaterial(
                        bodyId,
                        bodyTranslucent));
        // 中文：26.1.2 原生 MaterialBaker.get(Material, ModelDebugName) 的 Fabric API glue；
        // 第二个参数是函数式接口 ModelDebugName，用 lambda 提供调试名。
        // English: The 26.1.2 native MaterialBaker.get(Material, ModelDebugName) Fabric API
        // glue; the second argument is the functional ModelDebugName interface, supplied by a
        // lambda carrying the debug name.
        Function<Material, Material.Baked> baker =
                material -> materials.get(
                        material,
                        () ->
                                "AutoSeamBlend Athena pane");
        for (Role role : Role.values()) {
            Material.Baked baked = baker.apply(
                    paneMaterials.get(
                            role.nativeIndex()));
            if (baked == null
                    || missing(baked.sprite())) {
                return Optional.empty();
            }
        }

        ConnectionRuleSet<Block> rules =
                generation.selectors().rules();
        BlockStateModel nativeModel =
                new AthenaBakedModel(
                        new FabricRuleAwarePaneModel(
                                paneMaterials,
                                rules),
                        baker);
        TextureAtlasSprite edgeSprite =
                baker.apply(
                                paneMaterials.get(
                                        Role.EDGE
                                                .nativeIndex()))
                        .sprite();
        SpriteFinder finder =
                ((FabricMaterialBaker) materials)
                        .spriteFinder(
                                AtlasIds.BLOCKS);
        // 中文：FabricMaterialBaker.spriteFinder(Identifier) 只识别注册图集 ID
        // （AtlasIds.BLOCKS/ITEMS）；传纹理文件路径 TextureAtlas.LOCATION_BLOCKS 会静默返回
        // MissingSpriteFinderImpl，导致 pane 顶臂 UV 落到 missing 贴图。
        // English: FabricMaterialBaker.spriteFinder(Identifier) only recognizes registered
        // atlas ids (AtlasIds.BLOCKS/ITEMS); passing the atlas texture path
        // TextureAtlas.LOCATION_BLOCKS silently returns MissingSpriteFinderImpl, so pane
        // top-arm UVs would land on the missing texture.
        // 中文：与 1.21.1 已验收 97d1478/ce33d6c 的 wrapper 顺序一致——specialized Athena
        // 模型替换会丢弃 AfterBake WRAP_PHASE 阶段安装的 PaneCullingModel，这里在最终模型
        // 外层重新应用同一个包装器。
        // English: Matching the accepted 1.21.1 97d1478/ce33d6c wrapper order -- the
        // specialized Athena model replacement drops the PaneCullingModel installed by the
        // AfterBake WRAP_PHASE, so the same wrapper is reapplied around the final model.
        return Optional.of(
                FabricGlassPaneSeamCulling.wrap(
                        new FabricPaneTopUvModel(
                                nativeModel,
                                edgeSprite,
                                finder)));
    }

    private static boolean translucent(
            BakedQuad quad) {
        return quad.materialInfo().layer()
                == ChunkSectionLayer.TRANSLUCENT;
    }

    private static boolean missing(
            TextureAtlasSprite sprite) {
        return sprite == null
                || sprite.contents()
                        .name()
                        .equals(
                                MissingTextureAtlasSprite
                                        .getLocation());
    }

    /** 中文：首烤无 surfaces 时的 body/edge 回退：从同代基础模型收集表面，再复用同一选择逻辑并做 sibling cap 借用。 / English: First-bake body/edge fallback: collects surfaces from the same-generation base model, reuses the same selection logic, and applies sibling cap borrowing. */
    private static Optional<PaneSources>
            firstBakePaneSources(
                    BlockState state,
                    BlockStateModel model,
                    Collection<StateSurface> siblings) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(
                siblings,
                "siblings");
        StateSurface current =
                FabricPaneSurfaceRoles
                        .surfaceFacesFromModel(
                                state,
                                model);
        return AthenaPaneSurfaceRoles
                .selectWithSiblingFallback(
                        current,
                        siblings)
                .map(roles -> new PaneSources(
                        roles.body(),
                        roles.edge()));
    }

    /** 中文：提供同 Block 的 sibling 表面：优先已发布/已 stage 快照，快照为空（首烤）时从同代捕获的基础模型组（FabricModelCapture）构建；纯数据驱动。 / English: Supplies same-block sibling surfaces: preferred from the published/staged snapshot; when the snapshot is empty (first bake) they are built from the same-generation captured base-model group (FabricModelCapture); purely data-driven. */
    private static List<StateSurface>
            sameBlockSiblings(
                    BlockState state,
                    MinecraftSurfaceCatalog.Snapshot
                            surfaces) {
        Block block = state.getBlock();
        LinkedHashSet<BlockState> seen =
                new LinkedHashSet<>();
        ArrayList<StateSurface> siblings =
                new ArrayList<>();
        for (Map.Entry<BlockState, StateSurface> entry
                : surfaces.states().entrySet()) {
            BlockState candidate = entry.getKey();
            if (candidate.getBlock() == block
                    && !candidate.equals(state)
                    && seen.add(candidate)) {
                siblings.add(entry.getValue());
            }
        }
        for (Map.Entry<BlockState, BlockStateModel> entry
                : FabricModelCapture
                        .latestBaseModels()
                        .entrySet()) {
            BlockState candidate = entry.getKey();
            if (candidate.getBlock() == block
                    && !candidate.equals(state)
                    && seen.add(candidate)) {
                siblings.add(
                        FabricPaneSurfaceRoles
                                .surfaceFacesFromModel(
                                        candidate,
                                        entry.getValue()));
            }
        }
        return List.copyOf(siblings);
    }

    record PaneSources(
            FaceSurface body,
            FaceSurface edge) {}
}
