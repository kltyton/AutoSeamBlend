package com.kltyton.autoseamblend.frontend.uilib.layout.property;

import com.kltyton.autoseamblend.frontend.uilib.widget.ActionButton;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * 中文：属性编辑器共享的文本按像素宽度截断/换行、区段动作和按钮构造规则。
 *
 * English: Shared property-editor rules for width-based text fitting/wrapping,
 * section actions, and translated button construction.
 */
public final class NativePropertyPanelLayout {
    private static final int CONTENT_INSET = 16;
    private static final int SELECTOR_FIT_WIDTH = 320;

    private NativePropertyPanelLayout() {}

    public static String compactSelector(String value) {
        return fit(value, SELECTOR_FIT_WIDTH);
    }

    /**
     * 中文：按 Minecraft 字体像素宽度省略超长文本，避免横向溢出。
     *
     * English: Ellipsizes text to a pixel width measured with the Minecraft
     * font so it can never overflow its content column horizontally.
     */
    public static String fit(String value, int maxWidth) {
        if (value == null
                || value.isEmpty()
                || maxWidth <= 0) {
            return value == null
                    ? ""
                    : value;
        }
        Font font = Minecraft.getInstance().font;
        if (font.width(value) <= maxWidth) {
            return value;
        }
        int ellipsisWidth = font.width("\u2026");
        int prefixWidth = Math.max(
                1,
                maxWidth - ellipsisWidth);
        return font.plainSubstrByWidth(
                        value,
                        prefixWidth)
                + "\u2026";
    }

    /**
     * 中文：按像素宽度省略超长组件文本，保留组件语义。
     *
     * English: Ellipsizes an over-long component to a pixel width.
     */
    public static Component fitComponent(
            Component text,
            int maxWidth) {
        if (text == null
                || maxWidth <= 0) {
            return text == null
                    ? Component.empty()
                    : text;
        }
        Font font = Minecraft.getInstance().font;
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return Component.literal(
                fit(text.getString(), maxWidth));
    }

    /**
     * 中文：按像素宽度把文本拆成不超宽的多行，供正文逐行绘制。
     *
     * English: Splits text into lines that never exceed maxWidth pixels, so
     * the body can draw each line without horizontal overflow.
     */
    public static List<Component> wrap(
            Component text,
            int maxWidth) {
        if (text == null
                || maxWidth <= 0) {
            return List.of(Component.empty());
        }
        Font font = Minecraft.getInstance().font;
        List<Component> lines = new ArrayList<>();
        for (String segment :
                text.getString().split("\n", -1)) {
            if (segment.isEmpty()) {
                lines.add(Component.empty());
                continue;
            }
            if (font.width(segment) <= maxWidth) {
                lines.add(Component.literal(segment));
                continue;
            }
            String remaining = segment;
            while (!remaining.isEmpty()
                    && font.width(remaining)
                            > maxWidth) {
                String prefix = font.plainSubstrByWidth(
                        remaining,
                        maxWidth);
                if (prefix.isEmpty()) {
                    break;
                }
                lines.add(Component.literal(prefix));
                remaining = remaining.substring(
                        prefix.length());
            }
            if (!remaining.isEmpty()) {
                lines.add(Component.literal(remaining));
            }
        }
        return List.copyOf(lines);
    }

    /**
     * 中文：按属性内容像素宽度压缩原生来源路径，保留首尾以便诊断与回写。
     *
     * English: Compacts a native source path to the property-content pixel width
     * while retaining both ends for diagnostics and round-tripping.
     */
    public static String compactPath(String path, int contentWidth) {
        Font font = Minecraft.getInstance().font;
        int maxWidth = Math.max(
                120,
                contentWidth - 32);
        if (path.isEmpty()
                || font.width(path) <= maxWidth) {
            return path;
        }
        int ellipsisWidth = font.width("\u2026");
        int available = Math.max(
                1,
                maxWidth - ellipsisWidth);
        int half = Math.max(
                8,
                available / 2);
        String leading = font.plainSubstrByWidth(
                path,
                half);
        String trailing = font.plainSubstrByWidth(
                new StringBuilder(path)
                        .reverse()
                        .toString(),
                half);
        return leading
                + "\u2026"
                + new StringBuilder(trailing)
                        .reverse();
    }

    public static int placeSectionAction(
            PropertyCanvas canvas,
            ActionButton action,
            int sectionTop,
            int preferredWidth) {
        int usableWidth = Math.max(
                1,
                canvas.width() - CONTENT_INSET * 2);
        if (usableWidth >= 400) {
            canvas.placeButton(
                    action,
                    canvas.width() - CONTENT_INSET - preferredWidth,
                    sectionTop - 7,
                    preferredWidth);
            return sectionTop + 16;
        }
        canvas.placeButton(
                action,
                CONTENT_INSET,
                sectionTop + 14,
                usableWidth);
        return sectionTop + 42;
    }

    public static ActionButton button(String translationKey) {
        return new ActionButton(Component.translatable(translationKey));
    }
}
