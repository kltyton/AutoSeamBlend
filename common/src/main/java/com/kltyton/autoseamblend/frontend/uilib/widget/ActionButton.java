package com.kltyton.autoseamblend.frontend.uilib.widget;

import com.daqem.uilib.gui.widget.ButtonWidget;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchMetrics;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;

/**
 * 中文：视图构建后仍可绑定动作的 UILib 按钮。
 *
 * English: UILib button whose action can be bound after the view is
 * constructed.
 */
public final class ActionButton extends ButtonWidget {
    private static final int DEFAULT_WIDTH = 80;
    private static final int DEFAULT_HEIGHT =
            UilibWorkbenchMetrics.CONTROL_HEIGHT;
    private static final int TEXT_INSET =
            UilibWorkbenchMetrics.GRID * 2;

    private final ActionHandler handler;

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
                ignored -> handler.run());
        this.handler = handler;
    }

    public void setAction(Runnable action) {
        handler.action = Objects.requireNonNull(
                action,
                "action");
    }

    /**
     * 中文：工作台按钮使用无阴影高对比文字，避免浅灰面板上的双边缘模糊。
     *
     * English: Workbench buttons use high-contrast text without a shadow,
     * avoiding double-edge blur on light gray panels.
     */
    @Override
    protected void extractContents(
            @NotNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        extractDefaultSprite(graphics);
        Font font = Minecraft.getInstance().font;
        int availableWidth = Math.max(
                0,
                getWidth() - TEXT_INSET * 2);
        if (availableWidth <= 0) {
            return;
        }
        FormattedText clipped =
                font.width(getMessage()) <= availableWidth
                        ? getMessage()
                        : font.substrByWidth(
                                getMessage(),
                                availableWidth);
        FormattedCharSequence visual = Language.getInstance()
                .getVisualOrder(clipped);
        int textX = getX()
                + (getWidth() - font.width(visual))
                        / 2;
        int textY = getY()
                + (getHeight() - font.lineHeight)
                        / 2;
        graphics.text(
                font,
                visual,
                textX,
                textY,
                active
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
