package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

import com.kltyton.autoseamblend.compat.athena.authoring.AthenaPaneTilePlan;
import com.kltyton.autoseamblend.compat.athena.authoring.AthenaPaneTilePlan.Role;
import com.kltyton.autoseamblend.compat.athena.generation.AthenaGeneratedSpritePlan;
import com.kltyton.autoseamblend.compat.athena.runtime.pane.AthenaPaneSurfaceRoles;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.fabric.runtime.culling.FabricGlassPaneSeamCulling;
import com.kltyton.autoseamblend.fabric.reload.lifecycle.FabricModelCapture;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.StateSurface;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
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
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：把已规划的 CTM 状态精灵装入 Athena 原生玻璃板模型生命周期。
 *
 * English: Installs planned CTM state sprites into Athena's native pane-model lifecycle.
 */
public final class FabricAthenaGeneratedPaneModelFactory {
    private static final VertexFormat BLOCK_FORMAT =
            DefaultVertexFormat.BLOCK;
    private static final int STRIDE_INTS =
            BLOCK_FORMAT.getVertexSize() / 4;
    private static final int UV0_OFFSET_INTS =
            BLOCK_FORMAT.getOffset(
                    VertexFormatElement.UV0) / 4;

    private FabricAthenaGeneratedPaneModelFactory() {}

    /**
     * 中文：按当前代次路由创建 Fabric pane 模型；与 NeoForge 生命周期同序（先 pane factory，
     * 否则由调用方回退到通用包装器）。
     *
     * <p>English: Creates the Fabric pane model from the current-generation routing, matching
     * the NeoForge lifecycle order (pane factory first, callers fall back to the generic
     * wrapper otherwise).
     */
    public static Optional<BakedModel> create(
            Function<Material, TextureAtlasSprite> textureGetter,
            ReloadPublication.Generation generation,
            MinecraftSurfaceCatalog.Snapshot surfaces,
            BlockState state,
            BakedModel currentModel) {
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(surfaces, "surfaces");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(currentModel, "currentModel");
        // 中文：与 NeoForge 工厂同序——先做廉价门控（IronBars/原生模型/代次对齐）再路由。
        // English: Same order as the NeoForge factory -- cheap gates (IronBars, native
        // model, generation alignment) run before routing.
        if (!(state.getBlock() instanceof IronBarsBlock)) {
            return Optional.empty();
        }
        if (currentModel instanceof AthenaBakedModel) {
            return Optional.empty();
        }
        if (generation.generation() != surfaces.generation()) {
            return Optional.empty();
        }
        Objects.requireNonNull(textureGetter, "textureGetter");
        return create(
                textureGetter,
                generation,
                surfaces,
                state,
                currentModel,
                EngineQueryRouter.select(
                        state,
                        generation));
    }

    /**
     * 中文：纯决策 seam：路由结果由调用方提供，单测可注入稳定 ATHENA 选择而不依赖全局
     * 引擎注册表；生产入口委托本方法。
     *
     * <p>English: Pure-decision seam: the caller supplies the routed selection so unit tests
     * can inject a stable ATHENA selection without the global engine registry; the production
     * entry delegates here.
     */
    static Optional<BakedModel> create(
            Function<Material, TextureAtlasSprite> textureGetter,
            ReloadPublication.Generation generation,
            MinecraftSurfaceCatalog.Snapshot surfaces,
            BlockState state,
            BakedModel currentModel,
            Optional<EngineQuerySelection> routed) {
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(surfaces, "surfaces");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(currentModel, "currentModel");
        Objects.requireNonNull(routed, "routed");
        // 中文：两个入口强制同一 NeoForge 门控：IronBars、非 Athena 原生模型、代次对齐。
        // English: Both entries enforce the same NeoForge gates: IronBars, a non-native
        // Athena model, and generation alignment.
        if (!(state.getBlock() instanceof IronBarsBlock)) {
            return Optional.empty();
        }
        if (currentModel instanceof AthenaBakedModel) {
            return Optional.empty();
        }
        if (generation.generation() != surfaces.generation()) {
            return Optional.empty();
        }
        Objects.requireNonNull(textureGetter, "textureGetter");
        if (routed.isEmpty()
                || routed.orElseThrow().family()
                != EngineFamily.ATHENA
                || !routed.orElseThrow()
                .runsAutoBlend()) {
            return Optional.empty();
        }
        // 中文：优先用已发布/已 stage 的 surfaces 选择 body/edge；首次 bake surfaces 为空
        // 时回退到 common MinecraftSurfaceCatalog.surfaceFromModel（6 方向桶 + null bucket，
        // 与已发布表面同一收集语义），生成精灵已在同代首轮 Atlas 规划注册，禁止二次资源
        // 重载补救。
        // English: Prefers published/staged surfaces for body/edge selection; when the first
        // bake has no surfaces it falls back to the common
        // MinecraftSurfaceCatalog.surfaceFromModel (six direction buckets plus the null
        // bucket, the same collection semantics as published surfaces). Generated sprites
        // are registered by the same-generation first-round atlas planning; no second
        // resource reload is ever used.
        Optional<PaneSources> sources = Optional
                .ofNullable(surfaces.states().get(state))
                .flatMap(FabricAthenaGeneratedPaneModelFactory
                        ::paneSources)
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
        ConnectionMethod method = AthenaPaneTilePlan.resolveRuntimeMethod(
                selection.method(),
                inferred);
        if (method != ConnectionMethod.CTM) {
            return Optional.empty();
        }

        ResourceLocation bodyId = pane.body()
                .sprite()
                .contents()
                .name();
        ResourceLocation edgeId = pane.edge()
                .sprite()
                .contents()
                .name();
        Int2ObjectMap<Material> materials =
                new Int2ObjectArrayMap<>();
        materials.put(
                Role.PARTICLE.nativeIndex(),
                blockMaterial(bodyId));
        materials.put(
                Role.EMPTY.nativeIndex(),
                generatedMaterial(
                        bodyId,
                        Role.EMPTY));
        materials.put(
                Role.CENTER.nativeIndex(),
                generatedMaterial(
                        bodyId,
                        Role.CENTER));
        materials.put(
                Role.VERTICAL.nativeIndex(),
                generatedMaterial(
                        bodyId,
                        Role.VERTICAL));
        materials.put(
                Role.HORIZONTAL.nativeIndex(),
                generatedMaterial(
                        bodyId,
                        Role.HORIZONTAL));
        materials.put(
                Role.EDGE.nativeIndex(),
                blockMaterial(edgeId));
        // 中文：自动规则没有原生 side_edge 声明；窄边按 Athena 工厂合同回退到 particle。
        // English: Automatic rules have no native side_edge declaration; match Athena's
        // particle fallback.
        materials.put(
                Role.SIDE_EDGE.nativeIndex(),
                blockMaterial(bodyId));
        for (Role role : Role.values()) {
            Material material = materials.get(
                    role.nativeIndex());
            TextureAtlasSprite materialSprite =
                    textureGetter.apply(material);
            if (missing(materialSprite)) {
                return Optional.empty();
            }
        }

        ConnectionRuleSet<Block> rules =
                generation.selectors().rules();
        BakedModel nativeModel = new AthenaBakedModel(
                new FabricRuleAwarePaneModel(
                        materials,
                        rules),
                textureGetter);
        // 中文：与 26.1.2 已验收 97d1478 的 wrapper 顺序一致——specialized Athena 模型替换
        // 会丢弃 AfterBake WRAP_PHASE 阶段安装的 PaneCullingModel，这里在最终模型外层重新
        // 应用同一个包装器。
        // English: Matching the accepted 26.1.2 97d1478 wrapper order -- the specialized
        // Athena model replacement drops the PaneCullingModel installed by the AfterBake
        // WRAP_PHASE, so the same wrapper is reapplied around the final model.
        return Optional.of(
                FabricGlassPaneSeamCulling.wrap(
                        new FabricPaneTopUvModel(
                                nativeModel,
                                textureGetter.apply(
                                        blockMaterial(
                                                edgeId)))));
    }

    private static Material blockMaterial(
            ResourceLocation spriteId) {
        return new Material(
                TextureAtlas.LOCATION_BLOCKS,
                spriteId);
    }

    private static Material generatedMaterial(
            ResourceLocation source,
            Role role) {
        // 中文：按角色原生索引选择生成材质，不再经过物理槽选择。
        // English: Selects the generated material by the role's native index instead of a
        // physical slot selection.
        return blockMaterial(
                AthenaGeneratedSpritePlan.generatedId(
                        source,
                        ConnectionMethod.CTM,
                        role.nativeIndex()));
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

    /**
     * 中文：从表面快照选择 pane 的 body/edge 来源：edge 为竖直轴最大非透明面，body 为
     * 水平轴优先非 edge 精灵、再按面积最大的面；与 NeoForge paneSources 逐段同构。
     *
     * <p>English: Selects the pane body/edge sources from the surface snapshot: edge is the
     * largest non-transparent vertical face, body is the horizontal face preferring a
     * non-edge sprite then the largest area; stage-for-stage isomorphic to the NeoForge
     * paneSources.
     */
    static Optional<PaneSources> paneSources(
            StateSurface stateSurface) {
        Objects.requireNonNull(
                stateSurface,
                "stateSurface");
        return AthenaPaneSurfaceRoles
                .select(stateSurface)
                .map(roles -> new PaneSources(
                        roles.body(),
                        roles.edge()));
    }

    /**
     * 中文：首次 bake 无 surfaces 时的 body/edge 回退：调用 common
     * MinecraftSurfaceCatalog.surfaceFacesFromModel（6 方向桶 + null bucket，按 quad.getDirection()
     * 分组，与已发布表面同一收集语义），再复用 paneSources 的同一选择逻辑；生成精灵已在
     * 同代首轮 Atlas 规划注册，禁止二次资源重载补救。
     *
     * <p>English: First-bake body/edge fallback when surfaces are absent: calls the common
     * MinecraftSurfaceCatalog.surfaceFromModel (six direction buckets plus the null bucket,
     * grouped by quad.getDirection(), the same collection semantics as published surfaces)
     * and reuses the same paneSources selection logic; generated sprites are registered by
     * the same-generation first-round atlas planning, and no second resource reload is ever
     * used.
     */
    static Optional<PaneSources> firstBakePaneSources(
            BlockState state,
            BakedModel model,
            Collection<StateSurface> siblings) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(siblings, "siblings");
        StateSurface current = MinecraftSurfaceCatalog
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

    /**
     * 中文：提供同 Block 的 sibling 表面：优先来自已发布/已 stage 快照，快照为空时（首次
     * bake）从同代捕获的基础模型组（FabricModelCapture，同 Block 其他连接状态）经
     * surfaceFacesFromModel 构建；纯数据驱动，不依赖方块 ID/精灵名白名单。
     *
     * <p>English: Supplies same-block sibling surfaces: preferred from the published/staged
     * snapshot; when the snapshot is empty (first bake) they are built from the same-generation
     * captured base-model group (FabricModelCapture, other connection states of the same block)
     * via surfaceFacesFromModel; purely data-driven with no block-id or sprite whitelists.
     */
    private static List<StateSurface> sameBlockSiblings(
            BlockState state,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
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
        for (Map.Entry<BlockState, BakedModel> entry
                : FabricModelCapture
                        .latestBaseModels()
                        .entrySet()) {
            BlockState candidate = entry.getKey();
            if (candidate.getBlock() == block
                    && !candidate.equals(state)
                    && seen.add(candidate)) {
                siblings.add(
                        MinecraftSurfaceCatalog
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
