package com.kltyton.autoseamblend.texture.geometry;

/** 中文：不依赖 Minecraft 类的六个轴对齐世界方向。 / English: The six axis-aligned world directions without a Minecraft class dependency. */
public enum WorldDirection {
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0);

    private final WorldOffset offset;

    WorldDirection(int x, int y, int z) {
        offset = new WorldOffset(x, y, z);
    }

    public WorldOffset offset() {
        return offset;
    }

    public WorldDirection opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    public static WorldDirection fromUnitOffset(WorldOffset offset) {
        for (WorldDirection direction : values()) {
            if (direction.offset.equals(offset)) {
                return direction;
            }
        }
        throw new IllegalArgumentException("Not an axis-aligned unit offset: " + offset);
    }
}
