package com.kltyton.autoseamblend.texture.geometry;

/** 中文：一个 UV 分量的世界空间梯度。 / English: A world-space gradient of one UV component. */
public record UvGradient(double x, double y, double z) {
    private static final double AXIS_EPSILON = 1.0e-6;

    public WorldDirection increasingDirection() {
        double ax = Math.abs(x);
        double ay = Math.abs(y);
        double az = Math.abs(z);
        double dominant = Math.max(ax, Math.max(ay, az));
        if (dominant <= AXIS_EPSILON) {
            throw new IllegalArgumentException("Degenerate UV gradient: " + this);
        }

        int significant = (ax > AXIS_EPSILON ? 1 : 0)
                + (ay > AXIS_EPSILON ? 1 : 0)
                + (az > AXIS_EPSILON ? 1 : 0);
        if (significant != 1) {
            throw new IllegalArgumentException("UV gradient is not axis-aligned: " + this);
        }
        if (ax > AXIS_EPSILON) {
            return x > 0 ? WorldDirection.EAST : WorldDirection.WEST;
        }
        if (ay > AXIS_EPSILON) {
            return y > 0 ? WorldDirection.UP : WorldDirection.DOWN;
        }
        return z > 0 ? WorldDirection.SOUTH : WorldDirection.NORTH;
    }
}
