package com.kltyton.autoseamblend.texture.geometry;

/** 中文：精灵 UV 空间中的一个正交方向，其中 V 向下增长。 / English: A cardinal direction in sprite UV space, where V grows downward. */
public enum TextureEdge {
    LEFT(0, -1, 0),
    DOWN(2, 0, 1),
    RIGHT(4, 1, 0),
    UP(6, 0, -1);

    private final int connectionBit;
    private final int uOffset;
    private final int vOffset;

    TextureEdge(int connectionBit, int uOffset, int vOffset) {
        this.connectionBit = connectionBit;
        this.uOffset = uOffset;
        this.vOffset = vOffset;
    }

    public int connectionBit() {
        return connectionBit;
    }

    public int uOffset() {
        return uOffset;
    }

    public int vOffset() {
        return vOffset;
    }

    public TextureEdge opposite() {
        return switch (this) {
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
            case UP -> DOWN;
            case DOWN -> UP;
        };
    }
}
