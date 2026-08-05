package com.kltyton.autoseamblend.frontend.uilib.component.preview;

import net.minecraft.client.input.MouseButtonEvent;

/**
 * 中文：在 Screen 边界为预览画布持有中键与右键拖拽捕获，不把输入状态混入工作台布局。
 *
 * English:
 * Holds middle- and right-button drag capture for the preview canvas at the
 * Screen boundary without mixing input state into workbench layout code.
 */
public final class PreviewPointerCapture {
    private static final int LEFT_BUTTON = 0;
    private static final int NONE = -1;

    private int button = NONE;

    public void reset() {
        button = NONE;
    }

    public boolean click(
            InteractiveBlockPreviewWidget scene,
            MouseButtonEvent event,
            boolean doubleClick) {
        if (scene == null
                || event.button() == LEFT_BUTTON
                || !scene.isMouseOver(event.x(), event.y())
                || !scene.mouseClicked(event, doubleClick)) {
            return false;
        }
        button = event.button();
        return true;
    }

    public boolean drag(
            InteractiveBlockPreviewWidget scene,
            MouseButtonEvent event,
            double deltaX,
            double deltaY) {
        return scene != null
                && event.button() == button
                && scene.mouseDragged(
                        event,
                        deltaX,
                        deltaY);
    }

    public boolean release(
            InteractiveBlockPreviewWidget scene,
            MouseButtonEvent event) {
        if (event.button() != button) {
            return false;
        }
        button = NONE;
        return scene != null
                && scene.mouseReleased(event);
    }
}
