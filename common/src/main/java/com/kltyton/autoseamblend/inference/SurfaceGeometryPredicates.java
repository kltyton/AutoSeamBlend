package com.kltyton.autoseamblend.inference;

import java.util.Objects;

import net.minecraft.core.Direction;

/**
 * 中文：模型表面几何的 Loader 无关判定；只接收已冻结的数值和稳定方向值。
 * <p>
 * English: Loader-neutral predicates for model-surface geometry; they accept only frozen numeric
 * values and the stable Minecraft direction value.
 */
public final class SurfaceGeometryPredicates {
    private static final float MODEL_EPSILON = 1.0e-4F;

    private SurfaceGeometryPredicates() {
    }

    /**
     * 中文：判断原生 cuboid 面的 UV 是否由有限数值组成。
     * <p>
     * English: Checks whether a native cuboid face UV is made of finite values.
     */
    public static boolean hasFiniteUv(
            float minU,
            float minV,
            float maxU,
            float maxV) {
        return Float.isFinite(minU)
                && Float.isFinite(minV)
                && Float.isFinite(maxU)
                && Float.isFinite(maxV);
    }

    /**
     * 中文：按 NeoForge 基线判断元素是否覆盖完整、未旋转的 16x16x16 方块。
     * <p>
     * English: Applies the NeoForge baseline for an unrotated 16x16x16 full-block element.
     */
    public static boolean isFullBlock(
            boolean axisAligned,
            float fromX,
            float fromY,
            float fromZ,
            float toX,
            float toY,
            float toZ) {
        return axisAligned
                && close(fromX, 0.0F)
                && close(fromY, 0.0F)
                && close(fromZ, 0.0F)
                && close(toX, 16.0F)
                && close(toY, 16.0F)
                && close(toZ, 16.0F);
    }

    /**
     * 中文：按原生面方向判断 cuboid 是否覆盖完整方块面。
     * <p>
     * English: Checks whether a cuboid covers the complete block face for its native direction.
     */
    public static boolean isFullFace(
            float fromX,
            float fromY,
            float fromZ,
            float toX,
            float toY,
            float toZ,
            Direction direction) {
        Objects.requireNonNull(direction, "direction");
        return switch (direction) {
            case DOWN -> close(fromY, 0.0F)
                    && close(fromX, 0.0F)
                    && close(toX, 16.0F)
                    && close(fromZ, 0.0F)
                    && close(toZ, 16.0F);
            case UP -> close(toY, 16.0F)
                    && close(fromX, 0.0F)
                    && close(toX, 16.0F)
                    && close(fromZ, 0.0F)
                    && close(toZ, 16.0F);
            case NORTH -> close(fromZ, 0.0F)
                    && close(fromX, 0.0F)
                    && close(toX, 16.0F)
                    && close(fromY, 0.0F)
                    && close(toY, 16.0F);
            case SOUTH -> close(toZ, 16.0F)
                    && close(fromX, 0.0F)
                    && close(toX, 16.0F)
                    && close(fromY, 0.0F)
                    && close(toY, 16.0F);
            case WEST -> close(fromX, 0.0F)
                    && close(fromZ, 0.0F)
                    && close(toZ, 16.0F)
                    && close(fromY, 0.0F)
                    && close(toY, 16.0F);
            case EAST -> close(toX, 16.0F)
                    && close(fromZ, 0.0F)
                    && close(toZ, 16.0F)
                    && close(fromY, 0.0F)
                    && close(toY, 16.0F);
        };
    }

    private static boolean close(float left, float right) {
        return Math.abs(left - right) <= MODEL_EPSILON;
    }
}
