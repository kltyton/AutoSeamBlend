package com.kltyton.autoseamblend.neoforge.runtime.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 测试——overlay quad 必须写入与面一致的顶点法线（DefaultVertexFormat.BLOCK
 * NORMAL 分量）。当前 overlay() 的裸顶点数组未写 NORMAL，四顶点法线为零，本测试应失败。
 * 注意：NORMAL 是归一化有符号字节，单位分量编码为 face step * 127（非 ±1），零轴保持 0；
 * 不断言 UV2（光照贴图），UV2 为零在当前实现中是合法的。
 *
 * English: RED test. The overlay quad must write per-vertex normals consistent with the
 * face (DefaultVertexFormat.BLOCK NORMAL component). The current overlay() builds a raw
 * vertex array without writing NORMAL, so all four normals are zero and this test is
 * expected to fail. NORMAL is a normalized signed byte: the unit component encodes
 * face step * 127 (not +/-1) and the zero axes stay 0. UV2 (lightmap) is deliberately
 * not asserted: zero UV2 is legal here.
 */
class NeoForgeQuadRetexturingOverlayVertexContractTest {

    @Test
    void overlayWritesFaceNormalsOnAllVertices() {
        TextureAtlasSprite sprite = TestSprite.INSTANCE;
        VertexFormat format = DefaultVertexFormat.BLOCK;
        int stride = format.getVertexSize() / 4;
        int normalOffset = format.getOffset(
                VertexFormatElement.NORMAL) / 4;

        for (Direction face : Direction.values()) {
            BakedQuad quad = NeoForgeQuadRetexturing.overlay(
                    face,
                    sprite,
                    0xFFFFFFFF);
            int[] vertices = quad.getVertices();
            for (int vertex = 0; vertex < 4; vertex++) {
                int normal = vertices[
                        vertex * stride + normalOffset];
                assertNotEquals(
                        0,
                        normal,
                        "vertex " + vertex + " of face "
                                + face + " must carry a normal");
                int normalX = (byte) (normal & 0xFF);
                int normalY = (byte) ((normal >>> 8) & 0xFF);
                int normalZ = (byte) ((normal >>> 16) & 0xFF);
                assertEquals(
                        (byte) (face.getStepX() * 127),
                        normalX,
                        "normal.x of vertex " + vertex
                                + " face " + face);
                assertEquals(
                        (byte) (face.getStepY() * 127),
                        normalY,
                        "normal.y of vertex " + vertex
                                + " face " + face);
                assertEquals(
                        (byte) (face.getStepZ() * 127),
                        normalZ,
                        "normal.z of vertex " + vertex
                                + " face " + face);
            }
        }
    }

    /**
     * 中文：最小可用测试精灵；仅提供 UV 归一化 0..1，供 overlay 构造 UV 使用。
     * English: Minimal test sprite exposing normalized 0..1 UVs for overlay UV building.
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
