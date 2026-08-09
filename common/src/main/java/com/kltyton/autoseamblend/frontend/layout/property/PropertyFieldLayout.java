package com.kltyton.autoseamblend.frontend.layout.property;

import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchMetrics;

/**
 * 中文：按属性画布宽度计算标签、控件与选择器行的响应式几何。
 *
 * English: Computes responsive geometry for property labels, controls, and
 * selector rows from the available canvas width.
 */
public final class PropertyFieldLayout {
    private static final int INSET = 16;
    private static final int GAP = UilibWorkbenchMetrics.GRID;
    private static final int WIDE_CONTROL_X = 164;
    private static final int WIDE_FIELD_THRESHOLD = 320;
    private static final int SELECTOR_CONTROLS_WIDTH = 124;
    private static final int SELECTOR_SIDE_THRESHOLD = 296;

    private PropertyFieldLayout() {}

    public static Field field(int contentWidth, int top) {
        if (contentWidth >= WIDE_FIELD_THRESHOLD) {
            return new Field(
                    INSET,
                    top + 7,
                    WIDE_CONTROL_X,
                    top,
                    Math.max(1, contentWidth - WIDE_CONTROL_X - INSET),
                    30);
        }
        return new Field(
                INSET,
                top,
                INSET,
                top + 14,
                Math.max(1, contentWidth - INSET * 2),
                42);
    }

    public static EntryField entryField(int contentWidth, int top, int actionWidth) {
        int usable = Math.max(1, contentWidth - INSET * 2);
        int controlTop = top + 14;
        if (usable >= actionWidth + GAP + 80) {
            return new EntryField(
                    INSET,
                    top,
                    INSET,
                    controlTop,
                    usable - actionWidth - GAP,
                    INSET + usable - actionWidth,
                    controlTop,
                    actionWidth,
                    58);
        }
        return new EntryField(
                INSET,
                top,
                INSET,
                controlTop,
                usable,
                INSET,
                controlTop + 24,
                usable,
                82);
    }

    public static SelectorRow selectorRow(int contentWidth, int left, int top) {
        int usable = Math.max(1, contentWidth - left - INSET);
        if (usable >= SELECTOR_SIDE_THRESHOLD) {
            int chipWidth = usable - SELECTOR_CONTROLS_WIDTH - 6;
            return new SelectorRow(
                    left,
                    top,
                    chipWidth,
                    left + chipWidth + 6,
                    top + 4,
                    38);
        }
        return new SelectorRow(left, top, usable, left, top + 36, 62);
    }

    public record Field(
            int labelX,
            int labelY,
            int controlX,
            int controlY,
            int controlWidth,
            int rowHeight) {}

    public record EntryField(
            int labelX,
            int labelY,
            int inputX,
            int inputY,
            int inputWidth,
            int actionX,
            int actionY,
            int actionWidth,
            int rowHeight) {}

    public record SelectorRow(
            int chipX,
            int chipY,
            int chipWidth,
            int controlsX,
            int controlsY,
            int rowHeight) {}
}
