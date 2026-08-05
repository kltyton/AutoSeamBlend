package com.kltyton.autoseamblend.texture.generation;

import com.kltyton.autoseamblend.texture.geometry.TextureCorner;
import com.kltyton.autoseamblend.texture.geometry.TextureEdge;
import com.kltyton.autoseamblend.texture.image.ArgbImage;
import com.kltyton.autoseamblend.texture.mapping.Ctm47Mapper;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import com.kltyton.autoseamblend.texture.mask.ContinuityFrameCtmMasks;
import java.util.Objects;

/**
 * 中文：通过用相邻内侧像素替换已连接的框架边缘来创建 CTM 变体；缺失对角连接时只保留实际内角框架，不缩放整个象限。
 *
 * English:
 * Creates CTM variants by replacing connected frame edges with their adjacent interior pixels.
 *
 * <p>No quadrant is rescaled. A missing diagonal preserves only the actual corner-frame pixels so
 * inside corners remain visible without restoring two half-length borders.
 */
public final class Ctm47TileGenerator {
    private Ctm47TileGenerator() {}

    public static ArgbImage generate(ArgbImage source, int tileIndex, int borderWidth) {
        return generate(source, Ctm47Mapper.connectionsForTile(tileIndex), borderWidth);
    }

    public static ArgbImage generate(
            ArgbImage source, NeighborConnections connections, int borderWidth) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(connections, "connections");
        var framed = ContinuityFrameCtmMasks.apply(
                source,
                Ctm47Mapper.tileIndex(connections));
        if (framed.isPresent()) {
            return framed.orElseThrow();
        }
        if (borderWidth < 1 || borderWidth * 2 >= Math.min(source.width(), source.height())) {
            throw new IllegalArgumentException("Invalid CTM border width " + borderWidth);
        }
        int effectiveBorderWidth = fullyCoveredTranslucent(source)
                ? 1
                : borderWidth;
        int[] output = new int[Math.multiplyExact(
                source.width(),
                source.height())];
        int splitX = (source.width() + 1) / 2;
        int splitY = (source.height() + 1) / 2;
        for (int y = 0; y < source.height(); y++) {
            for (int x = 0; x < source.width(); x++) {
                boolean leftHalf = x < splitX;
                boolean topHalf = y < splitY;
                TextureEdge horizontal = leftHalf
                        ? TextureEdge.LEFT
                        : TextureEdge.RIGHT;
                TextureEdge vertical = topHalf
                        ? TextureEdge.UP
                        : TextureEdge.DOWN;
                boolean trimHorizontal =
                        connections.connected(horizontal);
                boolean trimVertical =
                        connections.connected(vertical);
                TextureCorner corner =
                        TextureCorner.between(
                                horizontal,
                                vertical);
                boolean preserveInsideCorner = trimHorizontal
                        && trimVertical
                        && !connections.connected(corner);
                int sampleX = removeEdge(
                        x,
                        source.width(),
                        leftHalf,
                        trimHorizontal
                                ? effectiveBorderWidth
                                : 0);
                int sampleY = removeEdge(
                        y,
                        source.height(),
                        topHalf,
                        trimVertical
                                ? effectiveBorderWidth
                                : 0);
                if (preserveInsideCorner
                        && inRemovedEdge(
                                x,
                                source.width(),
                                leftHalf,
                                effectiveBorderWidth)
                        && inRemovedEdge(
                                y,
                                source.height(),
                                topHalf,
                                effectiveBorderWidth)) {
                    sampleX = x;
                    sampleY = y;
                }
                output[y * source.width() + x] =
                        source.pixelAt(
                                sampleX,
                                sampleY);
            }
        }
        return ArgbImage.wrapGenerated(source.width(), source.height(), output);
    }

    /**
     * 中文：全覆盖半透明纹理没有透明内区可用于推断宽框；保守地只移除一像素接缝，避免把染色玻璃的斜线细节裁成方点。
     *
     * English:
     * Fully covered translucent textures have no transparent interior from which to infer a wide
     * frame. Removing only one seam pixel preserves stained-glass diagonal details instead of
     * cropping them into square artifacts.
     */
    private static boolean fullyCoveredTranslucent(ArgbImage source) {
        boolean translucent = false;
        for (int y = 0; y < source.height(); y++) {
            for (int x = 0; x < source.width(); x++) {
                int alpha = source.pixelAt(x, y) >>> 24;
                if (alpha == 0) {
                    return false;
                }
                translucent |= alpha < 255;
            }
        }
        return translucent;
    }

    /** 中文：只把实际框架带钳制到相邻内侧像素，保持其余纹素坐标不变。 / English: Clamps only the actual frame band to its adjacent interior pixel and leaves every other texel coordinate unchanged. */
    private static int removeEdge(
            int coordinate,
            int size,
            boolean firstHalf,
            int inset) {
        if (inset == 0) {
            return coordinate;
        }
        return firstHalf
                ? Math.max(coordinate, inset)
                : Math.min(coordinate, size - inset - 1);
    }

    /** 中文：判断纹素是否位于当前象限将被移除的外框带。 / English: Returns whether a texel lies in the outer frame band removed for this half. */
    private static boolean inRemovedEdge(
            int coordinate,
            int size,
            boolean firstHalf,
            int inset) {
        return firstHalf
                ? coordinate < inset
                : coordinate >= size - inset;
    }
}
