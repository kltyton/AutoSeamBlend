package com.kltyton.autoseamblend.texture.mapping;

import com.kltyton.autoseamblend.texture.geometry.TextureCorner;
import com.kltyton.autoseamblend.texture.geometry.TextureEdge;
import java.util.Objects;

/** 中文：四种 OptiFine 或 Continuity 轴向方法的纹理块选择。 / English: Tile selection for the four OptiFine/Continuity axis-oriented methods. */
public final class AxisTileMapper {
    private static final int[] SINGLE_AXIS = {3, 2, 0, 1};
    private static final int[] HORIZONTAL_VERTICAL_SECONDARY = {
            3, 3, 6, 3, 3, 3, 3, 3, 3, 3, 6, 3, 3, 3, 3, 3,
            4, 4, 5, 4, 4, 4, 4, 4, 3, 3, 6, 3, 3, 3, 3, 3,
            3, 3, 6, 3, 3, 3, 3, 3, 3, 3, 6, 3, 3, 3, 3, 3,
            3, 3, 6, 3, 3, 3, 3, 3, 3, 3, 6, 3, 3, 3, 3, 3
    };
    private static final int[] VERTICAL_HORIZONTAL_SECONDARY = {
            3, 6, 3, 3, 3, 6, 3, 3, 4, 5, 4, 4, 3, 6, 3, 3,
            3, 6, 3, 3, 3, 6, 3, 3, 3, 6, 3, 3, 3, 6, 3, 3,
            3, 3, 3, 3, 3, 3, 3, 3, 4, 4, 4, 4, 3, 3, 3, 3,
            3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3
    };

    private AxisTileMapper() {
    }

    public static int horizontal(NeighborConnections connections) {
        Objects.requireNonNull(connections, "connections");
        return SINGLE_AXIS[bit(connections, TextureEdge.LEFT, 0)
                | bit(connections, TextureEdge.RIGHT, 1)];
    }

    public static int vertical(NeighborConnections connections) {
        Objects.requireNonNull(connections, "connections");
        return SINGLE_AXIS[bit(connections, TextureEdge.DOWN, 0)
                | bit(connections, TextureEdge.UP, 1)];
    }

    public static int horizontalVertical(NeighborConnections connections) {
        Objects.requireNonNull(connections, "connections");
        int primary = bit(connections, TextureEdge.LEFT, 0)
                | bit(connections, TextureEdge.RIGHT, 1);
        if (primary != 0) {
            return SINGLE_AXIS[primary];
        }
        int secondary = bit(connections, TextureCorner.BOTTOM_LEFT, 0)
                | bit(connections, TextureEdge.DOWN, 1)
                | bit(connections, TextureCorner.BOTTOM_RIGHT, 2)
                | bit(connections, TextureCorner.TOP_RIGHT, 3)
                | bit(connections, TextureEdge.UP, 4)
                | bit(connections, TextureCorner.TOP_LEFT, 5);
        return HORIZONTAL_VERTICAL_SECONDARY[secondary];
    }

    public static int verticalHorizontal(NeighborConnections connections) {
        Objects.requireNonNull(connections, "connections");
        int primary = bit(connections, TextureEdge.DOWN, 0)
                | bit(connections, TextureEdge.UP, 1);
        if (primary != 0) {
            return SINGLE_AXIS[primary];
        }
        int secondary = bit(connections, TextureEdge.LEFT, 0)
                | bit(connections, TextureCorner.TOP_LEFT, 1)
                | bit(connections, TextureCorner.TOP_RIGHT, 2)
                | bit(connections, TextureEdge.RIGHT, 3)
                | bit(connections, TextureCorner.BOTTOM_RIGHT, 4)
                | bit(connections, TextureCorner.BOTTOM_LEFT, 5);
        return VERTICAL_HORIZONTAL_SECONDARY[secondary];
    }

    private static int bit(NeighborConnections connections, TextureEdge edge, int targetBit) {
        return connections.connected(edge) ? 1 << targetBit : 0;
    }

    private static int bit(NeighborConnections connections, TextureCorner corner, int targetBit) {
        return connections.connected(corner) ? 1 << targetBit : 0;
    }
}
