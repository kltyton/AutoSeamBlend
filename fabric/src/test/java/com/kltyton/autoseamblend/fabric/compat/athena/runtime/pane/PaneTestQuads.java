package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

/**
 * 中文：测试共享的 26.1.2 BakedQuad 构造器；位置与 UV 可自由指定，供顶臂 UV 修正与
 * pane 表面角色选择契约使用。
 *
 * <p>English: Shared 26.1.2 BakedQuad builder for tests; positions and UVs are freely
 * configurable for the top-arm UV and pane surface-role contracts.
 */
final class PaneTestQuads {
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
        Vector3f[] vertex = new Vector3f[4];
        for (int index = 0; index < 4; index++) {
            vertex[index] = new Vector3f(
                    positions[index][0],
                    positions[index][1],
                    positions[index][2]);
        }
        long packed = UVPair.pack(u, v);
        return new BakedQuad(
                vertex[0],
                vertex[1],
                vertex[2],
                vertex[3],
                packed,
                packed,
                packed,
                packed,
                direction,
                new BakedQuad.MaterialInfo(
                        sprite,
                        ChunkSectionLayer.SOLID,
                        Sheets.cutoutBlockItemSheet(),
                        -1,
                        false,
                        0));
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
