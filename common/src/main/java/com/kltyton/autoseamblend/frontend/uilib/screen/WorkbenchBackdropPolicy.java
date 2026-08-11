package com.kltyton.autoseamblend.frontend.uilib.screen;

/**
 * 中文：工作台背景策略的纯逻辑选择；按 DESIGN.md 必须保留 Minecraft 原版模糊/压暗，
 * 而不是 UILib 默认渐变。
 *
 * English:
 * Pure-logic selection of the workbench backdrop; per DESIGN.md it must keep the Minecraft
 * vanilla blur/darken instead of the UILib default gradient.
 */
public final class WorkbenchBackdropPolicy {
    /** 中文：可选背景。 / English: Selectable backdrops. */
    public enum Kind {
        /** 中文：Minecraft 原版模糊/压暗。 / English: Vanilla Minecraft blur/darken. */
        VANILLA_BLUR_DARKEN,
        /** 中文：UILib 默认渐变。 / English: UILib default gradient. */
        UILIB_GRADIENT
    }

    private WorkbenchBackdropPolicy() {}

    /**
     * 中文：按 DESIGN.md 保留 Minecraft 原版模糊/压暗背景。
     *
     * English: Keeps the vanilla Minecraft blur/darken backdrop per DESIGN.md.
     */
    public static Kind selected() {
        return Kind.VANILLA_BLUR_DARKEN;
    }
}
