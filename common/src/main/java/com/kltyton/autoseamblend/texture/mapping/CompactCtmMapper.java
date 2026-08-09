package com.kltyton.autoseamblend.texture.mapping;

import com.kltyton.autoseamblend.texture.geometry.TextureCorner;
import com.kltyton.autoseamblend.texture.geometry.TextureEdge;
import java.util.Objects;

/** 中文：为每个表面象限选择五个 compact CTM 源纹理块之一。 / English: Selects one of the five compact CTM source tiles for each face quadrant. */
public final class CompactCtmMapper {
    public static final int TILE_COUNT = 5;

    private CompactCtmMapper() {
    }

    public static int tileIndex(NeighborConnections connections, TextureCorner quadrant) {
        Objects.requireNonNull(connections, "connections");
        Objects.requireNonNull(quadrant, "quadrant");
        TextureEdge first = quadrant.firstEdge();
        TextureEdge second = quadrant.secondEdge();
        boolean firstConnected = connections.connected(first);
        boolean secondConnected = connections.connected(second);
        if (firstConnected && secondConnected) {
            return connections.connected(quadrant) ? 1 : 4;
        }
        if (!firstConnected && !secondConnected) {
            return 0;
        }

        // 中文：在标准 compact 布局中，纹理块 2 和 3 是镜像边缘象限。 / English: Tiles 2 and 3 are mirrored edge quadrants in the standard compact layout.
        return switch (quadrant) {
            case TOP_LEFT -> firstConnected ? 2 : 3;
            case BOTTOM_LEFT -> firstConnected ? 3 : 2;
            case BOTTOM_RIGHT -> firstConnected ? 2 : 3;
            case TOP_RIGHT -> firstConnected ? 3 : 2;
        };
    }

    public static CompactTiles tiles(NeighborConnections connections) {
        return new CompactTiles(
                tileIndex(connections, TextureCorner.TOP_LEFT),
                tileIndex(connections, TextureCorner.TOP_RIGHT),
                tileIndex(connections, TextureCorner.BOTTOM_RIGHT),
                tileIndex(connections, TextureCorner.BOTTOM_LEFT));
    }

    public record CompactTiles(int topLeft, int topRight, int bottomRight, int bottomLeft) {
        public CompactTiles {
            check(topLeft);
            check(topRight);
            check(bottomRight);
            check(bottomLeft);
        }

        private static void check(int index) {
            if (index < 0 || index >= TILE_COUNT) {
                throw new IllegalArgumentException("Compact tile index out of range: " + index);
            }
        }
    }
}
