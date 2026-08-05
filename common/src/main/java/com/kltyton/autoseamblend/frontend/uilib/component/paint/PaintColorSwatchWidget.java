package com.kltyton.autoseamblend.frontend.uilib.component.paint;

import com.daqem.uilib.api.widget.IWidget;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * 中文：可旁白、可选中的 RGBA 调色板色块。
 *
 * English: Narratable and selectable RGBA palette swatch.
 */
public final class PaintColorSwatchWidget
        extends AbstractWidget
        implements IWidget {
    private static final int LEFT_BUTTON = 0;

    private final int color;
    private final BooleanSupplier selected;
    private final Runnable choose;

    public PaintColorSwatchWidget(
            int color,
            String rgba,
            BooleanSupplier selected,
            Runnable choose) {
        super(
                0,
                0,
                18,
                18,
                Component.translatable(
                        "gui.autoseamblend.paint.swatch",
                        rgba));
        this.color = color;
        this.selected = Objects.requireNonNull(
                selected,
                "selected");
        this.choose = Objects.requireNonNull(
                choose,
                "choose");
    }

    @Override
    protected void extractWidgetRenderState(
            @NotNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        int border = selected.getAsBoolean()
                ? UilibWorkbenchTheme.ACCENT_PRIMARY
                : isHovered()
                        ? UilibWorkbenchTheme.BORDER_DEFAULT
                        : UilibWorkbenchTheme.BORDER_SUBTLE;
        graphics.fill(
                getX(),
                getY(),
                getX() + getWidth(),
                getY() + getHeight(),
                border);
        graphics.fill(
                getX() + 2,
                getY() + 2,
                getX() + getWidth() - 2,
                getY() + getHeight() - 2,
                color);
    }

    @Override
    protected boolean isValidClickButton(
            MouseButtonInfo button) {
        return button.button() == LEFT_BUTTON;
    }

    @Override
    public void onClick(
            MouseButtonEvent event,
            boolean doubleClick) {
        choose.run();
    }

    @Override
    protected void updateWidgetNarration(
            @NotNull NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
