package com.kltyton.autoseamblend.frontend.uilib.widget;

import com.daqem.uilib.gui.widget.ButtonWidget;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchMetrics;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import com.kltyton.autoseamblend.frontend.model.TargetRowView;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;

/** 中文：同时绘制方块图标、语言名、ID 与来源状态的整行按钮。 / English: Whole-row button drawing a block icon, localized name, id, and source state. */
public final class TargetRowWidget extends ButtonWidget {
    private static final int GRID =
            UilibWorkbenchMetrics.GRID;
    private static final int INNER_INSET = GRID * 2;
    private static final int COLUMN_GAP = GRID * 2;
    private static final int ICON_SIZE = GRID * 4;
    private static final int PRIMARY_MIN_WIDTH = GRID * 10;
    private static final int METADATA_MIN_WIDTH = GRID * 8;
    private static final int FIRST_LINE_OFFSET = GRID * 2;
    private static final int SECOND_LINE_OFFSET = GRID * 6;
    private final TargetRowView row;
    private boolean expanded;

    public TargetRowWidget(
            int width,
            TargetRowView row,
            Runnable action) {
        super(
                0,
                0,
                width,
                42,
                Component.translatable(
                        "gui.autoseamblend.target.row.narration",
                        row.displayName(),
                        row.entryId()),
                ignored -> Objects.requireNonNull(
                                action,
                                "action")
                        .run());
        this.row = Objects.requireNonNull(row, "row");
    }

    public void setExpanded(boolean value) {
        expanded = value;
    }

    @Override
    protected void extractContents(
            @NotNull GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        int left = getX();
        int top = getY();
        int right = left + getWidth();
        int bottom = top + getHeight();
        extractDefaultSprite(graphics);
        int border = isFocused()
                ? UilibWorkbenchTheme.FOCUS_RING
                : expanded
                        ? UilibWorkbenchTheme.ACCENT_PRIMARY
                        : 0;
        if (border != 0) {
            graphics.horizontalLine(
                    left,
                    right - 1,
                    top,
                    border);
            graphics.horizontalLine(
                    left,
                    right - 1,
                    bottom - 1,
                    border);
            graphics.verticalLine(
                    left,
                    top,
                    bottom - 1,
                    border);
            graphics.verticalLine(
                    right - 1,
                    top,
                    bottom - 1,
                    border);
        }
        Font font = Minecraft.getInstance().font;
        Component marker = Component.literal(
                        expanded ? "\u25b2" : "\u25bc")
                .withColor(
                        UilibWorkbenchTheme.TEXT_BUTTON_SECONDARY);
        int markerWidth = font.width(marker);
        int markerX = Math.max(
                left,
                right - INNER_INSET - markerWidth);
        int textRight = Math.max(
                left,
                markerX - COLUMN_GAP);
        boolean iconFits = right - left
                >= INNER_INSET * 2
                        + ICON_SIZE
                        + COLUMN_GAP
                        + markerWidth
                        + PRIMARY_MIN_WIDTH;
        int textLeft;
        if (iconFits) {
            int iconX = left + INNER_INSET;
            graphics.fakeItem(
                    row.icon(),
                    iconX,
                    top + (getHeight() - ICON_SIZE) / 2);
            textLeft = iconX + ICON_SIZE + COLUMN_GAP;
        } else {
            textLeft = Math.min(
                    textRight,
                    left + INNER_INSET);
        }
        int availableTextWidth = Math.max(
                0,
                textRight - textLeft);
        Component source = sourceLabel();
        Component family = Component.literal(
                        row.family().formatId())
                .withColor(
                        UilibWorkbenchTheme.TEXT_BUTTON_SECONDARY);
        int desiredMetadataWidth = Math.max(
                font.width(source),
                font.width(family));
        boolean showMetadata = availableTextWidth
                >= PRIMARY_MIN_WIDTH
                        + COLUMN_GAP
                        + METADATA_MIN_WIDTH;
        int metadataWidth = showMetadata
                ? Math.min(
                        desiredMetadataWidth,
                        availableTextWidth
                                - PRIMARY_MIN_WIDTH
                                - COLUMN_GAP)
                : 0;
        int primaryWidth = showMetadata
                ? availableTextWidth
                        - metadataWidth
                        - COLUMN_GAP
                : availableTextWidth;
        drawClipped(
                graphics,
                font,
                row.displayName()
                        .copy()
                        .withColor(
                                UilibWorkbenchTheme.TEXT_INVERSE),
                textLeft,
                top + FIRST_LINE_OFFSET,
                primaryWidth);
        drawClipped(
                graphics,
                font,
                Component.literal(row.entryId())
                        .withColor(
                                UilibWorkbenchTheme
                                        .TEXT_BUTTON_SECONDARY),
                textLeft,
                top + SECOND_LINE_OFFSET,
                primaryWidth);
        if (showMetadata) {
            int metadataLeft = textRight - metadataWidth;
            drawClipped(
                    graphics,
                    font,
                    source,
                    metadataLeft,
                    top + FIRST_LINE_OFFSET,
                    metadataWidth);
            drawClipped(
                    graphics,
                    font,
                    family,
                    metadataLeft,
                    top + SECOND_LINE_OFFSET,
                    metadataWidth);
        }
        drawClipped(
                graphics,
                font,
                marker,
                markerX,
                top + (getHeight() - font.lineHeight) / 2,
                Math.max(0, right - markerX));
    }

    /**
     * 中文：按实际像素宽度裁切长文本，使主信息、状态列和展开标记不互相覆盖。
     *
     * English:
     * Clips long text to its measured pixel width so primary content,
     * metadata, and the expansion marker never overlap.
     */
    private static void drawClipped(
            GuiGraphicsExtractor graphics,
            Font font,
            FormattedText value,
            int x,
            int y,
            int width) {
        if (width <= 0) {
            return;
        }
        FormattedText clipped = font.width(value) <= width
                ? value
                : font.substrByWidth(value, width);
        FormattedCharSequence visual =
                Language.getInstance()
                        .getVisualOrder(clipped);
        graphics.text(
                font,
                visual,
                x,
                y,
                UilibWorkbenchTheme.TEXT_INVERSE,
                false);
    }

    private Component sourceLabel() {
        String suffix;
        if (row.managed() && row.configured()) {
            suffix = "both";
        } else if (row.managed()) {
            suffix = "managed";
        } else {
            suffix = "config";
        }
        return Component.translatable(
                        "gui.autoseamblend.target.source."
                                + suffix)
                .withColor(
                        row.managed()
                                ? UilibWorkbenchTheme.STATUS_COMPLETION
                                : UilibWorkbenchTheme.STATUS_NATIVE);
    }
}
