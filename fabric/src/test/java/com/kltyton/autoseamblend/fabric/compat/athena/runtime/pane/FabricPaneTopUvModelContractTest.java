package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 契约——Fabric 顶臂 UV 修正必须等于已验收 NeoForge PaneTopUvModel 语义：
 * 东西向宽顶臂（maxX-minX > maxZ-minZ）且精灵为 edge 时，UV 按
 * u'=getU(localV)、v'=getV(localU) 交换；南北向/非 edge 精灵保持不变。当前 Fabric
 * 没有 PaneTopUvModel 等价路径，测试先红。
 *
 * <p>English: RED contract -- the Fabric top-arm UV correction must equal the accepted
 * NeoForge PaneTopUvModel semantics: an east/west wide top arm (maxX-minX >
 * maxZ-minZ) bound to the edge sprite swaps UVs via u'=getU(localV) and
 * v'=getV(localU); north/south arms and foreign sprites stay unchanged. The Fabric side
 * has no PaneTopUvModel equivalent yet, so the test fails first.
 */
class FabricPaneTopUvModelContractTest {

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void eastWestTopArmRemapsUvVertically() {
        TestSprite edge = TestSprite.INSTANCE;
        BakedQuad source = wideTopQuad(edge, 0.3F, 0.7F);

        BakedQuad corrected =
                FabricPaneTopUvModel.correct(
                        source,
                        edge);

        float u = u(corrected);
        float v = v(corrected);
        // 中文：归一化精灵下 localU=u、localV=v，修正后 u'=v、v'=u，与 NeoForge 测试一致。
        // English: With a normalized sprite localU=u and localV=v, the corrected
        // UVs are u'=v and v'=u, matching the NeoForge test.
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

    @Test
    void northSouthArmStaysUnchanged() {
        TestSprite edge = TestSprite.INSTANCE;
        BakedQuad source = narrowTopQuad(edge, 0.3F, 0.7F);

        BakedQuad result =
                FabricPaneTopUvModel.correct(
                        source,
                        edge);

        assertSame(
                source,
                result,
                "a north/south top arm (maxX-minX <= maxZ-minZ) must stay unchanged");
    }

    @Test
    void foreignSpriteStaysUnchanged() {
        TestSprite edge = TestSprite.INSTANCE;
        TestSprite body = TestSprite.create("minecraft:block/glass_pane");
        BakedQuad source = wideTopQuad(body, 0.3F, 0.7F);

        BakedQuad result =
                FabricPaneTopUvModel.correct(
                        source,
                        edge);

        assertSame(
                source,
                result,
                "a quad bound to a non-edge sprite must stay unchanged");
    }

    @Test
    void pureUvSwapMatchesNeoForgeFormula() {
        TestSprite edge = TestSprite.INSTANCE;

        FabricPaneTopUvModel.UvSwap swapped =
                FabricPaneTopUvModel.swap(
                        0.3F,
                        0.7F,
                        edge);

        assertEquals(
                0.7F,
                swapped.u(),
                1.0e-4F,
                "swapped U must equal the source V");
        assertEquals(
                0.3F,
                swapped.v(),
                1.0e-4F,
                "swapped V must equal the source U");
    }

    private static float u(BakedQuad quad) {
        VertexFormat format = DefaultVertexFormat.BLOCK;
        int uvOffset = format.getOffset(
                VertexFormatElement.UV0) / 4;
        return Float.intBitsToFloat(
                quad.getVertices()[uvOffset]);
    }

    private static float v(BakedQuad quad) {
        VertexFormat format = DefaultVertexFormat.BLOCK;
        int uvOffset = format.getOffset(
                VertexFormatElement.UV0) / 4;
        return Float.intBitsToFloat(
                quad.getVertices()[uvOffset + 1]);
    }

    private static BakedQuad wideTopQuad(
            TextureAtlasSprite sprite,
            float u,
            float v) {
        return topQuad(
                sprite,
                u,
                v,
                new float[][] {
                    {0.0F, 16.0F, 0.0F},
                    {16.0F, 16.0F, 0.0F},
                    {16.0F, 16.0F, 4.0F},
                    {0.0F, 16.0F, 4.0F}
                });
    }

    private static BakedQuad narrowTopQuad(
            TextureAtlasSprite sprite,
            float u,
            float v) {
        return topQuad(
                sprite,
                u,
                v,
                new float[][] {
                    {0.0F, 16.0F, 0.0F},
                    {4.0F, 16.0F, 0.0F},
                    {4.0F, 16.0F, 16.0F},
                    {0.0F, 16.0F, 16.0F}
                });
    }

    private static BakedQuad topQuad(
            TextureAtlasSprite sprite,
            float u,
            float v,
            float[][] positions) {
        VertexFormat format = DefaultVertexFormat.BLOCK;
        int stride = format.getVertexSize() / 4;
        int uvOffset = format.getOffset(
                VertexFormatElement.UV0) / 4;
        int[] vertices = new int[stride * 4];
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

    /** 中文：归一化 0..1 UV 的测试精灵；与 NeoForge TopUvContractTest 同型。 / English: Test sprite with normalized 0..1 UVs, same shape as the NeoForge TopUvContractTest. */
    private static final class TestSprite
            extends TextureAtlasSprite {
        private static final TestSprite INSTANCE =
                create("minecraft:block/glass_pane_top");

        private static TestSprite create(String name) {
            NativeImage image =
                    new NativeImage(16, 16, false);
            SpriteContents contents = new SpriteContents(
                    ResourceLocation.parse(name),
                    new FrameSize(16, 16),
                    image,
                    ResourceMetadata.EMPTY);
            return new TestSprite(
                    TextureAtlas.LOCATION_BLOCKS,
                    contents);
        }

        private TestSprite(
                ResourceLocation atlasLocation,
                SpriteContents contents) {
            super(
                    atlasLocation,
                    contents,
                    16,
                    16,
                    0,
                    0);
        }
    }
}
