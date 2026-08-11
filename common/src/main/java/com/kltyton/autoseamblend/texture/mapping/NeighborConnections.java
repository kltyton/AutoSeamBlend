package com.kltyton.autoseamblend.texture.mapping;

import com.kltyton.autoseamblend.texture.geometry.TextureCorner;
import com.kltyton.autoseamblend.texture.geometry.TextureEdge;
import java.util.EnumSet;
import java.util.Objects;

/**
 * 中文：Quad 周围在纹理空间中的不可变八邻域观察；对角状态独立保留，因为优先轴方法即使在另一正交轴断开时仍会使用它们。
 *
 * English:
 * Immutable eight-neighbor observations around a quad in texture space.
 * Diagonals are retained independently because prioritized axis methods use
 * them even when the other cardinal axis is disconnected.
 */
public final class NeighborConnections {
    private static final int VALID_BITS = 0xFF;

    private final int bits;

    private NeighborConnections(int bits) {
        this.bits = bits & VALID_BITS;
    }

    public static NeighborConnections none() {
        return new NeighborConnections(0);
    }

    public static NeighborConnections fromBits(int bits) {
        if ((bits & ~VALID_BITS) != 0) {
            throw new IllegalArgumentException("Connection bits must fit in eight bits: " + bits);
        }
        return new NeighborConnections(bits);
    }

    public static NeighborConnections of(EnumSet<TextureEdge> edges, EnumSet<TextureCorner> corners) {
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(corners, "corners");
        int bits = 0;
        for (TextureEdge edge : edges) {
            bits |= 1 << edge.connectionBit();
        }
        for (TextureCorner corner : corners) {
            bits |= 1 << corner.connectionBit();
        }
        return new NeighborConnections(bits);
    }

    public int bits() {
        return bits;
    }

    public boolean connected(TextureEdge edge) {
        Objects.requireNonNull(edge, "edge");
        return (bits & (1 << edge.connectionBit())) != 0;
    }

    public boolean connected(TextureCorner corner) {
        Objects.requireNonNull(corner, "corner");
        return (bits & (1 << corner.connectionBit())) != 0;
    }

    /** 中文：移除不会影响标准 47 状态 CTM 纹理块的对角连接。 / English: Removes diagonals that cannot affect a standard 47-state CTM tile. */
    public int normalizedCtmBits() {
        int normalized = bits;
        for (TextureCorner corner : TextureCorner.values()) {
            if (!connected(corner.firstEdge()) || !connected(corner.secondEdge())) {
                normalized &= ~(1 << corner.connectionBit());
            }
        }
        return normalized;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NeighborConnections connections && bits == connections.bits;
    }

    @Override
    public int hashCode() {
        return bits;
    }

    @Override
    public String toString() {
        return "NeighborConnections[bits=0x" + Integer.toHexString(bits) + ']';
    }
}
