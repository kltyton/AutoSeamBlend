package com.kltyton.autoseamblend.neoforge.runtime.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
 * 中文：RED 测试——overlay 侧面 quad 的顶点 y 与纹理 V 必须保持 26.1.2 MutableQuad
 * 语义：面上沿（y=1）绑定 v0（纹理上沿）、面下沿（y=0）绑定 v1（纹理下沿）。当前
 * 1.21.1 手写 positions() 的侧面顶点 y 序为 0,1,1,0，会把纹理上沿贴到面下沿（上下反），
 * 本测试应失败。UP/DOWN 面 y 恒定，不参与本断言。
 *
 * <p>English: RED test. Overlay side-face quads must keep the 26.1.2 MutableQuad
 * binding: face top (y=1) maps to v0 (texture top) and face bottom (y=0) maps to v1
 * (texture bottom). The current 1.21.1 handwritten positions() uses side-face vertex
 * y order 0,1,1,0, which pastes the texture top onto the face bottom (vertically
 * flipped); this test is expected to fail. UP/DOWN faces have constant y and are not
 * asserted here.
 */
class NeoForgeQuadRetexturingOverlayVerticalOrientationTest {

    @Test
    void sideFacesBindTextureVToWorldYTopDown() {
        TextureAtlasSprite sprite = TestSprite.INSTANCE;
        VertexFormat format = DefaultVertexFormat.BLOCK;
        int stride = format.getVertexSize() / 4;
        int uvOffset = format.getOffset(
                VertexFormatElement.UV0) / 4;

        for (Direction face : Direction.values()) {
            if (face.getAxis() == Direction.Axis.Y) {
                continue;
            }
            BakedQuad quad = NeoForgeQuadRetexturing.overlay(
                    face,
                    sprite,
                    0xFFFFFFFF);
            int[] vertices = quad.getVertices();
            // 中文：26.1.2 MutableQuad 侧面顶点 y 序为 1,0,0,1，UV 序 v0,v1,v1,v0。
            // English: 26.1.2 MutableQuad side-face vertex y order is 1,0,0,1 with
            // UV order v0,v1,v1,v0.
            float[] expectedY = {1.0F, 0.0F, 0.0F, 1.0F};
            for (int vertex = 0; vertex < 4; vertex++) {
                int base = vertex * stride;
                float y = Float.intBitsToFloat(
                        vertices[base + 1]);
                float v = Float.intBitsToFloat(
                        vertices[base + uvOffset + 1]);
                assertEquals(
                        expectedY[vertex],
                        y,
                        1.0e-4F,
                        "vertex " + vertex + " y on face "
                                + face);
                float expectedV = expectedY[vertex] > 0.5F
                        ? sprite.getV0()
                        : sprite.getV1();
                assertEquals(
                        expectedV,
                        v,
                        1.0e-4F,
                        "vertex " + vertex + " v on face "
                                + face);
            }
        }
    }

    /**
     * 中文：最小可用测试精灵；仅提供 UV 供 overlay 构造使用。
     *
     * <p>English: Minimal test sprite providing UVs for overlay construction.
     */
    private static final class TestSprite
            extends TextureAtlasSprite {
        private static final TestSprite INSTANCE =
                new TestSprite();

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
