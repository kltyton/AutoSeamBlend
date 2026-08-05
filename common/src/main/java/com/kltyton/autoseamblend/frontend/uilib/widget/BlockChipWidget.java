package com.kltyton.autoseamblend.frontend.uilib.widget;

import com.daqem.uilib.gui.widget.ButtonWidget;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 中文：用真实物品图标、语言名和原生选择器文本表示一个可点击方块条目。
 *
 * English: Clickable block entry represented by a real item icon, localized
 * name, and native selector text.
 */
public class BlockChipWidget extends ButtonWidget {
    private final ItemStack icon;
    private final Component displayName;
    private final String detail;
    private final boolean removable;

    public BlockChipWidget(
            int width,
            ItemStack icon,
            Component displayName,
            String detail,
            boolean removable,
            Runnable action) {
        super(
                0,
                0,
                width,
                32,
                Component.translatable(
                        "gui.autoseamblend.property.block_chip",
                        displayName,
                        detail),
                ignored -> Objects.requireNonNull(
                                action,
                                "action")
                        .run());
        this.icon = Objects.requireNonNull(icon, "icon");
        this.displayName = Objects.requireNonNull(
                displayName,
                "displayName");
        this.detail = Objects.requireNonNull(detail, "detail");
        this.removable = removable;
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
        int background = isHoveredOrFocused()
                ? UilibWorkbenchTheme.SURFACE_RAISED
                : UilibWorkbenchTheme.SURFACE_PANEL;
        int border = isFocused()
                ? UilibWorkbenchTheme.FOCUS_RING
                : UilibWorkbenchTheme.BORDER_SUBTLE;
        graphics.fill(left, top, right, bottom, background);
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
        graphics.fakeItem(icon, left + 6, top + 8);
        graphics.textRenderer().accept(
                left + 28,
                top + 5,
                displayName);
        graphics.textRenderer().accept(
                left + 28,
                top + 18,
                Component.literal(detail)
                        .withColor(
                                UilibWorkbenchTheme.TEXT_SECONDARY));
        if (removable) {
            graphics.textRenderer().accept(
                    right - 14,
                    top + 10,
                    Component.literal("\u00d7")
                            .withColor(
                                    UilibWorkbenchTheme.STATUS_ERROR));
        }
    }
}
