package com.kltyton.autoseamblend.frontend.uilib.widget;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文：UILib 9.0.0 ScrollContentComponent 垂直布局契约的纯逻辑镜像；行必须按 4px 网格
 * 非重叠堆叠，内容高度为行高之和加间隔，滚动偏移被裁剪在视口范围内并在重建后保持。
 *
 * English:
 * Pure-logic mirror of the UILib 9.0.0 ScrollContentComponent vertical layout contract;
 * rows must stack without overlap on the 4px grid, content height is the row-height sum
 * plus spacing, the scroll offset is clamped to the viewport, and it survives rebuilds.
 */
public final class ScrollBodyLayout {
    private ScrollBodyLayout() {}

    /**
     * 中文：按 UILib ScrollContentComponent 的垂直布局逐行计算 y：前一行高度累加后再加
     * 固定间隔，保证行互不重叠。
     *
     * English:
     * Computes each row's y the way UILib ScrollContentComponent lays out vertically:
     * accumulate the previous row height plus the fixed spacing, so rows never overlap.
     */
    public static List<Integer> stackedYs(
            List<Integer> heights,
            int spacing) {
        List<Integer> positions = new ArrayList<>(heights.size());
        int y = 0;
        for (int index = 0; index < heights.size(); index++) {
            positions.add(y);
            y += heights.get(index);
            if (index < heights.size() - 1) {
                y += spacing;
            }
        }
        return positions;
    }

    /**
     * 中文：内容高度为所有行高之和加 (n-1) 个间隔，与 ScrollContentComponent 一致。
     *
     * English:
     * Content height is the row-height sum plus (n-1) spacing, matching
     * ScrollContentComponent.
     */
    public static int contentHeight(
            List<Integer> heights,
            int spacing) {
        if (heights.isEmpty()) {
            return 0;
        }
        return heights.stream()
                        .mapToInt(Integer::intValue)
                        .sum()
                + spacing * (heights.size() - 1);
    }

    /**
     * 中文：最大滚动偏移为内容高度超出视口的部分，不小于 0。
     *
     * English: Max scroll offset is the content height beyond the viewport, never negative.
     */
    public static int maxScroll(
            int contentHeight,
            int viewportHeight) {
        return Math.max(
                0,
                contentHeight - viewportHeight);
    }

    /**
     * 中文：把滚动偏移裁剪到 [-maxScroll, 0]；内容不超视口时归零，重建后原偏移可恢复。
     *
     * English: Clamps the scroll offset into [-maxScroll, 0]; zero when content fits the
     * viewport, so a captured offset can be restored after a rebuild.
     */
    public static int clampOffset(
            int offset,
            int contentHeight,
            int viewportHeight) {
        int max = maxScroll(
                contentHeight,
                viewportHeight);
        return Math.max(
                -max,
                Math.min(0, offset));
    }
}
