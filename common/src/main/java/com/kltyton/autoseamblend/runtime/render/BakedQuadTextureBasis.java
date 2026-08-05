package com.kltyton.autoseamblend.runtime.render;

import com.kltyton.autoseamblend.texture.geometry.TextureBasis;
import com.kltyton.autoseamblend.texture.geometry.TextureBasisQuadView;
import com.kltyton.autoseamblend.texture.geometry.TextureBasisResolver;
import com.kltyton.autoseamblend.texture.geometry.WorldDirection;
import java.util.Objects;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;

/**
 * 中文：从实际烘焙 Quad 的位置与 UV 计算纹理空间方向；退化或非轴对齐 UV 回退到方块面规范方向。
 *
 * English:
 * Derives texture-space directions from an actual baked quad's positions and UVs.
 * Degenerate or non-axis-aligned UVs fall back to the canonical face basis.
 */
public final class BakedQuadTextureBasis {
    private BakedQuadTextureBasis() {}

    public static TextureBasis resolve(BakedQuad quad) {
        Objects.requireNonNull(quad, "quad");
        WorldDirection face = WorldDirection.valueOf(quad.direction().name());
        return TextureBasisResolver.resolve(new BakedQuadView(quad, face));
    }

    private record BakedQuadView(BakedQuad quad, WorldDirection face)
            implements TextureBasisQuadView {
        @Override
        public float u(int vertex) {
            return UVPair.unpackU(quad.packedUV(vertex));
        }

        @Override
        public float v(int vertex) {
            return UVPair.unpackV(quad.packedUV(vertex));
        }

        @Override
        public float position(int vertex, int component) {
            return quad.position(vertex).get(component);
        }
    }
}
