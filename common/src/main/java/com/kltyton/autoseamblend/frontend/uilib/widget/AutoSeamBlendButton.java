package com.kltyton.autoseamblend.frontend.uilib.widget;

import com.daqem.uilib.client.gui.component.ButtonComponent;
import net.minecraft.network.chat.Component;

/**
 * 中文：UILib 0.3.6 按钮基类：0.3.6 的 ButtonComponent 只有 (ITexture,x,y,w,h) 构造器且
 * 没有 enabled/focused 状态，这里提供项目级的启用/焦点状态与旁白绑定，保持 9.0.0 语义。
 *
 * English: UILib 0.3.6 button base: 0.3.6 ButtonComponent only has the
 * (ITexture,x,y,w,h) constructor and no enabled/focused state; this base supplies the
 * project-level enabled/focus state and narration binding to keep 9.0.0 semantics.
 */
public abstract class AutoSeamBlendButton
        extends ButtonComponent {
    private boolean enabled = true;

    protected AutoSeamBlendButton(
            int x,
            int y,
            int width,
            int height) {
        super(
                null,
                x,
                y,
                width,
                height);
    }

    public final void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    /** 中文：0.3.6 无焦点状态；保持接口兼容并返回 false。 / English: 0.3.6 has no focus state; returns false for API compatibility. */
    public boolean isFocused() {
        return false;
    }

    /** 中文：绑定旁白文本与点击动作（0.3.6 通过 OnClickEvent 分发）。 / English: Binds narration and click action (dispatched via OnClickEvent in 0.3.6). */
    protected void bind(
            Component narration,
            Runnable action) {
        setText(null);
        setOnClickEvent(
                (button, screen, mouseX, mouseY, mb) -> {
                    if (enabled) {
                        action.run();
                    }
                });
    }
}
