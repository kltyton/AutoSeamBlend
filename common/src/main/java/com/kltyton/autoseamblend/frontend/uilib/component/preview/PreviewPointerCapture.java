package com.kltyton.autoseamblend.frontend.uilib.component.preview;

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

    /**
     * 中文：仅用于 Screen 关闭等真正结束手势的场合；工作台重建不得调用本方法，
     * 否则拖拽捕获会在重建后丢失（见 NeoForgePreviewWorkspaceRenderer.clear()）。
     *
     * English: Intended only for truly ending a gesture such as closing the
     * Screen; workbench rebuilds must not call this, otherwise the drag capture
     * is lost after the rebuild (see NeoForgePreviewWorkspaceRenderer.clear()).
     */
    public void reset() {
        button = NONE;
    }

    /**
     * 中文：分发每次点击；左键即时处理但不捕获。
     *
     * English: Dispatches every click; the left button is handled immediately
     * but never captured.
     */
    public boolean click(
            InteractiveBlockPreviewWidget scene,
            double mouseX,
            double mouseY,
            int button) {
        boolean over = scene != null
                && scene.isMouseOver(mouseX, mouseY);
        boolean handled = over
                && scene.mouseClicked(mouseX, mouseY, button);
        // 中文：左键由场景即时处理（增删/循环中心）并消费，但只对中键/右键建立拖拽捕获。
        // English: The left button is handled and consumed immediately by the
        // scene (toggle/cycle), while only middle/right establish drag capture.
        if (handled && button != LEFT_BUTTON) {
            this.button = button;
        }
        return handled;
    }

    /**
     * 中文：分发拖拽；只有仍持有匹配按键捕获时才交给场景。
     *
     * English: Dispatches a drag; only a still-held matching capture reaches
     * the scene.
     */
    public boolean drag(
            InteractiveBlockPreviewWidget scene,
            double mouseX,
            double mouseY,
            int button,
            double deltaX,
            double deltaY) {
        boolean captured = scene != null
                && button == this.button;
        boolean handled = captured
                && scene.mouseDragged(
                        mouseX,
                        mouseY,
                        button,
                        deltaX,
                        deltaY);
        return handled;
    }

    /**
     * 中文：释放时清除捕获状态；不匹配的按键直接忽略。
     *
     * English: Clears the capture on release; a non-matching button is ignored.
     */
    public boolean release(
            InteractiveBlockPreviewWidget scene,
            double mouseX,
            double mouseY,
            int button) {
        if (button != this.button) {
            return false;
        }
        this.button = NONE;
        boolean handled = scene != null
                && scene.mouseReleased(mouseX, mouseY, button);
        return handled;
    }
}
