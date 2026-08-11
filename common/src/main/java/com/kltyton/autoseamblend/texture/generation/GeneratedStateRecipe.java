package com.kltyton.autoseamblend.texture.generation;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.geometry.TextureEdge;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import java.util.Objects;

/**
 * 中文：把一个已由原生引擎定向的完整邻接状态转换为与 Continuity 已验收效果相同的像素配方。
 *
 * English:
 * Converts one native-engine-oriented full neighbor state into the pixel recipe used by the
 * user-accepted Continuity result.
 */
public final class GeneratedStateRecipe {
    private GeneratedStateRecipe() {}

    public static GeneratedTileRecipe forConnections(
            ConnectionMethod method,
            NeighborConnections connections) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(connections, "connections");
        return switch (method) {
            case CTM -> new GeneratedTileRecipe.BorderConnections(
                    connections);
            case CTM_COMPACT -> new GeneratedTileRecipe.CompactConnections(
                    connections);
            case HORIZONTAL, VERTICAL,
                    HORIZONTAL_VERTICAL, VERTICAL_HORIZONTAL ->
                    new GeneratedTileRecipe.BorderConnections(
                            connectionsForMethod(method, connections));
            case RUNTIME_BLEND, OVERLAY, OVERLAY_CTM ->
                    new GeneratedTileRecipe.BlendConnections(
                            connections);
            case TOP, FIXED, NONE -> GeneratedTileRecipe.Source.INSTANCE;
            case AUTO -> throw new IllegalArgumentException(
                    "auto must be resolved before generating a state recipe");
        };
    }

    public static NeighborConnections connectionsForMethod(
            ConnectionMethod method,
            NeighborConnections connections) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(connections, "connections");
        int bits = connections.bits();
        if (method == ConnectionMethod.HORIZONTAL) {
            bits &= bit(TextureEdge.LEFT)
                    | bit(TextureEdge.RIGHT);
        } else if (method == ConnectionMethod.VERTICAL) {
            bits &= bit(TextureEdge.UP)
                    | bit(TextureEdge.DOWN);
        } else if (method == ConnectionMethod.HORIZONTAL_VERTICAL) {
            int horizontal = bits & (bit(TextureEdge.LEFT)
                    | bit(TextureEdge.RIGHT));
            bits = horizontal != 0
                    ? horizontal
                    : bits & (bit(TextureEdge.UP)
                            | bit(TextureEdge.DOWN));
        } else if (method == ConnectionMethod.VERTICAL_HORIZONTAL) {
            int vertical = bits & (bit(TextureEdge.UP)
                    | bit(TextureEdge.DOWN));
            bits = vertical != 0
                    ? vertical
                    : bits & (bit(TextureEdge.LEFT)
                            | bit(TextureEdge.RIGHT));
        }
        return NeighborConnections.fromBits(bits);
    }

    private static int bit(TextureEdge edge) {
        return 1 << edge.connectionBit();
    }
}
