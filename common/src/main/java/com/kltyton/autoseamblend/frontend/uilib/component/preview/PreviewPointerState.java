package com.kltyton.autoseamblend.frontend.uilib.component.preview;

/**
 * 中文：预览画布的跨 Loader 鼠标按键捕获状态；Screen 只负责转发事件，控件不共享全局输入。
 * English: Cross-Loader mouse-button capture state for preview canvases; Screens only forward
 * events and widgets never use global input state.
 */
public final class PreviewPointerState {
    public static final int LEFT_BUTTON = 0;
    public static final int RIGHT_BUTTON = 1;
    public static final int MIDDLE_BUTTON = 2;
    private static final int NONE = -1;

    private int capturedButton = NONE;

    public static boolean validButton(int button) {
        return button == LEFT_BUTTON
                || button == RIGHT_BUTTON
                || button == MIDDLE_BUTTON;
    }

    public boolean begin(int button) {
        if (!validButton(button)) {
            return false;
        }
        capturedButton = button;
        return true;
    }

    public boolean captures(int button) {
        return button == capturedButton;
    }

    public void release() {
        capturedButton = NONE;
    }

    public void reset() {
        release();
    }
}
