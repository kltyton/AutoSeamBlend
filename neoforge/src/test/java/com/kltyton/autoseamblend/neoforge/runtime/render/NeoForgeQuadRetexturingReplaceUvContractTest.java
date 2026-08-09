package com.kltyton.autoseamblend.neoforge.runtime.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.util.List;
import java.util.Map;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：NeoForgeQuadRetexturing.replace 的 UV 合同回归测试。source 与 target 精灵放在
 * 同一假定 2048x2048 方块 Atlas 的不同 16x16 区域；构造四顶点 UV 覆盖 source bounds 的
 * BakedQuad，调用 replace 后断言：返回 quad 的 sprite 恒等为 target；四顶点 UV 全部落在
 * target bounds；四角归一化坐标与 source 一致；target bounds 推导像素宽高均为 16。
 *
 * <p>English: UV contract regression test for NeoForgeQuadRetexturing.replace. The source
 * and target sprites sit in different 16x16 regions of one assumed 2048x2048 block atlas; a
 * BakedQuad whose four vertex UVs span the source bounds is passed to replace. Asserts: the
 * returned quad's sprite identity is target; all four UVs land inside the target bounds; the
 * four corner normalized coordinates match the source; and the target bounds imply 16x16
 * pixels.
 */
class NeoForgeQuadRetexturingReplaceUvContractTest {
    private static final int ATLAS_SIZE = 2048;
    private static final float EPS = 1.0e-4F;
    private static final VertexFormat FORMAT = DefaultVertexFormat.BLOCK;
    private static final int STRIDE = FORMAT.getVertexSize() / 4;
    private static final int UV0_OFFSET =
            FORMAT.getOffset(VertexFormatElement.UV0) / 4;
    private static final int COLOR_OFFSET =
            FORMAT.getOffset(VertexFormatElement.COLOR) / 4;

    @BeforeAll
    static void bootstrapRegistries() {
        // 中文：独立 JVM 测试需要游戏版本与注册表引导，避免静态初始化抛
        // ExceptionInInitializerError；与同包 overlay vertex 测试的构造方式一致。
        // English: Standalone JVM tests need a game version and registry bootstrap to avoid
        // ExceptionInInitializerError; same construction pattern as the overlay vertex test.
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        LoadingModList.of(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of());
        Bootstrap.bootStrap();
    }

    @Test
    void replaceRemapsUvIntoTargetBoundsPreservingNormalizedCorners() {
        TextureAtlasSprite source = TestSprite.at(32, 0);
        TextureAtlasSprite target = TestSprite.at(96, 16);

        BakedQuad replaced = NeoForgeQuadRetexturing.replace(
                fullFrameQuad(source, Direction.NORTH),
                target);

        assertSame(
                target,
                replaced.getSprite(),
                "returned quad sprite identity must be target");

        float[] us = new float[4];
        float[] vs = new float[4];
        for (int vertex = 0; vertex < 4; vertex++) {
            int base = vertex * STRIDE + UV0_OFFSET;
            us[vertex] = Float.intBitsToFloat(
                    replaced.getVertices()[base]);
            vs[vertex] = Float.intBitsToFloat(
                    replaced.getVertices()[base + 1]);
            assertTrue(
                    us[vertex] >= target.getU0() - EPS
                            && us[vertex] <= target.getU1() + EPS,
                    "vertex " + vertex + " u must land inside target bounds: "
                            + us[vertex]);
            assertTrue(
                    vs[vertex] >= target.getV0() - EPS
                            && vs[vertex] <= target.getV1() + EPS,
                    "vertex " + vertex + " v must land inside target bounds: "
                            + vs[vertex]);
        }

        float[][] expected = {
            {0.0F, 0.0F},
            {1.0F, 0.0F},
            {1.0F, 1.0F},
            {0.0F, 1.0F}
        };
        float targetWidth = target.getU1() - target.getU0();
        float targetHeight = target.getV1() - target.getV0();
        for (int vertex = 0; vertex < 4; vertex++) {
            assertEquals(
                    expected[vertex][0],
                    (us[vertex] - target.getU0()) / targetWidth,
                    EPS,
                    "normalized u of vertex " + vertex);
            assertEquals(
                    expected[vertex][1],
                    (vs[vertex] - target.getV0()) / targetHeight,
                    EPS,
                    "normalized v of vertex " + vertex);
        }

        assertEquals(
                16.0,
                (target.getU1() - target.getU0()) * ATLAS_SIZE,
                0.01,
                "target u bounds must imply 16 pixels");
        assertEquals(
                16.0,
                (target.getV1() - target.getV0()) * ATLAS_SIZE,
                0.01,
                "target v bounds must imply 16 pixels");
    }

    /**
     * 中文：构造四顶点 UV 恰好覆盖 sprite atlas bounds 的整帧 BakedQuad（BLOCK 格式）。
     *
     * <p>English: Builds a full-frame BLOCK-format BakedQuad whose four vertex UVs span the
     * sprite's atlas bounds exactly.
     */
    private static BakedQuad fullFrameQuad(
            TextureAtlasSprite sprite,
            Direction face) {
        int[] vertices = new int[4 * STRIDE];
        float[][] corners = {
            {sprite.getU0(), sprite.getV0()},
            {sprite.getU1(), sprite.getV0()},
            {sprite.getU1(), sprite.getV1()},
            {sprite.getU0(), sprite.getV1()}
        };
        for (int vertex = 0; vertex < 4; vertex++) {
            int base = vertex * STRIDE;
            vertices[base] = Float.floatToRawIntBits(0.0F);
            vertices[base + 1] = Float.floatToRawIntBits(0.0F);
            vertices[base + 2] = Float.floatToRawIntBits(0.0F);
            vertices[base + COLOR_OFFSET] = 0xFFFFFFFF;
            vertices[base + UV0_OFFSET] =
                    Float.floatToRawIntBits(corners[vertex][0]);
            vertices[base + UV0_OFFSET + 1] =
                    Float.floatToRawIntBits(corners[vertex][1]);
        }
        return new BakedQuad(
                vertices,
                -1,
                face,
                sprite,
                true);
    }

    /** 中文：位于假定 2048x2048 Atlas 指定 (x,y) 的 16x16 测试精灵。 / English: 16x16 test sprite at (x,y) of the assumed 2048x2048 atlas. */
    private static final class TestSprite extends TextureAtlasSprite {
        private TestSprite(
                ResourceLocation atlasLocation,
                SpriteContents contents,
                int x,
                int y) {
            super(
                    atlasLocation,
                    contents,
                    ATLAS_SIZE,
                    ATLAS_SIZE,
                    x,
                    y);
        }

        private static TestSprite at(int x, int y) {
            return new TestSprite(
                    TextureAtlas.LOCATION_BLOCKS,
                    MissingTextureAtlasSprite.create(),
                    x,
                    y);
        }
    }
}
