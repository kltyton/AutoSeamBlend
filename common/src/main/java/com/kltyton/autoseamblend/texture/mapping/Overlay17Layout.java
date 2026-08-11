package com.kltyton.autoseamblend.texture.mapping;

import com.kltyton.autoseamblend.texture.geometry.*;
import com.kltyton.autoseamblend.texture.image.*;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** 中文：Overlays 参考资源包所采用的 OptiFine 或 Continuity 标准 overlay 纹理块顺序。 / English: OptiFine/Continuity standard overlay tile order, as used by the Overlays reference pack. */
public final class Overlay17Layout {
    public static final int TILE_COUNT = 17;

    private Overlay17Layout() {}

    public static AlphaMask mask(
            int tile,
            int width,
            int height,
            OverlayCutoutProfile profile) {
        if (tile < 0 || tile >= TILE_COUNT) throw new IllegalArgumentException("Overlay tile must be in [0,16]");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Overlay mask dimensions must be positive");
        }
        Objects.requireNonNull(profile, "profile");
        byte[] alpha = new byte[Math.multiplyExact(width, height)];
        for (int y = 0; y < height; y++) {
            int maskY = y * 16 / height;
            int row = profile.rowBits(
                    1 << tile,
                    maskY);
            for (int x = 0; x < width; x++) {
                int maskX = x * 16 / width;
                if ((row & 1 << maskX) != 0) {
                    alpha[y * width + x] = (byte) 0xFF;
                }
            }
        }
        return AlphaMask.wrapGenerated(width, height, alpha);
    }

    public static ArgbImage cutout(
            ArgbImage source,
            int tile) {
        Objects.requireNonNull(source, "source");
        OverlayCutoutProfile profile =
                OverlayCutoutProfile.fromArgb(
                        source.width(),
                        source.height(),
                        source.copyPixels());
        AlphaMask mask = mask(
                tile,
                source.width(),
                source.height(),
                profile);
        int[] pixels = source.copyPixels();
        for (int index = 0; index < pixels.length; index++) {
            pixels[index] = PremultipliedArgb.applyCoverage(pixels[index], mask.alphaAtIndex(index));
        }
        return ArgbImage.wrapGenerated(source.width(), source.height(), pixels);
    }

    /**
     * 中文：解析一个已定向且已经过引擎谓词门控的邻接状态所需的逻辑 overlay 槽位；
     * 这里刻意不做标准 CTM 对角归一化，因为 Continuity/Fusion overlay 可以在各自原生条件满足时保留视觉上的孤立角。
     *
     * <p>English: Resolves the logical overlay slots required by an already-oriented neighbor state
     * whose bits have already passed the engine predicates. Standard-CTM diagonal normalization is
     * deliberately absent because Continuity/Fusion overlays may retain a visually isolated corner
     * when their own native conditions allow it.
     */
    public static List<Integer> selectedSlots(NeighborConnections connections) {
        Objects.requireNonNull(connections, "connections");
        LinkedHashSet<Integer> slots = new LinkedHashSet<>();
        boolean left = connections.connected(TextureEdge.LEFT);
        boolean down = connections.connected(TextureEdge.DOWN);
        boolean right = connections.connected(TextureEdge.RIGHT);
        boolean up = connections.connected(TextureEdge.UP);
        int edgeMask = (left ? 1 : 0)
                | (down ? 2 : 0)
                | (right ? 4 : 0)
                | (up ? 8 : 0);
        switch (edgeMask) {
            case 0 -> {
                addCornerIfConnected(slots, connections, TextureCorner.BOTTOM_RIGHT, 0);
                addCornerIfConnected(slots, connections, TextureCorner.BOTTOM_LEFT, 2);
                addCornerIfConnected(slots, connections, TextureCorner.TOP_RIGHT, 14);
                addCornerIfConnected(slots, connections, TextureCorner.TOP_LEFT, 16);
            }
            case 1 -> {
                slots.add(9);
                addCornerIfConnected(slots, connections, TextureCorner.BOTTOM_RIGHT, 0);
                addCornerIfConnected(slots, connections, TextureCorner.TOP_RIGHT, 14);
            }
            case 2 -> {
                slots.add(1);
                addCornerIfConnected(slots, connections, TextureCorner.TOP_RIGHT, 14);
                addCornerIfConnected(slots, connections, TextureCorner.TOP_LEFT, 16);
            }
            case 3 -> {
                slots.add(4);
                addCornerIfConnected(slots, connections, TextureCorner.TOP_RIGHT, 14);
            }
            case 4 -> {
                slots.add(7);
                addCornerIfConnected(slots, connections, TextureCorner.TOP_LEFT, 16);
                addCornerIfConnected(slots, connections, TextureCorner.BOTTOM_LEFT, 2);
            }
            case 5 -> {
                slots.add(9);
                slots.add(7);
            }
            case 6 -> {
                slots.add(3);
                addCornerIfConnected(slots, connections, TextureCorner.TOP_LEFT, 16);
            }
            case 7 -> slots.add(5);
            case 8 -> {
                slots.add(15);
                addCornerIfConnected(slots, connections, TextureCorner.BOTTOM_LEFT, 2);
                addCornerIfConnected(slots, connections, TextureCorner.BOTTOM_RIGHT, 0);
            }
            case 9 -> {
                slots.add(11);
                addCornerIfConnected(slots, connections, TextureCorner.BOTTOM_RIGHT, 0);
            }
            case 10 -> {
                slots.add(1);
                slots.add(15);
            }
            case 11 -> slots.add(6);
            case 12 -> {
                slots.add(10);
                addCornerIfConnected(slots, connections, TextureCorner.BOTTOM_LEFT, 2);
            }
            case 13 -> slots.add(13);
            case 14 -> slots.add(12);
            case 15 -> slots.add(8);
            default -> throw new IllegalStateException("unreachable overlay edge mask");
        }
        return List.copyOf(slots);
    }

    private static void addCornerIfConnected(
            LinkedHashSet<Integer> slots,
            NeighborConnections connections,
            TextureCorner corner,
            int slot) {
        if (connections.connected(corner)) slots.add(slot);
    }

    /** 中文：返回此标准 overlay 状态是否到达请求的边缘。 / English: Returns whether this standard overlay state reaches the requested edge. */
    public static boolean usesEdge(int tile, TextureEdge edge) {
        if (tile < 0 || tile >= TILE_COUNT) throw new IllegalArgumentException("Overlay tile must be in [0,16]");
        return switch (edge) {
            case LEFT -> tile == 2 || tile == 4 || tile == 5 || tile == 6 || tile == 8
                    || tile == 9 || tile == 11 || tile == 13 || tile == 16;
            case RIGHT -> tile == 0 || tile == 3 || tile == 5 || tile == 7 || tile == 8
                    || tile == 10 || tile == 12 || tile == 13 || tile == 14;
            case UP -> tile == 6 || tile == 8 || tile == 10 || tile == 11 || tile == 12
                    || tile == 13 || tile == 14 || tile == 15 || tile == 16;
            case DOWN -> tile == 0 || tile == 1 || tile == 2 || tile == 3 || tile == 4
                    || tile == 5 || tile == 6 || tile == 8 || tile == 12;
        };
    }

}
