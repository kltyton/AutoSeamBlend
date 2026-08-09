package com.kltyton.autoseamblend.frontend.uilib.widget;

/**
 * 中文：原生按钮 sprite 与 DESIGN.md FOCUS_RING 状态的纯逻辑选择；禁用优先于悬停，
 * 键盘焦点绘制原版黄色焦点环。
 *
 * English:
 * Pure-logic selection of the native button sprite and the DESIGN.md FOCUS_RING state;
 * disabled wins over hover, and keyboard focus draws the vanilla yellow focus ring.
 */
public final class ButtonSpriteState {
    private ButtonSpriteState() {}

    /**
     * 中文：UILib ButtonComponent 原生 sprite 下标：0 默认、1 禁用、2 悬停。
     *
     * English: UILib ButtonComponent native sprite index: 0 normal, 1 disabled, 2 hovered.
     */
    public static int spriteIndex(
            boolean enabled,
            boolean hovered) {
        return enabled
                ? (hovered ? 2 : 0)
                : 1;
    }

    /**
     * 中文：聚焦且可用时按 DESIGN.md 绘制原版黄色 FOCUS_RING；禁用控件不显示焦点环，
     * 与原版键盘导航一致。
     *
     * English:
     * Draws the vanilla yellow FOCUS_RING when focused and enabled per DESIGN.md; disabled
     * widgets show no focus ring, matching vanilla keyboard navigation.
     */
    public static boolean drawsFocusRing(
            boolean focused,
            boolean enabled) {
        return focused && enabled;
    }
}
