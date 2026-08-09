package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：保留 Athena 的原生玻璃板几何，只解除东西向顶面几何范围与 UV 范围的错误绑定；
 * 逐段移植已验收 26.1.2 NeoForge PaneTopUvModel 与 1.21.1 ce33d6c
 * FabricPaneTopUvModel。Fabric 26.1.2 世界渲染走 FabricBlockStateModel#emitQuads
 * （AthenaBakedModel 的 Fabric Renderer API 路径），因此除 collectParts/getQuads 修正外，
 * 还必须在 QuadTransform 中应用同一 UV 交换，否则修正被绕过；外层继续沿用
 * FabricGlassPaneSeamCulling（pane 端盖剔除）的包装顺序。
 *
 * <p>English: Retains Athena's native pane geometry while decoupling the east/west top-arm
 * geometry range from its incorrectly bound UV range, porting the accepted 26.1.2 NeoForge
 * PaneTopUvModel and 1.21.1 ce33d6c FabricPaneTopUvModel stage by stage. Fabric 26.1.2
 * world rendering runs through FabricBlockStateModel#emitQuads (the AthenaBakedModel Fabric
 * Renderer API path), so besides the collectParts/getQuads correction the same UV swap must
 * also be applied in a QuadTransform or the fix is bypassed; the FabricGlassPaneSeamCulling
 * (pane cap culling) wrapping order is preserved outside.
 */
final class FabricPaneTopUvModel
        extends WrapperBlockStateModel {
    private static final float EPSILON = 1.0e-6F;

    private final TextureAtlasSprite edgeSprite;
    private final SpriteFinder finder;

    FabricPaneTopUvModel(
            BlockStateModel delegate,
            TextureAtlasSprite edgeSprite,
            SpriteFinder finder) {
        super(Objects.requireNonNull(
                delegate,
                "delegate"));
        this.edgeSprite = Objects.requireNonNull(
                edgeSprite,
                "edgeSprite");
        this.finder = Objects.requireNonNull(
                finder,
                "finder");
    }

    @Override
    public void collectParts(
            RandomSource random,
            List<BlockStateModelPart> output) {
        ArrayList<BlockStateModelPart> source =
                new ArrayList<>();
        super.collectParts(random, source);
        for (BlockStateModelPart part : source) {
            output.add(new PaneTopUvPart(
                    part,
                    edgeSprite));
        }
    }

    /** 中文：保留 Fabric Renderer API 发射路径，并在 Athena 原生 pane quad 发射后把东西向顶臂 UV 交换为已验收值；变换无状态，可跨区块线程共享。 / English: Keeps the Fabric Renderer API emission path and swaps the east/west top-arm UVs to the accepted values around the Athena native pane emission; the transform is stateless and safe to share across chunk-builder threads. */
    @Override
    public void emitQuads(
            QuadEmitter emitter,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            RandomSource random,
            Predicate<Direction> cullTest) {
        emitter.pushTransform(
                new TopArmUvTransform(
                        edgeSprite,
                        finder));
        try {
            super.emitQuads(
                    emitter,
                    level,
                    pos,
                    state,
                    random,
                    cullTest);
        } finally {
            emitter.popTransform();
        }
    }

    /** 中文：批量修正的 identity-preserving 版本；没有可修正 quad 时原样返回源列表。 / English: Identity-preserving batch correction; the source list is returned unchanged when no quad needs correction. */
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

    /** 中文：与 NeoForge PaneTopUvModel.correct 逐值同构：竖直面且精灵为 edge 且 maxX-minX &gt; maxZ-minZ 时，UV 按 u'=getU(localV)、v'=getV(localU) 交换；null-bucket quad 防御性跳过。 / English: Value-for-value isomorphic to the NeoForge PaneTopUvModel.correct: a vertical-face quad bound to the edge sprite with maxX-minX &gt; maxZ-minZ swaps UVs via u'=getU(localV) and v'=getV(localU); null-bucket quads are skipped defensively. */
    static BakedQuad correct(
            BakedQuad quad,
            TextureAtlasSprite edgeSprite) {
        Objects.requireNonNull(quad, "quad");
        Objects.requireNonNull(
                edgeSprite,
                "edgeSprite");
        Direction direction = quad.direction();
        if (direction == null
                || !direction.getAxis().isVertical()
                || !quad.materialInfo()
                        .sprite()
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
                vertex < BakedQuad.VERTEX_COUNT;
                vertex++) {
            minX = Math.min(
                    minX,
                    quad.position(vertex).x());
            minZ = Math.min(
                    minZ,
                    quad.position(vertex).z());
            maxX = Math.max(
                    maxX,
                    quad.position(vertex).x());
            maxZ = Math.max(
                    maxZ,
                    quad.position(vertex).z());
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
        long[] packed = new long[
                BakedQuad.VERTEX_COUNT];
        for (int vertex = 0;
                vertex < BakedQuad.VERTEX_COUNT;
                vertex++) {
            long source = quad.packedUV(vertex);
            UvSwap swapped = swap(
                    UVPair.unpackU(source),
                    UVPair.unpackV(source),
                    edgeSprite);
            packed[vertex] = UVPair.pack(
                    swapped.u(),
                    swapped.v());
        }
        return new BakedQuad(
                quad.position(0),
                quad.position(1),
                quad.position(2),
                quad.position(3),
                packed[0],
                packed[1],
                packed[2],
                packed[3],
                direction,
                quad.materialInfo());
    }

    /** 中文：纯 UV 交换公式：localU=(u-u0)/width、localV=(v-v0)/height，修正后 u'=getU(localV)、v'=getV(localU)；供 BakedQuad 与 QuadTransform 两条路径共用。 / English: Pure UV swap formula: localU=(u-u0)/width, localV=(v-v0)/height, then u'=getU(localV) and v'=getV(localU); shared by the BakedQuad and QuadTransform paths. */
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

    private static final class PaneTopUvPart
            implements BlockStateModelPart {
        private final BlockStateModelPart delegate;
        private final TextureAtlasSprite edgeSprite;

        private PaneTopUvPart(
                BlockStateModelPart delegate,
                TextureAtlasSprite edgeSprite) {
            this.delegate = Objects.requireNonNull(
                    delegate,
                    "delegate");
            this.edgeSprite = Objects.requireNonNull(
                    edgeSprite,
                    "edgeSprite");
        }

        @Override
        public List<BakedQuad> getQuads(
                Direction direction) {
            return correct(
                    delegate.getQuads(direction),
                    edgeSprite);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return delegate.useAmbientOcclusion();
        }

        @Override
        public Material.Baked particleMaterial() {
            return delegate.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return delegate.materialFlags();
        }
    }

    /** 中文：东西向顶臂 UV 交换的 QuadTransform：以 lightFace（几何面）判定竖直面，经 SpriteFinder 反查精灵名与 edge 比对，再按同一 swap 公式改写四个顶点 UV。 / English: QuadTransform for the east/west top-arm UV swap: lightFace (the geometry face) decides the vertical face, SpriteFinder resolves the sprite name against the edge sprite, and the same swap formula rewrites the four vertex UVs. */
    private static final class TopArmUvTransform
            implements QuadTransform {
        private final TextureAtlasSprite edgeSprite;
        private final SpriteFinder finder;

        private TopArmUvTransform(
                TextureAtlasSprite edgeSprite,
                SpriteFinder finder) {
            this.edgeSprite = Objects.requireNonNull(
                    edgeSprite,
                    "edgeSprite");
            this.finder = Objects.requireNonNull(
                    finder,
                    "finder");
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
                    finder.find(quad);
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
                minX = Math.min(
                        minX,
                        quad.posByIndex(
                                vertex,
                                0));
                minZ = Math.min(
                        minZ,
                        quad.posByIndex(
                                vertex,
                                2));
                maxX = Math.max(
                        maxX,
                        quad.posByIndex(
                                vertex,
                                0));
                maxZ = Math.max(
                        maxZ,
                        quad.posByIndex(
                                vertex,
                                2));
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
