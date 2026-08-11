package com.kltyton.autoseamblend.forge.compat.athena.runtime.pane;

import com.kltyton.autoseamblend.forge.testing.ForgeTestBootstrap;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.util.List;
import java.util.Map;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.client.model.data.ModelData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 测试——PaneTopUvModel 的 5 参 getQuads 必须与 3 参一样应用顶面 UV 修正。
 * 当前类只重写 3 参，世界渲染与预览都走 5 参（BakedModelWrapper 直接委托），修正被
 * 绕过，本测试应失败。
 *
 * <p>English: RED test. PaneTopUvModel's 5-arg getQuads must apply the top-arm UV
 * correction just like the 3-arg overload. The class currently overrides only the 3-arg
 * overload while world rendering and the scene preview use the 5-arg overload
 * (BakedModelWrapper delegates it), so the correction is bypassed and this test fails.
 */
class AthenaGeneratedPaneModelFactoryTopUvContractTest {

    @BeforeAll
    static void bootstrapRegistries() {
        // 中文：独立 JVM 测试需要游戏版本与注册表引导，否则 RenderType/Blocks 静态
        // 初始化抛 ExceptionInInitializerError；与 CTM 测试同型，仅测试初始化。
        // English: Standalone JVM tests need a game version and registry bootstrap or
        // RenderType/Blocks static init throws ExceptionInInitializerError; same shape
        // as the CTM tests, test-only initialization.
        ForgeTestBootstrap.bootStrap();
    }

    @Test
    void fiveArgQuadsAppliesTopUvCorrection() {
        TestSprite edge = TestSprite.INSTANCE;
        BakedQuad source = wideTopQuad(edge, 0.3F, 0.7F);
        AthenaGeneratedPaneModelFactory.PaneTopUvModel model =
                new AthenaGeneratedPaneModelFactory.PaneTopUvModel(
                        new QuadDelegate(source),
                        edge);
        BlockState state = Blocks.GLASS_PANE.defaultBlockState();

        List<BakedQuad> quads = model.getQuads(
                state,
                Direction.UP,
                RandomSource.create(0),
                ModelData.EMPTY,
                RenderType.cutout());

        VertexFormat format = DefaultVertexFormat.BLOCK;
        int uvOffset = 4 /* UV0 int offset in DefaultVertexFormat.BLOCK */;
        BakedQuad corrected = quads.get(0);
        float u = Float.intBitsToFloat(
                corrected.getVertices()[uvOffset]);
        float v = Float.intBitsToFloat(
                corrected.getVertices()[uvOffset + 1]);
        // 中文：归一化精灵下 localU=u、localV=v，修正后 u'=v、v'=u。
        // English: With a normalized sprite localU=u and localV=v, the corrected
        // UVs are u'=v and v'=u.
        assertEquals(
                0.7F,
                u,
                1.0e-4F,
                "corrected U must equal the source V");
        assertEquals(
                0.3F,
                v,
                1.0e-4F,
                "corrected V must equal the source U");
    }

    /**
     * 中文：东西向宽顶臂 quad（maxX-minX > maxZ-minZ），带可预测的源 UV。
     * English: East-west wide top-arm quad (maxX-minX > maxZ-minZ) with predictable UVs.
     */
    private static BakedQuad wideTopQuad(
            TextureAtlasSprite sprite,
            float u,
            float v) {
        VertexFormat format = DefaultVertexFormat.BLOCK;
        int stride = format.getVertexSize() / 4;
        int uvOffset = 4 /* UV0 int offset in DefaultVertexFormat.BLOCK */;
        int[] vertices = new int[stride * 4];
        float[][] positions = {
            {0.0F, 16.0F, 0.0F},
            {16.0F, 16.0F, 0.0F},
            {16.0F, 16.0F, 4.0F},
            {0.0F, 16.0F, 4.0F}
        };
        for (int vertex = 0; vertex < 4; vertex++) {
            int base = vertex * stride;
            vertices[base] = Float.floatToRawIntBits(
                    positions[vertex][0]);
            vertices[base + 1] = Float.floatToRawIntBits(
                    positions[vertex][1]);
            vertices[base + 2] = Float.floatToRawIntBits(
                    positions[vertex][2]);
            vertices[base + 3] = 0xFFFFFFFF;
            vertices[base + uvOffset] =
                    Float.floatToRawIntBits(u);
            vertices[base + uvOffset + 1] =
                    Float.floatToRawIntBits(v);
        }
        return new BakedQuad(
                vertices,
                -1,
                Direction.UP,
                sprite,
                false);
    }

    /**
     * 中文：固定 quad 的 BakedModel 替身；只服务 3 参/5 参 getQuads。
     * English: BakedModel double returning a fixed quad; serves only the 3-arg
     * and 5-arg getQuads.
     */
    private static final class QuadDelegate implements BakedModel {
        private final BakedQuad quad;

        private QuadDelegate(BakedQuad quad) {
            this.quad = quad;
        }

        @Override
        public List<BakedQuad> getQuads(
                BlockState state,
                Direction direction,
                RandomSource random) {
            return List.of(quad);
        }

        @Override
        public List<BakedQuad> getQuads(
                BlockState state,
                Direction direction,
                RandomSource random,
                ModelData modelData,
                RenderType renderType) {
            return List.of(quad);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return true;
        }

        @Override
        public boolean isGui3d() {
            return false;
        }

        @Override
        public boolean usesBlockLight() {
            return false;
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return TestSprite.INSTANCE;
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }

    /**
     * 中文：最小可用测试精灵；仅提供 UV 归一化 0..1，供 UV 修正计算使用。
     * English: Minimal test sprite exposing normalized 0..1 UVs for the UV
     * correction math.
     */
    private static final class TestSprite extends TextureAtlasSprite {
        private static final TestSprite INSTANCE = new TestSprite();

        private TestSprite() {
            super(
                    TextureAtlas.LOCATION_BLOCKS,
                    MissingTextureAtlasSprite.create(),
                    16,
                    16,
                    0,
                    0);
        }
    }
}
