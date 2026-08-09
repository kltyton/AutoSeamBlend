package com.kltyton.autoseamblend.texture.generation;

import java.util.Objects;

/**
 * 中文：保存精确版本原生引擎槽位表的不可变数组快照；common 不持有任何第三方类型。
 * English: Immutable array snapshots of exact-version native slot maps; common holds no third-party types.
 */
public final class NativeSlotMapSnapshot {
    public static final int CTM_LENGTH = 256;
    public static final int COMPACT_REPRESENTATIVE_LENGTH = 5;
    public static final int SINGLE_AXIS_LENGTH = 4;
    public static final int PRIORITIZED_PRIMARY_LENGTH = 4;
    public static final int PRIORITIZED_SECONDARY_LENGTH = 64;

    private final int[] ctm;
    private final int[] compactRepresentatives;
    private final int[] horizontal;
    private final int[] vertical;
    private final int[] horizontalVerticalPrimary;
    private final int[] horizontalVerticalSecondary;
    private final int[] verticalHorizontalPrimary;
    private final int[] verticalHorizontalSecondary;

    public NativeSlotMapSnapshot(
            int[] ctm,
            int[] compactRepresentatives,
            int[] horizontal,
            int[] vertical,
            int[] horizontalVerticalPrimary,
            int[] horizontalVerticalSecondary,
            int[] verticalHorizontalPrimary,
            int[] verticalHorizontalSecondary) {
        this.ctm = copyOf(ctm, CTM_LENGTH, "ctm");
        this.compactRepresentatives = copyOf(
                compactRepresentatives,
                COMPACT_REPRESENTATIVE_LENGTH,
                "compactRepresentatives");
        this.horizontal = copyOf(horizontal, SINGLE_AXIS_LENGTH, "horizontal");
        this.vertical = copyOf(vertical, SINGLE_AXIS_LENGTH, "vertical");
        this.horizontalVerticalPrimary = copyOf(
                horizontalVerticalPrimary,
                PRIORITIZED_PRIMARY_LENGTH,
                "horizontalVerticalPrimary");
        this.horizontalVerticalSecondary = copyOf(
                horizontalVerticalSecondary,
                PRIORITIZED_SECONDARY_LENGTH,
                "horizontalVerticalSecondary");
        this.verticalHorizontalPrimary = copyOf(
                verticalHorizontalPrimary,
                PRIORITIZED_PRIMARY_LENGTH,
                "verticalHorizontalPrimary");
        this.verticalHorizontalSecondary = copyOf(
                verticalHorizontalSecondary,
                PRIORITIZED_SECONDARY_LENGTH,
                "verticalHorizontalSecondary");
    }

    public int[] ctm() {
        return ctm.clone();
    }

    public int[] compactRepresentatives() {
        return compactRepresentatives.clone();
    }

    public int[] horizontal() {
        return horizontal.clone();
    }

    public int[] vertical() {
        return vertical.clone();
    }

    public int[] horizontalVerticalPrimary() {
        return horizontalVerticalPrimary.clone();
    }

    public int[] horizontalVerticalSecondary() {
        return horizontalVerticalSecondary.clone();
    }

    public int[] verticalHorizontalPrimary() {
        return verticalHorizontalPrimary.clone();
    }

    public int[] verticalHorizontalSecondary() {
        return verticalHorizontalSecondary.clone();
    }

    private static int[] copyOf(int[] values, int expectedLength, String name) {
        Objects.requireNonNull(values, name);
        if (values.length != expectedLength) {
            throw new IllegalArgumentException(
                    name + " map length must be " + expectedLength + " but was " + values.length);
        }
        return values.clone();
    }
}
