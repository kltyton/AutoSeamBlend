package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;

/**
 * 中文：测试共享的 26.1.2 BakedQuad 构造器；位置与 UV 可自由指定，供顶臂 UV 修正与
 * pane 表面角色选择契约使用。
 *
 * <p>English: Shared 1.20.1 BakedQuad builder for tests; positions and UVs are freely
 * configurable for the top-arm UV and pane surface-role contracts.
 */
final class PaneTestQuads {
    private static final int VERTEX_SIZE_INTS = 8; // DefaultVertexFormat.BLOCK
    private static final int UV0_INT_OFFSET = 4;

    private PaneTestQuads() {}

    /** 中文：东西向宽顶臂（X 跨 16、Z 跨 4）。 / English: East/west wide top arm (X span 16, Z span 4). */
    static BakedQuad wideTop(
            TextureAtlasSprite sprite,
            float u,
            float v) {
        return top(
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

    /** 中文：南北向窄顶臂（X 跨 4、Z 跨 16）。 / English: North/south narrow top arm (X span 4, Z span 16). */
    static BakedQuad narrowTop(
            TextureAtlasSprite sprite,
            float u,
            float v) {
        return top(
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

    /** 中文：任意方向的四顶点 quad，UV 四顶点相同。 / English: An arbitrary-direction quad with one shared UV. */
    static BakedQuad quad(
            TextureAtlasSprite sprite,
            Direction direction,
            float u,
            float v,
            float[][] positions) {
        int[] vertices = new int[4 * VERTEX_SIZE_INTS];
        for (int index = 0; index < 4; index++) {
            int base = index * VERTEX_SIZE_INTS;
            vertices[base] = Float.floatToRawIntBits(
                    positions[index][0]);
            vertices[base + 1] = Float.floatToRawIntBits(
                    positions[index][1]);
            vertices[base + 2] = Float.floatToRawIntBits(
                    positions[index][2]);
            vertices[base + 3] = 0xFFFFFFFF; // white color
            vertices[base + UV0_INT_OFFSET] =
                    Float.floatToRawIntBits(u);
            vertices[base + UV0_INT_OFFSET + 1] =
                    Float.floatToRawIntBits(v);
            vertices[base + 6] = 0; // uv1 light
            vertices[base + 7] = packNormal(
                    0.0F,
                    1.0F,
                    0.0F);
        }
        return new BakedQuad(
                vertices,
                -1,
                direction,
                sprite,
                false);
    }

    private static int packNormal(
            float x,
            float y,
            float z) {
        int bx = (int) (x * 127.0F) & 0xFF;
        int by = (int) (y * 127.0F) & 0xFF;
        int bz = (int) (z * 127.0F) & 0xFF;
        return bx | (by << 8) | (bz << 16);
    }

    private static BakedQuad top(
            TextureAtlasSprite sprite,
            float u,
            float v,
            float[][] positions) {
        return quad(
                sprite,
                Direction.UP,
                u,
                v,
                positions);
    }
}
