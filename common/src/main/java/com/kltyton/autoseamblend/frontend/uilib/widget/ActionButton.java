package com.kltyton.autoseamblend.frontend.uilib.widget;

import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchMetrics;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * 中文：视图构建后仍可绑定动作的 UILib 按钮。
 *
 * English: UILib button whose action can be bound after the view is
 * constructed.
 */
public final class ActionButton extends AutoSeamBlendButton {
    private static final int DEFAULT_WIDTH = 80;
    private static final int DEFAULT_HEIGHT =
            UilibWorkbenchMetrics.CONTROL_HEIGHT;
    private static final int TEXT_INSET =
            UilibWorkbenchMetrics.GRID * 2;

    private final ActionHandler handler;
    private Component message;

    public ActionButton(Component message) {
        this(message, new ActionHandler());
    }

    private ActionButton(
            Component message,
            ActionHandler handler) {
        super(
                0,
                0,
                DEFAULT_WIDTH,
                DEFAULT_HEIGHT);
        this.message = message;
        this.handler = handler;
        // 中文：0.3.6 的 ButtonComponent 没有 (x,y,w,h,message,onClick) 构造器；
        // 文字由本类 render 无阴影绘制，点击经 OnClickEvent 绑定。
        // English: 0.3.6 ButtonComponent has no (x,y,w,h,message,onClick) ctor;
        // the label is drawn by this render override and the click is bound via OnClickEvent.
        bind(
                message,
                handler::run);
    }

    public void setAction(Runnable action) {
        handler.action = Objects.requireNonNull(
                action,
                "action");
    }

    public void setActive(boolean active) {
        setEnabled(active);
    }

    public void setMessage(Component message) {
        this.message = message;
    }

    public Component getMessage() {
        return message;
    }

    /**
     * 中文：工作台按钮使用无阴影高对比文字，避免浅灰面板上的双边缘模糊。
     *
     * English: Workbench buttons use high-contrast text without a shadow,
     * avoiding double-edge blur on light gray panels.
     */
    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float delta) {
        boolean enabled = isEnabled();
        // 中文：AutoSeamBlendButton 为 UILib 0.3.6 提供的是 null 纹理，因而
        // ButtonComponent.render() 会直接返回。这里必须显式绘制 1.20.1 原版按钮，
        // 否则底栏、工具栏和抽屉动作只剩文字。
        // English: AutoSeamBlendButton supplies a null texture to UILib 0.3.6, so
        // ButtonComponent.render() returns immediately. Draw the 1.20.1 vanilla
        // button explicitly or footer, toolbar, and drawer actions lose their surface.
        graphics.blitNineSliced(
                AbstractWidget.WIDGETS_LOCATION,
                0,
                0,
                getWidth(),
                getHeight(),
                20,
                4,
                200,
                20,
                0,
                ButtonSpriteState.buttonSpriteV(
                        ButtonSpriteState.spriteIndex(
                                enabled,
                                isTotalHovered(mouseX, mouseY))));
        if (ButtonSpriteState.drawsFocusRing(
                isFocused(),
                enabled)) {
            int right = getWidth();
            int bottom = getHeight();
            graphics.hLine(
                    0,
                    right - 1,
                    0,
                    UilibWorkbenchTheme.FOCUS_RING);
            graphics.hLine(
                    0,
                    right - 1,
                    bottom - 1,
                    UilibWorkbenchTheme.FOCUS_RING);
            graphics.vLine(
                    0,
                    0,
                    bottom - 1,
                    UilibWorkbenchTheme.FOCUS_RING);
            graphics.vLine(
                    right - 1,
                    0,
                    bottom - 1,
                    UilibWorkbenchTheme.FOCUS_RING);
        }
        Font font = Minecraft.getInstance().font;
        int availableWidth = Math.max(
                0,
                getWidth() - TEXT_INSET * 2);
        if (availableWidth <= 0) {
            return;
        }
        FormattedText clipped =
                font.width(message) <= availableWidth
                        ? message
                        : font.substrByWidth(
                                message,
                                availableWidth);
        FormattedCharSequence visual = Language.getInstance()
                .getVisualOrder(clipped);
        int textX = (getWidth() - font.width(visual))
                / 2;
        int textY = (getHeight() - font.lineHeight)
                / 2;
        graphics.drawString(
                font,
                visual,
                textX,
                textY,
                enabled
                        ? UilibWorkbenchTheme.TEXT_INVERSE
                        : UilibWorkbenchTheme.TEXT_MUTED,
                false);
    }

    private static final class ActionHandler {
        private Runnable action = () -> {};

        private void run() {
            action.run();
        }
    }
}
