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

/**
 * 中文：同时绘制方块图标、语言名、ID 与来源状态的整行按钮。
 * 未展开行按固定纵向基线（第 1 行标题、第 2 行 ID）与横向列边界（图标、文本、
 * config、engine、toggle）布局；每一列都按自身像素宽度截断，绝不越界叠压。
 *
 * English: Whole-row button drawing a block icon, localized name, id, and
 * source state. Unexpanded rows use fixed vertical baselines (title line 1,
 * id line 2) and horizontal columns (icon, text, config, engine, toggle);
 * every column clips to its measured pixel width and never overflows.
 */
public final class TargetRowWidget extends ButtonWidget {
    private static final int GRID =
            UilibWorkbenchMetrics.GRID;
    private static final int INNER_INSET = GRID * 2;
    private static final int COLUMN_GAP = GRID * 2;
    private static final int ICON_SIZE = GRID * 4;
    private static final int PRIMARY_MIN_WIDTH = GRID * 10;
    private static final int METADATA_MIN_WIDTH = GRID * 8;
    private static final int MARKER_INSET = GRID * 2;
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
        int markerLeft = Math.max(
                left,
                right - MARKER_INSET - markerWidth);
        int textRegionRight = Math.max(
                left,
                markerLeft - COLUMN_GAP);
        int regionWidth = Math.max(
                0,
                textRegionRight - left);
        Component source = sourceLabel();
        Component family = Component.literal(
                        row.family().formatId())
                .withColor(
                        UilibWorkbenchTheme.TEXT_BUTTON_SECONDARY);
        int desiredMetadataWidth = Math.max(
                font.width(source),
                font.width(family));
        boolean showMetadata = regionWidth
                >= PRIMARY_MIN_WIDTH
                        + COLUMN_GAP
                        + METADATA_MIN_WIDTH;
        int metadataWidth = showMetadata
                ? Math.min(
                        desiredMetadataWidth,
                        regionWidth
                                - PRIMARY_MIN_WIDTH
                                - COLUMN_GAP)
                : 0;
        int metadataLeft = showMetadata
                ? textRegionRight - metadataWidth
                : textRegionRight;
        int primaryRight = showMetadata
                ? metadataLeft - COLUMN_GAP
                : textRegionRight;
        boolean showIcon = primaryRight - left
                >= INNER_INSET
                        + ICON_SIZE
                        + COLUMN_GAP
                        + PRIMARY_MIN_WIDTH;
        int textLeft;
        if (showIcon) {
            int iconX = left + INNER_INSET;
            graphics.fakeItem(
                    row.icon(),
                    iconX,
                    top + (getHeight() - ICON_SIZE) / 2);
            textLeft = iconX + ICON_SIZE + COLUMN_GAP;
        } else {
            textLeft = Math.min(
                    primaryRight,
                    left + INNER_INSET);
        }
        int primaryWidth = Math.max(
                0,
                primaryRight - textLeft);
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
                markerLeft,
                top + (getHeight() - font.lineHeight) / 2,
                Math.max(0, right - markerLeft));
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
