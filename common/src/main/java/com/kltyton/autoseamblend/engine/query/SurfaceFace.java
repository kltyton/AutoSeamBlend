package com.kltyton.autoseamblend.engine.query;

public enum SurfaceFace {
    DOWN,
    UP,
    NORTH,
    SOUTH,
    WEST,
    EAST,
    UNDEFINED;

    /**
     * 中文：返回同一表面的反向面；未定义面保持未定义。
     * English: Returns the opposite face of the same surface; undefined remains undefined.
     */
    public SurfaceFace opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
            case UNDEFINED -> UNDEFINED;
        };
    }
}
