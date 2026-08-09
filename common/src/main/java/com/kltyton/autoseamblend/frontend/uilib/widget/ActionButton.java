package com.kltyton.autoseamblend.frontend.uilib.widget;

import com.daqem.uilib.client.gui.component.io.ButtonComponent;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchMetrics;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
public final class ActionButton extends ButtonComponent {
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
                DEFAULT_HEIGHT,
                message,
                (button, screen, mouseX, mouseY, mb) -> {
                    handler.run();
                    return true;
                });
        this.message = message;
        this.handler = handler;
        // 中文：按钮文字由本类 render 无阴影绘制，避免 UILib ScrollingText 双倍绘制。
        // English: The button label is drawn shadow-free by this render override; drop the
        // UILib ScrollingText so it is not drawn twice.
        setText(null);
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
        // 中文：恢复 UILib 9 ButtonComponent 的原生管线——enableBlend/enableDepthTest、
        // 默认/disabled/highlight sprite 选择与 blit。此前完全重写 render 并直接 blit、
        // 不调用 super，丢失该管线导致按钮黑面；按钮文字仍由本类无阴影绘制，避免
        // ScrollingText 双绘。
        // English: Restore the UILib 9 ButtonComponent native pipeline: enableBlend/
        // enableDepthTest, default/disabled/highlight sprite selection, and blit. The
        // previous full override skipped super and blitted directly, losing this
        // pipeline and blackening buttons; the label is still drawn shadow-free here so
        // the ScrollingText is never drawn twice.
        super.render(graphics, mouseX, mouseY, delta);
        boolean enabled = isEnabled();
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
