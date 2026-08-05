package com.kltyton.autoseamblend.frontend.uilib.component.preview;

import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * 中文：预览控件共享的凹入画布、边框、不可用文本和旁白文案；Loader 控件只绘制真实结果。
 * English: Shared preview-canvas chrome, border, unavailable text, and narration copy; Loader
 * widgets only draw real runtime results.
 */
public final class PreviewWidgetChrome {
    private PreviewWidgetChrome() {}

    public static Component narration(boolean exactFace) {
        return Component.translatable(
                exactFace
                        ? "gui.autoseamblend.preview.exact_face"
                        : "gui.autoseamblend.preview.scene.narration");
    }

    public static Component unavailable() {
        return Component.translatable("gui.autoseamblend.preview.unavailable");
    }

    public static void drawCanvas(
            GuiGraphicsExtractor graphics,
            int left,
            int top,
            int width,
            int height) {
        Objects.requireNonNull(graphics, "graphics").fill(
                left,
                top,
                left + width,
                top + height,
                UilibWorkbenchTheme.SURFACE_INPUT);
    }

    public static void drawUnavailable(
            GuiGraphicsExtractor graphics,
            int left,
            int top,
            Component reason) {
        Objects.requireNonNull(graphics, "graphics").textRenderer().accept(
                left + 8,
                top + 8,
                Objects.requireNonNull(reason, "reason").copy().withColor(
                        UilibWorkbenchTheme.TEXT_BUTTON_SECONDARY));
    }

    public static void drawBorder(
            GuiGraphicsExtractor graphics,
            int left,
            int top,
            int width,
            int height,
            boolean focused) {
        Objects.requireNonNull(graphics, "graphics");
        int border = focused
                ? UilibWorkbenchTheme.FOCUS_RING
                : UilibWorkbenchTheme.BORDER_DEFAULT;
        graphics.horizontalLine(left, left + width - 1, top, border);
        graphics.horizontalLine(
                left,
                left + width - 1,
                top + height - 1,
                border);
        graphics.verticalLine(left, top, top + height - 1, border);
        graphics.verticalLine(
                left + width - 1,
                top,
                top + height - 1,
                border);
    }
}
