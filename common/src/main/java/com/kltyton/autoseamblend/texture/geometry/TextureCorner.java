package com.kltyton.autoseamblend.texture.geometry;

/** 中文：精灵 UV 空间中的一个对角方向。 / English: A diagonal direction in sprite UV space. */
public enum TextureCorner {
    BOTTOM_LEFT(1, TextureEdge.LEFT, TextureEdge.DOWN),
    BOTTOM_RIGHT(3, TextureEdge.DOWN, TextureEdge.RIGHT),
    TOP_RIGHT(5, TextureEdge.RIGHT, TextureEdge.UP),
    TOP_LEFT(7, TextureEdge.UP, TextureEdge.LEFT);

    private final int connectionBit;
    private final TextureEdge firstEdge;
    private final TextureEdge secondEdge;

    TextureCorner(int connectionBit, TextureEdge firstEdge, TextureEdge secondEdge) {
        this.connectionBit = connectionBit;
        this.firstEdge = firstEdge;
        this.secondEdge = secondEdge;
    }

    public int connectionBit() {
        return connectionBit;
    }

    public TextureEdge firstEdge() {
        return firstEdge;
    }

    public TextureEdge secondEdge() {
        return secondEdge;
    }

    public boolean touches(TextureEdge edge) {
        return firstEdge == edge || secondEdge == edge;
    }

    public static TextureCorner between(TextureEdge first, TextureEdge second) {
        for (TextureCorner corner : values()) {
            if (corner.touches(first) && corner.touches(second)) {
                return corner;
            }
        }
        throw new IllegalArgumentException("Edges do not share a corner: " + first + ", " + second);
    }
}
