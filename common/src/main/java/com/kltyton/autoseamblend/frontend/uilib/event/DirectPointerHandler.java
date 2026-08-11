package com.kltyton.autoseamblend.frontend.uilib.event;

/**
 * 中文：标记不依赖 UILib 回调对象、而是在组件覆写方法中直接处理的指针事件。
 *
 * English: Marks pointer events handled directly by component overrides instead
 * of UILib callback objects.
 */
public interface DirectPointerHandler {
    default boolean handlesDirectClick() {
        return false;
    }

    default boolean handlesDirectDrag() {
        return false;
    }

    default boolean handlesDirectScroll() {
        return false;
    }
}
