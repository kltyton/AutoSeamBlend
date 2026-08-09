package com.kltyton.autoseamblend.frontend.uilib.component.paint;

import com.daqem.uilib.client.gui.component.io.ButtonComponent;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 中文：可旁白、可选中的 RGBA 调色板色块。
 *
 * English: Selectable RGBA palette swatch.
 */
    public final class PaintColorSwatchWidget
        extends ButtonComponent {
    private final int color;
    private final BooleanSupplier selected;
    private final Runnable choose;

    public void setActive(boolean active) {
        setEnabled(active);
    }

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
                        rgba),
                (button, screen, mouseX, mouseY, mb) -> {
                    choose.run();
                    return true;
                });
        // 中文：UILib 9.0.0 的 ButtonComponent 会把 message 作为可滚动文本渲染在
        // 色块上；色块只需要颜色、边框与点击，因此抑制默认文本，仅保留旁白意图。
        // English: UILib 9.0.0 ButtonComponent renders its message as scrolling
        // text over the swatch; a swatch only needs color, border, and click, so
        // the default text is suppressed while the narration intent is kept.
        setText(null);
        this.color = color;
        this.selected = Objects.requireNonNull(
                selected,
                "selected");
        this.choose = Objects.requireNonNull(
                choose,
                "choose");
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float delta) {
        int border = selected.getAsBoolean()
                ? UilibWorkbenchTheme.ACCENT_PRIMARY
                : isTotalHovered(mouseX, mouseY)
                        ? UilibWorkbenchTheme.BORDER_DEFAULT
                        : UilibWorkbenchTheme.BORDER_SUBTLE;
        graphics.fill(
                0,
                0,
                getWidth(),
                getHeight(),
                border);
        graphics.fill(
                2,
                2,
                getWidth() - 2,
                getHeight() - 2,
                color);
    }
}
