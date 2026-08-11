package com.kltyton.autoseamblend.runtime.surface;

import com.kltyton.autoseamblend.engine.query.SurfaceFace;
import java.util.Objects;

/**
 * 中文：统一轴对齐、完整面和合法 UV 判定。
 *
 * English: Shared axis-alignment, full-face, and valid-UV predicates.
 */
public final class SurfaceQuadGeometry {
    private SurfaceQuadGeometry() {}

    public static boolean axisAligned(SurfaceQuadView quad, float epsilon) {
        SurfaceQuadView input = require(quad);
        int component = normalComponent(input.face());
        float plane = input.position(0, component);
        for (int vertex = 1; vertex < input.vertexCount(); vertex++) {
            if (!close(input.position(vertex, component), plane, epsilon)) {
                return false;
            }
        }
        return true;
    }

    public static boolean fullFace(SurfaceQuadView quad, float epsilon) {
        SurfaceQuadView input = require(quad);
        if (!axisAligned(input, epsilon)) {
            return false;
        }
        int normal = normalComponent(input.face());
        int first = normal == 0 ? 1 : 0;
        int second = normal == 2 ? 1 : 2;
        float plane = input.position(0, normal);
        float expected = positiveFace(input.face()) ? 1.0F : 0.0F;
        if (!close(plane, expected, epsilon)) {
            return false;
        }
        float minFirst = Float.POSITIVE_INFINITY;
        float maxFirst = Float.NEGATIVE_INFINITY;
        float minSecond = Float.POSITIVE_INFINITY;
        float maxSecond = Float.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < input.vertexCount(); vertex++) {
            minFirst = Math.min(minFirst, input.position(vertex, first));
            maxFirst = Math.max(maxFirst, input.position(vertex, first));
            minSecond = Math.min(minSecond, input.position(vertex, second));
            maxSecond = Math.max(maxSecond, input.position(vertex, second));
        }
        return close(minFirst, 0.0F, epsilon)
                && close(maxFirst, 1.0F, epsilon)
                && close(minSecond, 0.0F, epsilon)
                && close(maxSecond, 1.0F, epsilon);
    }

    public static boolean validUv(SurfaceQuadView quad, float epsilon) {
        SurfaceQuadView input = require(quad);
        for (int vertex = 0; vertex < input.vertexCount(); vertex++) {
            float u = input.u(vertex);
            float v = input.v(vertex);
            if (!Float.isFinite(u)
                    || !Float.isFinite(v)
                    || u < input.spriteMinU() - epsilon
                    || u > input.spriteMaxU() + epsilon
                    || v < input.spriteMinV() - epsilon
                    || v > input.spriteMaxV() + epsilon) {
                return false;
            }
        }
        return true;
    }

    private static SurfaceQuadView require(SurfaceQuadView quad) {
        return Objects.requireNonNull(quad, "quad");
    }

    private static int normalComponent(SurfaceFace face) {
        return switch (Objects.requireNonNull(face, "face")) {
            case WEST, EAST -> 0;
            case DOWN, UP -> 1;
            case NORTH, SOUTH -> 2;
            case UNDEFINED -> throw new IllegalArgumentException(
                    "surface face must be axis-aligned");
        };
    }

    private static boolean positiveFace(SurfaceFace face) {
        return switch (Objects.requireNonNull(face, "face")) {
            case EAST, UP, SOUTH -> true;
            case WEST, DOWN, NORTH -> false;
            case UNDEFINED -> throw new IllegalArgumentException(
                    "surface face must be axis-aligned");
        };
    }

    private static boolean close(float left, float right, float epsilon) {
        return Math.abs(left - right) <= epsilon;
    }
}
