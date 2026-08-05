package com.kltyton.autoseamblend.frontend.uilib.layout.property;

import com.kltyton.autoseamblend.frontend.uilib.widget.ActionButton;
import net.minecraft.network.chat.Component;

/**
 * 中文：属性编辑器共享的文本截断、区段动作和按钮构造规则。
 *
 * English: Shared property-editor rules for text compaction, section actions,
 * and translated button construction.
 */
public final class NativePropertyPanelLayout {
    private static final int CONTENT_INSET = 16;

    private NativePropertyPanelLayout() {}

    public static String compactSelector(String value) {
        int limit = 54;
        return value.length() <= limit
                ? value
                : value.substring(0, 26)
                        + '\u2026'
                        + value.substring(value.length() - 27);
    }

    /**
     * 中文：按属性内容宽度压缩原生来源路径，保留首尾以便诊断与回写。
     *
     * English: Compacts a native source path to the property-content width
     * while retaining both ends for diagnostics and round-tripping.
     */
    public static String compactPath(String path, int contentWidth) {
        int limit = Math.max(24, (contentWidth - 32) / 6);
        if (path.length() <= limit) {
            return path;
        }
        int leading = Math.max(8, limit / 2 - 1);
        int trailing = Math.max(8, limit - leading - 1);
        return path.substring(0, leading)
                + '\u2026'
                + path.substring(path.length() - trailing);
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
