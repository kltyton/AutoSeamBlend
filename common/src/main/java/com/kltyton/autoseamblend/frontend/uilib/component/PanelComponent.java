package com.kltyton.autoseamblend.frontend.uilib.component;

import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 中文：使用原版容器高光与阴影方向的石质工作台面板。
 *
 * English: Stone workbench panel using vanilla container highlight and shadow
 * directions.
 */
public final class PanelComponent
        extends AbstractAbsoluteComponent<PanelComponent> {
    private final int color;
    private final Relief relief;

    public PanelComponent(
            int x,
            int y,
            int width,
            int height,
            int color) {
        this(
                x,
                y,
                width,
                height,
                color,
                Relief.OUTSET);
    }

    public PanelComponent(
            int x,
            int y,
            int width,
            int height,
            int color,
            Relief relief) {
        super(x, y, width, height);
        this.color = color;
        this.relief = java.util.Objects.requireNonNull(
                relief,
                "relief");
    }

    @Override
    protected void extractRenderState(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        int left = getTotalX();
        int top = getTotalY();
        int right = left + getWidth();
        int bottom = top + getHeight();
        graphics.fill(left, top, right, bottom, color);
        if (relief == Relief.FLAT) {
            border(
                    graphics,
                    left,
                    top,
                    right,
                    bottom,
                    UilibWorkbenchTheme.BORDER_DEFAULT,
                    UilibWorkbenchTheme.BORDER_DEFAULT);
            return;
        }
        boolean inset = relief == Relief.INSET;
        border(
                graphics,
                left,
                top,
                right,
                bottom,
                inset
                        ? UilibWorkbenchTheme.BORDER_DEEP
                        : UilibWorkbenchTheme.BORDER_HIGHLIGHT,
                inset
                        ? UilibWorkbenchTheme.BORDER_HIGHLIGHT
                        : UilibWorkbenchTheme.BORDER_SHADOW);
        if (getWidth() > 3 && getHeight() > 3) {
            border(
                    graphics,
                    left + 1,
                    top + 1,
                    right - 1,
                    bottom - 1,
                    inset
                            ? UilibWorkbenchTheme.BORDER_SHADOW
                            : UilibWorkbenchTheme.BORDER_LIGHT,
                    inset
                            ? UilibWorkbenchTheme.BORDER_LIGHT
                            : UilibWorkbenchTheme.BORDER_DEEP);
        }
    }

    private static void border(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom,
            int topLeft,
            int bottomRight) {
        graphics.hLine(
                left,
                right - 1,
                top,
                topLeft);
        graphics.vLine(
                left,
                top,
                bottom - 1,
                topLeft);
        graphics.hLine(
                left,
                right - 1,
                bottom - 1,
                bottomRight);
        graphics.vLine(
                right - 1,
                top,
                bottom - 1,
                bottomRight);
    }

    /** 中文：石质面板的边框方向。 / English: Border direction of a stone panel. */
    public enum Relief {
        OUTSET,
        INSET,
        FLAT
    }
}
