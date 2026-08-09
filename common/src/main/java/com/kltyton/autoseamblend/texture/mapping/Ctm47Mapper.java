package com.kltyton.autoseamblend.texture.mapping;

import java.util.Objects;

/** 中文：把八邻域连接蒙版映射到 OptiFine 或 Continuity 的 47 纹理块索引。 / English: Maps an eight-neighbor connection mask to the OptiFine/Continuity 47-tile index. */
public final class Ctm47Mapper {
    public static final int TILE_COUNT = 47;

    // 中文：位布局为 TL U TR / L * R / BL D BR，正交方向位位于 0、2、4、6。 / English: Bit layout: TL U TR / L * R / BL D BR, with cardinal bits at 0,2,4,6.
    private static final int[] INDEX_BY_BITS = {
            0, 3, 0, 3, 12, 5, 12, 15, 0, 3, 0, 3, 12, 5, 12, 15,
            1, 2, 1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14,
            0, 3, 0, 3, 12, 5, 12, 15, 0, 3, 0, 3, 12, 5, 12, 15,
            1, 2, 1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14,
            36, 17, 36, 17, 24, 19, 24, 43, 36, 17, 36, 17, 24, 19, 24, 43,
            16, 18, 16, 18, 6, 46, 6, 21, 16, 18, 16, 18, 28, 9, 28, 22,
            36, 17, 36, 17, 24, 19, 24, 43, 36, 17, 36, 17, 24, 19, 24, 43,
            37, 40, 37, 40, 30, 8, 30, 34, 37, 40, 37, 40, 25, 23, 25, 45,
            0, 3, 0, 3, 12, 5, 12, 15, 0, 3, 0, 3, 12, 5, 12, 15,
            1, 2, 1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14,
            0, 3, 0, 3, 12, 5, 12, 15, 0, 3, 0, 3, 12, 5, 12, 15,
            1, 2, 1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14,
            36, 39, 36, 39, 24, 41, 24, 27, 36, 39, 36, 39, 24, 41, 24, 27,
            16, 42, 16, 42, 6, 20, 6, 10, 16, 42, 16, 42, 28, 35, 28, 44,
            36, 39, 36, 39, 24, 41, 24, 27, 36, 39, 36, 39, 24, 41, 24, 27,
            37, 38, 37, 38, 30, 11, 30, 32, 37, 38, 37, 38, 25, 33, 25, 26
    };

    private Ctm47Mapper() {
    }

    public static int tileIndex(NeighborConnections connections) {
        Objects.requireNonNull(connections, "connections");
        return INDEX_BY_BITS[connections.normalizedCtmBits()];
    }

    public static NeighborConnections connectionsForTile(int tileIndex) {
        if (tileIndex < 0 || tileIndex >= TILE_COUNT) {
            throw new IllegalArgumentException("CTM tile must be in [0,46]");
        }
        for (int bits = 0; bits <= 0xFF; bits++) {
            NeighborConnections candidate = NeighborConnections.fromBits(bits);
            if (candidate.normalizedCtmBits() == bits && INDEX_BY_BITS[bits] == tileIndex) return candidate;
        }
        throw new IllegalStateException("No normalized CTM neighborhood for tile " + tileIndex);
    }
}
