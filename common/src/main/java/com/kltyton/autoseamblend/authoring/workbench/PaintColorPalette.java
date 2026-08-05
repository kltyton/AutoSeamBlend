package com.kltyton.autoseamblend.authoring.workbench;

import java.util.List;

/**
 * 中文：静态 RGBA 编辑器的稳定作者颜色预设；这些值属于可编辑内容，不属于界面外观颜色。
 *
 * English:
 * Stable author-color presets for the static RGBA editor. These values are
 * editable content choices rather than interface-chrome colors.
 */
public final class PaintColorPalette {
    private static final List<Integer> STANDARD = List.of(
            0xFFFFFFFF,
            0xFFC8C8C8,
            0xFF555555,
            0xFF111111,
            0xFFE34B4B,
            0xFFF08B32,
            0xFFF0D949,
            0xFF65B856,
            0xFF55C7C7,
            0xFF4F79D8,
            0xFF8B5CC7,
            0xFFD85FB2);

    private PaintColorPalette() {}

    public static List<Integer> standard() {
        return STANDARD;
    }
}
