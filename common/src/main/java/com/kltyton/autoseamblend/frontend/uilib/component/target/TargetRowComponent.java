package com.kltyton.autoseamblend.frontend.uilib.component.target;

import com.kltyton.autoseamblend.frontend.uilib.component.AbstractAbsoluteComponent;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import com.kltyton.autoseamblend.frontend.layout.ThreeColumnActionGrid;
import com.kltyton.autoseamblend.frontend.model.TargetRowView;
import com.kltyton.autoseamblend.frontend.uilib.widget.ActionButton;
import com.kltyton.autoseamblend.frontend.uilib.widget.TargetRowWidget;
import java.util.Objects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** 中文：可单独展开三个可视编辑动作的目标行。 / English: Target row that expands exactly three visual editing actions. */
public final class TargetRowComponent
        extends AbstractAbsoluteComponent<TargetRowComponent> {
    private static final int ROW_HEIGHT = 42;
    private static final int DRAWER_HEIGHT = 32;

    public TargetRowComponent(
            int width,
            TargetRowView row,
            boolean expanded,
            Runnable toggle,
            Runnable preview,
            Runnable paint,
            Runnable properties) {
        super(
                0,
                0,
                width,
                expanded
                        ? ROW_HEIGHT + DRAWER_HEIGHT
                        : ROW_HEIGHT);
        Objects.requireNonNull(row, "row");
        TargetRowWidget rowButton =
                new TargetRowWidget(
                        width,
                        row,
                        toggle);
        rowButton.setExpanded(expanded);
        addChild(rowButton);
        if (expanded) {
            addDrawerButton(
                    8,
                    width,
                    0,
                    "gui.autoseamblend.action.preview",
                    preview,
                    row.previewEnabled());
            addDrawerButton(
                    8,
                    width,
                    1,
                    "gui.autoseamblend.action.edit_texture",
                    paint,
                    row.paintEnabled());
            addDrawerButton(
                    8,
                    width,
                    2,
                    "gui.autoseamblend.action.edit_properties",
                    properties,
                    row.propertiesEnabled());
        }
    }

    @Override
    protected void extractRenderState(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        if (getHeight() <= ROW_HEIGHT) {
            return;
        }
        int left = getTotalX();
        int top = getTotalY() + ROW_HEIGHT;
        graphics.fill(
                left,
                top,
                left + getWidth(),
                top + DRAWER_HEIGHT,
                UilibWorkbenchTheme.SURFACE_RAISED);
        graphics.hLine(
                left,
                left + getWidth() - 1,
                top,
                UilibWorkbenchTheme.BORDER_HIGHLIGHT);
        graphics.vLine(
                left,
                top,
                top + DRAWER_HEIGHT - 1,
                UilibWorkbenchTheme.BORDER_HIGHLIGHT);
        graphics.hLine(
                left,
                left + getWidth() - 1,
                top + DRAWER_HEIGHT - 1,
                UilibWorkbenchTheme.BORDER_SHADOW);
        graphics.vLine(
                left + getWidth() - 1,
                top,
                top + DRAWER_HEIGHT - 1,
                UilibWorkbenchTheme.BORDER_SHADOW);
    }

    private void addDrawerButton(
            int inset,
            int width,
            int index,
            String translationKey,
            Runnable action,
            boolean enabled) {
        int gap = 6;
        ThreeColumnActionGrid grid =
                ThreeColumnActionGrid.within(
                        0,
                        width,
                        inset,
                        gap);
        ActionButton button =
                new ActionButton(
                        Component.translatable(
                                translationKey));
        button.setX(grid.x(index));
        button.setY(ROW_HEIGHT + 6);
        button.setWidth(
                grid.width(index));
        button.setActive(enabled);
        button.setAction(
                Objects.requireNonNull(
                        action,
                        "action"));
        addChild(button);
    }
}
