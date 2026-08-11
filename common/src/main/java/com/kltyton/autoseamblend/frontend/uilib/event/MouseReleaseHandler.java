package com.kltyton.autoseamblend.frontend.uilib.event;

/**
 * 中文：UILib 0.3.6 没有鼠标释放回调；工作台自行分发释放事件到实现本接口的组件。
 *
 * English: UILib 0.3.6 has no mouse-release callback; the workbench screen dispatches
 * release events to components implementing this interface itself.
 */
public interface MouseReleaseHandler {
    boolean preformOnMouseReleaseEvent(
            double mouseX,
            double mouseY,
            int button);
}
