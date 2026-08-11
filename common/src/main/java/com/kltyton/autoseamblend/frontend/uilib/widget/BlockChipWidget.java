package com.kltyton.autoseamblend.frontend.uilib.widget;

import com.daqem.uilib.client.gui.component.ButtonComponent;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;

/**
 * 中文：用真实物品图标、语言名和原生选择器文本表示一个可点击方块条目。
 *
 * English: Clickable block entry represented by a real item icon, localized
 * name, and native selector text.
 */
public class BlockChipWidget extends AutoSeamBlendButton {
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
                32);
        bind(
                Component.translatable(
                        "gui.autoseamblend.property.block_chip",
                        displayName,
                        detail),
                () -> Objects.requireNonNull(
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

    public void setActive(boolean active) {
        setEnabled(active);
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float delta) {
        int width = getWidth();
        int height = getHeight();
        int background = isTotalHovered(mouseX, mouseY)
                || isFocused()
                ? UilibWorkbenchTheme.SURFACE_RAISED
                : UilibWorkbenchTheme.SURFACE_PANEL;
        int border = isFocused()
                ? UilibWorkbenchTheme.FOCUS_RING
                : UilibWorkbenchTheme.BORDER_SUBTLE;
        graphics.fill(0, 0, width, height, background);
        graphics.hLine(
                0,
                width - 1,
                0,
                border);
        graphics.hLine(
                0,
                width - 1,
                height - 1,
                border);
        graphics.vLine(
                0,
                0,
                height - 1,
                border);
        graphics.vLine(
                width - 1,
                0,
                height - 1,
                border);
        graphics.renderItem(icon, 6, 8);
        graphics.drawString(
                Minecraft.getInstance().font,
                displayName,
                28,
                5,
                UilibWorkbenchTheme.TEXT_PRIMARY);
        graphics.drawString(
                Minecraft.getInstance().font,
                Component.literal(detail)
                        .withStyle(style -> style.withColor(TextColor.fromRgb(UilibWorkbenchTheme.TEXT_SECONDARY))),
                28,
                18,
                UilibWorkbenchTheme.TEXT_SECONDARY);
        if (removable) {
            graphics.drawString(
                    Minecraft.getInstance().font,
                    Component.literal("\u00d7")
                            .withStyle(style -> style.withColor(TextColor.fromRgb(UilibWorkbenchTheme.STATUS_ERROR))),
                    width - 14,
                    10,
                    UilibWorkbenchTheme.STATUS_ERROR);
        }
    }
}
