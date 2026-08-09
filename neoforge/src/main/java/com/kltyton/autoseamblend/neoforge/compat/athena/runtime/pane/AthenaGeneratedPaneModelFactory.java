package com.kltyton.autoseamblend.neoforge.compat.athena.runtime.pane;

import com.kltyton.autoseamblend.compat.athena.authoring.AthenaPaneTilePlan;
import com.kltyton.autoseamblend.compat.athena.generation.AthenaGeneratedSpritePlan;
import com.kltyton.autoseamblend.compat.athena.runtime.pane.AthenaPaneSurfaceRoles;
import com.kltyton.autoseamblend.compat.athena.runtime.AthenaStateProjection;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import earth.terrarium.athena.api.client.models.AthenaQuad;
import earth.terrarium.athena.api.client.neoforge.AthenaBakedModel;
import earth.terrarium.athena.api.client.utils.AppearanceAndTintGetter;
import earth.terrarium.athena.api.client.utils.AthenaUtils;
import earth.terrarium.athena.api.client.utils.CtmState;
import earth.terrarium.athena.api.client.utils.CtmUtils;
import earth.terrarium.athena.impl.client.models.PaneConnectedBlockModel;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * 中文：把已规划的 CTM 状态精灵装入 Athena 原生玻璃板模型生命周期。
 *
 * English: Installs planned CTM state sprites into Athena's native pane-model lifecycle.
 */
public final class AthenaGeneratedPaneModelFactory {
    private static final VertexFormat BLOCK_FORMAT =
            DefaultVertexFormat.BLOCK;
    private static final int STRIDE_INTS =
            BLOCK_FORMAT.getVertexSize() / 4;
    private static final int UV0_OFFSET_INTS =
            BLOCK_FORMAT.getOffset(
                    VertexFormatElement.UV0) / 4;

    private AthenaGeneratedPaneModelFactory() {}

    public static Optional<BakedModel> create(
            Function<Material, TextureAtlasSprite> textureGetter,
            ReloadPublication.Generation generation,
            MinecraftSurfaceCatalog.Snapshot surfaces,
            BlockState state,
            BakedModel currentModel) {
        Objects.requireNonNull(textureGetter, "textureGetter");
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(surfaces, "surfaces");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(currentModel, "currentModel");
        if (!(state.getBlock() instanceof IronBarsBlock)) {
            return Optional.empty();
        }
        if (currentModel instanceof AthenaBakedModel) {
            return Optional.empty();
        }
        if (generation.generation() != surfaces.generation()) {
            return Optional.empty();
        }

        Optional<EngineQuerySelection> routed =
                EngineQueryRouter.select(state, generation);
        Optional<PaneSources> sources = Optional.ofNullable(
                        surfaces.states().get(state))
                .flatMap(AthenaGeneratedPaneModelFactory::paneSources);
        if (routed.isEmpty()
                || routed.orElseThrow().family() != EngineFamily.ATHENA
                || !routed.orElseThrow().runsAutoBlend()) {
            return Optional.empty();
        }
        if (sources.isEmpty()) {
            return Optional.empty();
        }

        EngineQuerySelection selection = routed.orElseThrow();
        PaneSources pane = sources.orElseThrow();
        // 中文：经 pane 专用 helper 解析运行时方法：configured=AUTO 且推断为 NONE 时
        // 降级为 CTM（通用几何可能因全臂 pane 状态误判 NONE），显式方法绝不被改写。
        // English: Resolves the runtime method through the pane-specific helper:
        // configured=AUTO with an inferred NONE degrades to CTM (generic geometry may
        // misjudge full-arm pane states as NONE) while explicit methods are never rewritten.
        ConnectionMethod method = AthenaPaneTilePlan.resolveRuntimeMethod(
                selection.method(),
                selection.resolveMethod(
                        state,
                        pane.body().direction(),
                        pane.body().sprite().contents().name()));
        if (method != ConnectionMethod.CTM) {
            return Optional.empty();
        }

        ResourceLocation bodyId = pane.body().sprite().contents().name();
        ResourceLocation edgeId = pane.edge().sprite().contents().name();
        Int2ObjectMap<Material> materials = new Int2ObjectArrayMap<>();
        materials.put(
                AthenaPaneTilePlan.Role.PARTICLE.nativeIndex(),
                blockMaterial(bodyId));
        materials.put(
                AthenaPaneTilePlan.Role.EMPTY.nativeIndex(),
                generatedMaterial(
                        bodyId,
                        AthenaPaneTilePlan.Role.EMPTY));
        materials.put(
                AthenaPaneTilePlan.Role.CENTER.nativeIndex(),
                generatedMaterial(
                        bodyId,
                        AthenaPaneTilePlan.Role.CENTER));
        materials.put(
                AthenaPaneTilePlan.Role.VERTICAL.nativeIndex(),
                generatedMaterial(
                        bodyId,
                        AthenaPaneTilePlan.Role.VERTICAL));
        materials.put(
                AthenaPaneTilePlan.Role.HORIZONTAL.nativeIndex(),
                generatedMaterial(
                        bodyId,
                        AthenaPaneTilePlan.Role.HORIZONTAL));
        materials.put(
                AthenaPaneTilePlan.Role.EDGE.nativeIndex(),
                blockMaterial(edgeId));
        // 中文：自动规则没有原生 side_edge 声明；窄边按 Athena 工厂合同回退到 particle。
        // English: Automatic rules have no native side_edge declaration; match Athena's particle fallback.
        materials.put(
                AthenaPaneTilePlan.Role.SIDE_EDGE.nativeIndex(),
                blockMaterial(bodyId));
        ArrayList<Integer> missingSlots = new ArrayList<>();
        for (AthenaPaneTilePlan.Role role : AthenaPaneTilePlan.Role.values()) {
            Material material = materials.get(role.nativeIndex());
            TextureAtlasSprite materialSprite = textureGetter.apply(
                    material);
            if (missing(materialSprite)) {
                missingSlots.add(role.nativeIndex());
            }
        }
        if (!missingSlots.isEmpty()) {
            return Optional.empty();
        }

        ConnectionRuleSet<Block> rules = generation.selectors().rules();
        BakedModel nativeModel = new AthenaBakedModel(
                new RuleAwarePaneModel(materials, rules),
                textureGetter);
        return Optional.of(new PaneTopUvModel(
                nativeModel,
                textureGetter.apply(
                        blockMaterial(edgeId))));
    }

    private static Material blockMaterial(
            ResourceLocation spriteId) {
        return new Material(
                TextureAtlas.LOCATION_BLOCKS,
                spriteId);
    }

    static Optional<PaneSources> paneSources(
            MinecraftSurfaceCatalog.StateSurface stateSurface) {
        Objects.requireNonNull(stateSurface, "stateSurface");
        return AthenaPaneSurfaceRoles
                .select(stateSurface)
                .map(roles -> new PaneSources(
                        roles.body(),
                        roles.edge()));
    }

    private static Material generatedMaterial(
            ResourceLocation source,
            AthenaPaneTilePlan.Role role) {
        // 中文：按角色原生索引选择生成材质，不再经过物理槽选择。
        // English: Selects the generated material by the role's native index instead of a physical slot selection.
        return blockMaterial(
                AthenaGeneratedSpritePlan.generatedId(
                        source,
                        ConnectionMethod.CTM,
                        role.nativeIndex()));
    }

    private static boolean missing(TextureAtlasSprite sprite) {
        return sprite == null
                || sprite.contents().name().equals(
                        MissingTextureAtlasSprite.getLocation());
    }

    record PaneSources(
            FaceSurface body,
            FaceSurface edge) {}

    /** 中文：读取 BakedQuad 顶点位置；PaneTopUvModel 顶臂 UV 修正复用。 / English: Reads a BakedQuad vertex position; reused by the PaneTopUvModel top-arm UV correction. */
    private static float[] position(
            BakedQuad quad,
            int vertex) {
        int base = vertex * STRIDE_INTS;
        int[] vertices = quad.getVertices();
        return new float[] {
            Float.intBitsToFloat(vertices[base]),
            Float.intBitsToFloat(vertices[base + 1]),
            Float.intBitsToFloat(vertices[base + 2])
        };
    }

    /**
     * 中文：保留 Athena 的原生玻璃板几何，只解除东西向顶面几何范围与 UV 范围的错误绑定。
     *
     * <p>English: Retains Athena's native pane geometry while decoupling the east/west top-arm
     * geometry range from its incorrectly bound UV range.
     */
    static final class PaneTopUvModel
            extends BakedModelWrapper<BakedModel> {
        private static final float EPSILON = 1.0e-6F;

        private final TextureAtlasSprite edgeSprite;

        PaneTopUvModel(
                BakedModel delegate,
                TextureAtlasSprite edgeSprite) {
            super(Objects.requireNonNull(delegate, "delegate"));
            this.edgeSprite = Objects.requireNonNull(
                    edgeSprite,
                    "edgeSprite");
        }

        @Override
        public List<BakedQuad> getQuads(
                BlockState state,
                Direction direction,
                RandomSource random) {
            return correct(
                    originalModel.getQuads(
                            state,
                            direction,
                            random),
                    edgeSprite);
        }

        /**
         * 中文：世界渲染与 3D 场景预览都走 5 参 getQuads（BakedModelWrapper 的 3 参
         * 重写不会命中），必须在这里同样应用顶面 UV 修正，否则修正被绕过。
         *
         * <p>English: World rendering and the 3D scene preview both use the 5-arg
         * getQuads (the 3-arg override is never hit there), so the top-arm UV
         * correction must also be applied here or it is bypassed.
         */
        @Override
        public List<BakedQuad> getQuads(
                BlockState state,
                Direction direction,
                RandomSource random,
                ModelData modelData,
                RenderType renderType) {
            return correct(
                    originalModel.getQuads(
                            state,
                            direction,
                            random,
                            modelData,
                            renderType),
                    edgeSprite);
        }

        private static List<BakedQuad> correct(
                List<BakedQuad> source,
                TextureAtlasSprite edgeSprite) {
            if (source.isEmpty()) {
                return source;
            }
            ArrayList<BakedQuad> output = null;
            for (int index = 0; index < source.size(); index++) {
                BakedQuad quad = source.get(index);
                BakedQuad replacement = correct(
                        quad,
                        edgeSprite);
                if (replacement == quad) {
                    if (output != null) {
                        output.add(quad);
                    }
                    continue;
                }
                if (output == null) {
                    output = new ArrayList<>(source.size());
                    output.addAll(source.subList(0, index));
                }
                output.add(replacement);
            }
            return output == null
                    ? source
                    : List.copyOf(output);
        }

        private static BakedQuad correct(
                BakedQuad quad,
                TextureAtlasSprite edgeSprite) {
            if (!quad.getDirection().getAxis().isVertical()
                    || !quad.getSprite()
                            .contents()
                            .name()
                            .equals(edgeSprite.contents().name())) {
                return quad;
            }
            float minX = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            for (int vertex = 0;
                    vertex < 4;
                    vertex++) {
                float[] position = position(quad, vertex);
                minX = Math.min(minX, position[0]);
                minZ = Math.min(minZ, position[2]);
                maxX = Math.max(maxX, position[0]);
                maxZ = Math.max(maxZ, position[2]);
            }
            if (maxX - minX <= maxZ - minZ + EPSILON) {
                return quad;
            }
            float atlasWidth = edgeSprite.getU1()
                    - edgeSprite.getU0();
            float atlasHeight = edgeSprite.getV1()
                    - edgeSprite.getV0();
            if (Math.abs(atlasWidth) <= EPSILON
                    || Math.abs(atlasHeight) <= EPSILON) {
                return quad;
            }
            int[] vertices = quad.getVertices()
                    .clone();
            for (int vertex = 0;
                    vertex < 4;
                    vertex++) {
                int base = vertex * STRIDE_INTS
                        + UV0_OFFSET_INTS;
                float u = Float.intBitsToFloat(
                        vertices[base]);
                float v = Float.intBitsToFloat(
                        vertices[base + 1]);
                float localU = (u - edgeSprite.getU0())
                        / atlasWidth;
                float localV = (v - edgeSprite.getV0())
                        / atlasHeight;
                vertices[base] =
                        Float.floatToRawIntBits(
                                edgeSprite.getU(localV));
                vertices[base + 1] =
                        Float.floatToRawIntBits(
                                edgeSprite.getV(localU));
            }
            return new BakedQuad(
                    vertices,
                    quad.getTintIndex(),
                    quad.getDirection(),
                    quad.getSprite(),
                    quad.isShade());
        }
    }

    static final class RuleAwarePaneModel
            extends PaneConnectedBlockModel {
        private final ConnectionRuleSet<Block> rules;

        RuleAwarePaneModel(
                Int2ObjectMap<Material> materials,
                ConnectionRuleSet<Block> rules) {
            // 中文：Athena 1.21.1 玻璃板模型使用原生 CtmState、形状判定和 Quad 生命周期。
            // English: Athena 1.21.1 pane model uses native CtmState, shape checks, and quad lifecycle.
            super(materials);
            this.rules = Objects.requireNonNull(rules, "rules");
        }

        @Override
        public List<AthenaQuad> getQuads(
                AppearanceAndTintGetter getter,
                BlockState state,
                BlockPos pos,
                Direction face) {
            // 中文：Athena 原实现按 BlockState 引用剔除上下盖板；项目合同要求同一连接组的属性变体也连续。
            // English: Athena culls vertical caps by BlockState identity; the product contract also joins property variants in one connection group.
            if (face.getAxis().isVertical()
                    && AthenaStateProjection.connects(
                            rules,
                            state.getBlock(),
                            getter.getBlockState(pos.relative(face)).getBlock())) {
                return List.of();
            }
            if (!face.getAxis().isVertical()) {
                // 中文：与 4.7.3 true 精确同序：先构造 CtmState，allTrue 必须走原生
                // CENTER（super），再判 cw/ccw；仅非 allTrue 且恰一侧连接才早返回替换，
                // 禁止在 super 结果后追加。4.0.6 无 connectCorners 开关（等价 false），
                // 单侧时原生返回一条 full-height sprite-0 旧条带，此处以公开 API
                // （CtmState.from 完成八邻域朝向，isConnected 为受保护原生谓词）复刻
                // 4.7.3 true 的单侧替换。双侧、无侧与垂直面仍走 super。
                // English: Exactly aligned with 4.7.3 true: build CtmState first; allTrue
                // must take the native CENTER (super) path; then evaluate cw/ccw. Only a
                // non-allTrue exactly-one-side connection takes the early-return
                // replacement, never an append after super. 4.0.6 has no connectCorners
                // switch (equivalent to false) and returns one full-height sprite-0 legacy
                // strip on a single side; public APIs (CtmState.from owns the eight-neighbor
                // orientation, isConnected is the protected native predicate) replicate the
                // 4.7.3 true single-side replacement. Both-sides, no-side, and vertical
                // faces keep the super path.
                CtmState ctmState = CtmState.from(
                        getter,
                        state,
                        pos,
                        face,
                        (neighborPos, neighborState, neighborAppearance) ->
                                isConnected(
                                        neighborAppearance,
                                        state,
                                        face));
                if (ctmState.allTrue()) {
                    return super.getQuads(
                            getter,
                            state,
                            pos,
                            face);
                }
                boolean cw = AthenaUtils.getFromDir(
                        state,
                        face.getClockWise());
                boolean ccw = AthenaUtils.getFromDir(
                        state,
                        face.getCounterClockWise());
                if (cw != ccw) {
                    float arm = AthenaUtils.getFromDir(
                            state,
                            face)
                            ? 0.5625F
                            : 0.4375F;
                    return cw
                            ? singleSideCorners(
                                    ctmState,
                                    arm,
                                    true)
                            : singleSideCorners(
                                    ctmState,
                                    arm,
                                    false);
                }
            }
            return super.getQuads(
                    getter,
                    state,
                    pos,
                    face);
        }

        /**
         * 中文：4.7.3 connectCorners=true 的单侧替换 quad 对。leftSide=true 为 CW 侧左列
         * [0,1-arm]，leftSide=false 为 CCW 侧右列 [arm,1]；每侧为上下两个半 quad，槽位
         * 用 CtmUtils.getTexture 真值表取该象限角色。数值逐值取自 4.7.3 字节码 true 分支。
         *
         * <p>English: The 4.7.3 connectCorners=true single-side replacement quad pair.
         * leftSide=true is the CW-side left column [0,1-arm]; leftSide=false is the CCW-side
         * right column [arm,1]; each side is a top/bottom half-quad pair whose slots come
         * from the CtmUtils.getTexture truth table. Values are taken verbatim from the 4.7.3
         * bytecode true branches.
         */
        private static List<AthenaQuad> singleSideCorners(
                CtmState state,
                float arm,
                boolean leftSide) {
            if (leftSide) {
                return List.of(
                        new AthenaQuad(
                                CtmUtils.getTexture(
                                        state.up(),
                                        state.left(),
                                        state.upLeft()),
                                0.0F,
                                1.0F - arm,
                                1.0F,
                                0.5F,
                                Rotation.NONE,
                                0.4375F),
                        new AthenaQuad(
                                CtmUtils.getTexture(
                                        state.down(),
                                        state.left(),
                                        state.downLeft()),
                                0.0F,
                                1.0F - arm,
                                0.5F,
                                0.0F,
                                Rotation.NONE,
                                0.4375F));
            }
            return List.of(
                    new AthenaQuad(
                            CtmUtils.getTexture(
                                    state.up(),
                                    state.right(),
                                    state.upRight()),
                            arm,
                            1.0F,
                            1.0F,
                            0.5F,
                            Rotation.NONE,
                            0.4375F),
                    new AthenaQuad(
                            CtmUtils.getTexture(
                                    state.down(),
                                    state.right(),
                                    state.downRight()),
                            arm,
                            1.0F,
                            0.5F,
                            0.0F,
                            Rotation.NONE,
                            0.4375F));
        }

        @Override
        protected boolean isConnected(
                BlockState neighbor,
                BlockState origin,
                Direction direction) {
            boolean nativeConnected = super.isConnected(
                    neighbor,
                    origin,
                    direction);
            if (nativeConnected) {
                return true;
            }
            boolean rulesConnected = AthenaStateProjection.connects(
                    rules,
                    origin.getBlock(),
                    neighbor.getBlock());
            if (!rulesConnected) {
                return false;
            }
            if (origin.getBlock() == neighbor.getBlock()) {
                return true;
            }
            // 中文：跨 ID 标签组仍复用 Athena 对邻居玻璃板形状的原生判定，不复制其方向算法。
            // English: Cross-id tag groups still reuse Athena's native neighbor-pane shape test without copying its direction algorithm.
            return super.isConnected(
                    neighbor,
                    neighbor,
                    direction);
        }
    }
}
