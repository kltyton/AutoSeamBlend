package com.kltyton.autoseamblend.frontend.layout;

/**
 * 中文：把三个同级动作精确分配到统一列线，并把余数像素稳定分配给左侧列。
 *
 * English: Places three peer actions on shared column lines and distributes
 * remainder pixels deterministically to the leftmost columns.
 */
public record ThreeColumnActionGrid(
        int left,
        int usableWidth,
        int gap,
        int baseWidth,
        int remainder) {
    private static final int COLUMNS = 3;

    public ThreeColumnActionGrid {
        if (left < 0
                || usableWidth < 0
                || gap < 0
                || baseWidth < 0
                || remainder < 0
                || remainder >= COLUMNS) {
            throw new IllegalArgumentException(
                    "invalid three-column action grid");
        }
    }

    public static ThreeColumnActionGrid within(
            int panelLeft,
            int panelWidth,
            int inset,
            int gap) {
        if (panelLeft < 0
                || panelWidth <= 0
                || inset < 0
                || gap < 0) {
            throw new IllegalArgumentException(
                    "invalid action-grid bounds");
        }
        int effectiveInset = Math.min(
                inset,
                Math.max(0, (panelWidth - COLUMNS) / 2));
        int innerWidth = panelWidth - effectiveInset * 2;
        int effectiveGap = innerWidth >= COLUMNS
                ? Math.min(
                        gap,
                        (innerWidth - COLUMNS)
                                / (COLUMNS - 1))
                : 0;
        int usable = Math.max(
                0,
                innerWidth
                        - effectiveGap * (COLUMNS - 1));
        return new ThreeColumnActionGrid(
                panelLeft + effectiveInset,
                usable,
                effectiveGap,
                usable / COLUMNS,
                usable % COLUMNS);
    }

    public int x(int column) {
        requireColumn(column);
        return left
                + column * (baseWidth + gap)
                + Math.min(
                        column,
                        remainder);
    }

    public int width(int column) {
        requireColumn(column);
        return baseWidth
                + (column < remainder
                        ? 1
                        : 0);
    }

    private static void requireColumn(
            int column) {
        if (column < 0
                || column >= COLUMNS) {
            throw new IndexOutOfBoundsException(
                    "action-grid column outside [0,2]");
        }
    }
}
