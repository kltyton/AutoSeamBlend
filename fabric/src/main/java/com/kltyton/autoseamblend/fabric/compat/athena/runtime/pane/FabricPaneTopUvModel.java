package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：保留 Athena 的原生玻璃板几何，只解除东西向顶面几何范围与 UV 范围的错误绑定；
 * 逐段移植已验收 1.21.1 NeoForge PaneTopUvModel。Fabric 世界渲染走 emitBlockQuads
 * （AthenaBakedModel 的 Fabric Renderer API 路径），因此除 getQuads 修正外，还必须在
 * QuadTransform 中应用同一 UV 交换，否则修正被绕过。
 *
 * <p>English: Retains Athena's native pane geometry while decoupling the east/west top-arm
 * geometry range from its incorrectly bound UV range, porting the accepted 1.21.1 NeoForge
 * PaneTopUvModel stage by stage. Fabric world rendering runs through emitBlockQuads (the
 * AthenaBakedModel Fabric Renderer API path), so besides the getQuads correction the same
 * UV swap must also be applied in a QuadTransform or the fix is bypassed.
 */
final class FabricPaneTopUvModel
        extends ForwardingBakedModel {
    private static final float EPSILON = 1.0e-6F;
    private static final VertexFormat BLOCK_FORMAT =
            DefaultVertexFormat.BLOCK;
    private static final int STRIDE_INTS =
            BLOCK_FORMAT.getVertexSize() / 4;
    private static final int UV0_OFFSET_INTS =
            BLOCK_FORMAT.getOffset(
                    VertexFormatElement.UV0) / 4;

    private final TextureAtlasSprite edgeSprite;

    FabricPaneTopUvModel(
            BakedModel delegate,
            TextureAtlasSprite edgeSprite) {
        super();
        this.wrapped = Objects.requireNonNull(
                delegate,
                "delegate");
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
                wrapped.getQuads(
                        state,
                        direction,
                        random),
                edgeSprite);
    }

    /**
     * 中文：保留 Fabric Renderer API 发射路径，并在 Athena 原生 pane quad 发射后把东西向
     * 顶臂 UV 交换为已验收值；变换无状态，可跨区块线程共享。
     *
     * <p>English: Keeps the Fabric Renderer API emission path and swaps the east/west top-arm
     * UVs to the accepted values around the Athena native pane emission; the transform is
     * stateless and safe to share across chunk-builder threads.
     */
    @Override
    public void emitBlockQuads(
            BlockAndTintGetter level,
            BlockState state,
            BlockPos pos,
            Supplier<RandomSource> randomSupplier,
            RenderContext context) {
        context.pushTransform(
                new TopArmUvTransform(edgeSprite));
        try {
            super.emitBlockQuads(
                    level,
                    state,
                    pos,
                    randomSupplier,
                    context);
        } finally {
            context.popTransform();
        }
    }

    /**
     * 中文：批量修正的 identity-preserving 版本；没有可修正 quad 时原样返回源列表。
     *
     * <p>English: Identity-preserving batch correction; the source list is returned
     * unchanged when no quad needs correction.
     */
    static List<BakedQuad> correct(
            List<BakedQuad> source,
            TextureAtlasSprite edgeSprite) {
        if (source.isEmpty()) {
            return source;
        }
        ArrayList<BakedQuad> output = null;
        for (int index = 0;
                index < source.size();
                index++) {
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
                output.addAll(
                        source.subList(0, index));
            }
            output.add(replacement);
        }
        return output == null
                ? source
                : List.copyOf(output);
    }

    /**
     * 中文：与 NeoForge PaneTopUvModel.correct 逐值同构：竖直面且精灵为 edge 且
     * maxX-minX > maxZ-minZ 时，UV 按 u'=getU(localV)、v'=getV(localU) 交换。Fabric
     * getQuads 回退路径可能收到 null-bucket quad，此处防御性跳过（NeoForge 该路径恒
     * 有方向）。
     *
     * <p>English: Value-for-value isomorphic to the NeoForge PaneTopUvModel.correct: a
     * vertical-face quad bound to the edge sprite with maxX-minX > maxZ-minZ swaps UVs via
     * u'=getU(localV) and v'=getV(localU). The Fabric getQuads fallback can receive
     * null-bucket quads, so null directions are skipped defensively (the NeoForge path
     * always has a direction).
     */
    static BakedQuad correct(
            BakedQuad quad,
            TextureAtlasSprite edgeSprite) {
        Objects.requireNonNull(quad, "quad");
        Objects.requireNonNull(edgeSprite, "edgeSprite");
        Direction direction = quad.getDirection();
        if (direction == null
                || !direction.getAxis().isVertical()
                || !quad.getSprite()
                        .contents()
                        .name()
                        .equals(
                                edgeSprite.contents()
                                        .name())) {
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
            UvSwap swapped = swap(
                    Float.intBitsToFloat(
                            vertices[base]),
                    Float.intBitsToFloat(
                            vertices[base + 1]),
                    edgeSprite);
            vertices[base] =
                    Float.floatToRawIntBits(
                            swapped.u());
            vertices[base + 1] =
                    Float.floatToRawIntBits(
                            swapped.v());
        }
        return new BakedQuad(
                vertices,
                quad.getTintIndex(),
                quad.getDirection(),
                quad.getSprite(),
                quad.isShade());
    }

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
     * 中文：纯 UV 交换公式：localU=(u-u0)/width、localV=(v-v0)/height，修正后
     * u'=getU(localV)、v'=getV(localU)；供 BakedQuad 与 QuadTransform 两条路径共用。
     *
     * <p>English: Pure UV swap formula: localU=(u-u0)/width, localV=(v-v0)/height, then
     * u'=getU(localV) and v'=getV(localU); shared by the BakedQuad and QuadTransform paths.
     */
    static UvSwap swap(
            float u,
            float v,
            TextureAtlasSprite edgeSprite) {
        float atlasWidth = edgeSprite.getU1()
                - edgeSprite.getU0();
        float atlasHeight = edgeSprite.getV1()
                - edgeSprite.getV0();
        if (Math.abs(atlasWidth) <= EPSILON
                || Math.abs(atlasHeight) <= EPSILON) {
            return new UvSwap(u, v);
        }
        float localU = (u - edgeSprite.getU0())
                / atlasWidth;
        float localV = (v - edgeSprite.getV0())
                / atlasHeight;
        return new UvSwap(
                edgeSprite.getU(localV),
                edgeSprite.getV(localU));
    }

    record UvSwap(float u, float v) {}

    /**
     * 中文：东西向顶臂 UV 交换的 QuadTransform：以 lightFace（几何面）判定竖直面，经
     * SpriteFinder 反查精灵名与 edge 比对，再按同一 swap 公式改写四个顶点 UV。
     *
     * <p>English: QuadTransform for the east/west top-arm UV swap: lightFace (the geometry
     * face) decides the vertical face, SpriteFinder resolves the sprite name against the
     * edge sprite, and the same swap formula rewrites the four vertex UVs.
     */
    private static final class TopArmUvTransform
            implements RenderContext.QuadTransform {
        private final TextureAtlasSprite edgeSprite;

        private TopArmUvTransform(
                TextureAtlasSprite edgeSprite) {
            this.edgeSprite = Objects.requireNonNull(
                    edgeSprite,
                    "edgeSprite");
        }

        @Override
        public boolean transform(
                MutableQuadView quad) {
            Direction face = quad.lightFace();
            if (face == null
                    || !face.getAxis().isVertical()) {
                return true;
            }
            TextureAtlasSprite sprite =
                    SpriteFinder.get(
                                    Minecraft.getInstance()
                                            .getModelManager()
                                            .getAtlas(
                                                    TextureAtlas
                                                            .LOCATION_BLOCKS))
                            .find(quad);
            if (sprite == null
                    || !sprite.contents()
                            .name()
                            .equals(
                                    edgeSprite.contents()
                                            .name())) {
                return true;
            }
            float minX = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            for (int vertex = 0;
                    vertex < 4;
                    vertex++) {
                float x = quad.posByIndex(
                        vertex,
                        0);
                float z = quad.posByIndex(
                        vertex,
                        2);
                minX = Math.min(minX, x);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxZ = Math.max(maxZ, z);
            }
            if (maxX - minX <= maxZ - minZ + EPSILON) {
                return true;
            }
            for (int vertex = 0;
                    vertex < 4;
                    vertex++) {
                UvSwap swapped = swap(
                        quad.u(vertex),
                        quad.v(vertex),
                        edgeSprite);
                quad.uv(
                        vertex,
                        swapped.u(),
                        swapped.v());
            }
            return true;
        }
    }
}
