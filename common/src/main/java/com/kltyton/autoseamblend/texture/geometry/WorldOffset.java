package com.kltyton.autoseamblend.texture.geometry;

/** 中文：整数世界空间偏移；对角偏移可有两个非零分量。 / English: Integer world-space offset; diagonals may have two non-zero components. */
public record WorldOffset(int x, int y, int z) {
    public WorldOffset add(WorldOffset other) {
        return new WorldOffset(x + other.x, y + other.y, z + other.z);
    }

    public WorldOffset negate() {
        return new WorldOffset(-x, -y, -z);
    }

    public int dot(WorldOffset other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public WorldOffset cross(WorldOffset other) {
        return new WorldOffset(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x);
    }
}
