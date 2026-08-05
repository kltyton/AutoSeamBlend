package com.kltyton.autoseamblend.neoforge.runtime.render;

import java.util.Objects;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.quad.MutableQuad;

/** 中文：原生动态适配器共享的不可变 Quad 重贴图。 / English: Shared immutable-quad retexturing used by native dynamic adapters. */
public final class NeoForgeQuadRetexturing {
    private static final float UV_EPSILON = 1.0e-6F;

    private NeoForgeQuadRetexturing() {}

    public static BakedQuad replace(
            BakedQuad source,
            TextureAtlasSprite target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        TextureAtlasSprite original =
                source.materialInfo().sprite();
        MutableQuad output =
                new MutableQuad().setFrom(source);
        float sourceWidth =
                original.getU1() - original.getU0();
        float sourceHeight =
                original.getV1() - original.getV0();
        // 中文：Continuity 替换精灵时保留原 Quad 的方块层与物品渲染类型，透明玻璃板不能按生成精灵重新分层。
        // English: Match Continuity by retaining the source quad's block and item render layers when replacing its sprite.
        output.setSprite(
                target,
                output.requiredChunkLayer(),
                output.requiredItemRenderType());
        if (Math.abs(sourceWidth) <= UV_EPSILON
                || Math.abs(sourceHeight) <= UV_EPSILON) {
            return output.toBakedQuad();
        }
        for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
            float u =
                    (output.u(vertex) - original.getU0())
                            / sourceWidth;
            float v =
                    (output.v(vertex) - original.getV0())
                            / sourceHeight;
            output.setUv(
                    vertex,
                    target.getU(u),
                    target.getV(v));
        }
        return output.toBakedQuad();
    }

    public static BakedQuad overlay(
            Direction face,
            TextureAtlasSprite target,
            int tintColor) {
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(target, "target");
        // 中文：Continuity 的 overlay 始终是标准整面四边形，不继承接收面的局部几何或旋转 UV。
        // English: Continuity emits overlays as canonical full-face quads instead of cloning receiver geometry or rotated UVs.
        MutableQuad output = new MutableQuad()
                .setCubeFaceFromSpriteCoords(
                        face,
                        0.0F,
                        0.0F,
                        1.0F,
                        1.0F,
                        0.0F)
                .setColor(tintColor)
                .setUv(0, target.getU0(), target.getV0())
                .setUv(1, target.getU0(), target.getV1())
                .setUv(2, target.getU1(), target.getV1())
                .setUv(3, target.getU1(), target.getV0())
                .setSprite(
                        target,
                        ChunkSectionLayer.CUTOUT,
                        Sheets.cutoutBlockItemSheet())
                .setAmbientOcclusion(true);
        return output.toBakedQuad();
    }
}
