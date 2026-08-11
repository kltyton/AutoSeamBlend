package com.kltyton.autoseamblend.forge.runtime.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.util.Objects;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/**
 * 中文：原生动态适配器共享的不可变 Quad 重贴图。
 *
 * English: Shared immutable-quad retexturing used by native dynamic adapters.
 */
public final class ForgeQuadRetexturing {
    private static final float UV_EPSILON = 1.0e-6F;
    private static final VertexFormat BLOCK_FORMAT =
            DefaultVertexFormat.BLOCK;
    private static final int STRIDE_INTS =
            BLOCK_FORMAT.getVertexSize() / 4;
    private static final int UV0_OFFSET_INTS =
            offsetInts(DefaultVertexFormat.ELEMENT_UV0);
    private static final int COLOR_OFFSET_INTS =
            offsetInts(DefaultVertexFormat.ELEMENT_COLOR);
    private static final int NORMAL_OFFSET_INTS =
            offsetInts(DefaultVertexFormat.ELEMENT_NORMAL);

    private static int offsetInts(VertexFormatElement element) {
        int offset = 0;
        for (VertexFormatElement candidate : BLOCK_FORMAT.getElements()) {
            if (candidate == element) {
                return offset / 4;
            }
            offset += candidate.getByteSize();
        }
        throw new IllegalStateException(
                "DefaultVertexFormat.BLOCK has no element " + element);
    }

    private ForgeQuadRetexturing() {}

    public static BakedQuad replace(
            BakedQuad source,
            TextureAtlasSprite target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        TextureAtlasSprite original =
                source.getSprite();
        float sourceWidth =
                original.getU1() - original.getU0();
        float sourceHeight =
                original.getV1() - original.getV0();
        int[] vertices = source.getVertices()
                .clone();
        if (Math.abs(sourceWidth) > UV_EPSILON
                && Math.abs(sourceHeight)
                        > UV_EPSILON) {
            for (int vertex = 0;
                    vertex < 4;
                    vertex++) {
                int base =
                        vertex * STRIDE_INTS
                                + UV0_OFFSET_INTS;
                float u = Float.intBitsToFloat(
                        vertices[base]);
                float v = Float.intBitsToFloat(
                        vertices[base + 1]);
                vertices[base] =
                        Float.floatToRawIntBits(
                                target.getU(
                                        (u - original.getU0())
                                                / sourceWidth
                                                * 16.0F));
                vertices[base + 1] =
                        Float.floatToRawIntBits(
                                target.getV(
                                        (v - original.getV0())
                                                / sourceHeight
                                                * 16.0F));
            }
        }
        return new BakedQuad(
                vertices,
                source.getTintIndex(),
                source.getDirection(),
                target,
                source.isShade());
    }

    public static BakedQuad overlay(
            Direction face,
            TextureAtlasSprite target,
            int tintColor) {
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(target, "target");
        int[] vertices = new int[4 * STRIDE_INTS];
        float[][] positions = positions(face);
        int packedColor = packedColor(tintColor);
        float[] uvs = {
            target.getU0(), target.getV0(),
            target.getU0(), target.getV1(),
            target.getU1(), target.getV1(),
            target.getU1(), target.getV0()
        };
        for (int vertex = 0;
                vertex < 4;
                vertex++) {
            int base = vertex * STRIDE_INTS;
            float[] position = positions[vertex];
            vertices[base] =
                    Float.floatToRawIntBits(
                            position[0]);
            vertices[base + 1] =
                    Float.floatToRawIntBits(
                            position[1]);
            vertices[base + 2] =
                    Float.floatToRawIntBits(
                            position[2]);
            vertices[base + COLOR_OFFSET_INTS] =
                    packedColor;
            vertices[base + UV0_OFFSET_INTS] =
                    Float.floatToRawIntBits(
                            uvs[vertex * 2]);
            vertices[base + UV0_OFFSET_INTS + 1] =
                    Float.floatToRawIntBits(
                            uvs[vertex * 2 + 1]);
            // 中文：写入 DefaultVertexFormat.BLOCK NORMAL 的有符号归一化字节：单位分量编码为
            // face step * 127，零轴保持 0，第四 padding 字节保持 0；不改颜色/UV/位置/UV2。
            // English: Writes the BLOCK NORMAL signed normalized bytes: the unit component
            // encodes face step * 127, zero axes stay 0, and the fourth padding byte stays 0;
            // color, UV, position, and UV2 are unchanged.
            vertices[base + NORMAL_OFFSET_INTS] =
                    (face.getStepX() * 127) & 0xFF
                            | ((face.getStepY() * 127)
                                            & 0xFF)
                                    << 8
                            | ((face.getStepZ() * 127)
                                            & 0xFF)
                                    << 16;
        }
        return new BakedQuad(
                vertices,
                -1,
                face,
                target,
                true);
    }

    /**
     * 中文：原版整面四边形的顶点坐标（未变换的 1x1 面）。侧面顶点 y 序为 1,0,0,1，
     * 与 26.1.2 MutableQuad.setCubeFaceFromSpriteCoords 的顶点序一致：顶点 0/3 在
     * 面上沿（y=1）绑定纹理 v0，顶点 1/2 在面下沿（y=0）绑定 v1，保证 overlay 纹理
     * 不上下颠倒。UP/DOWN 面 y 恒定，保持不变。
     *
     * English: Canonical vanilla full-face quad vertex positions (untransformed
     * 1x1 face). Side-face vertex y order is 1,0,0,1, matching 26.1.2
     * MutableQuad.setCubeFaceFromSpriteCoords: vertices 0/3 sit on the face top
     * (y=1) bound to texture v0 and vertices 1/2 sit on the face bottom (y=0) bound
     * to v1, so overlay textures are not vertically flipped. UP/DOWN faces keep a
     * constant y and stay unchanged.
     */
    private static float[][] positions(
            Direction face) {
        return switch (face) {
            case DOWN -> new float[][] {
                {0.0F, 0.0F, 0.0F},
                {0.0F, 0.0F, 1.0F},
                {1.0F, 0.0F, 1.0F},
                {1.0F, 0.0F, 0.0F}
            };
            case UP -> new float[][] {
                {0.0F, 1.0F, 0.0F},
                {0.0F, 1.0F, 1.0F},
                {1.0F, 1.0F, 1.0F},
                {1.0F, 1.0F, 0.0F}
            };
            case NORTH -> new float[][] {
                {1.0F, 1.0F, 0.0F},
                {1.0F, 0.0F, 0.0F},
                {0.0F, 0.0F, 0.0F},
                {0.0F, 1.0F, 0.0F}
            };
            case SOUTH -> new float[][] {
                {0.0F, 1.0F, 1.0F},
                {0.0F, 0.0F, 1.0F},
                {1.0F, 0.0F, 1.0F},
                {1.0F, 1.0F, 1.0F}
            };
            case WEST -> new float[][] {
                {0.0F, 1.0F, 0.0F},
                {0.0F, 0.0F, 0.0F},
                {0.0F, 0.0F, 1.0F},
                {0.0F, 1.0F, 1.0F}
            };
            case EAST -> new float[][] {
                {1.0F, 1.0F, 0.0F},
                {1.0F, 0.0F, 0.0F},
                {1.0F, 0.0F, 1.0F},
                {1.0F, 1.0F, 1.0F}
            };
        };
    }

    /**
     * 中文：把 ARGB 颜色打包成 BLOCK 顶点格式的 0xAABBGGRR。
     *
     * English: Packs an ARGB color into the BLOCK vertex format's
     * 0xAABBGGRR little-endian byte layout.
     */
    private static int packedColor(int argb) {
        int alpha = (argb >> 24) & 0xFF;
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        return (alpha << 24)
                | (blue << 16)
                | (green << 8)
                | red;
    }
}
