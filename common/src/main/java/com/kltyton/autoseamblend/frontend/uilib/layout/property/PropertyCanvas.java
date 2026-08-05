package com.kltyton.autoseamblend.frontend.uilib.layout.property;

import com.daqem.uilib.api.widget.IWidget;
import com.daqem.uilib.gui.component.text.TextComponent;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import com.kltyton.autoseamblend.frontend.uilib.component.PanelComponent;
import com.kltyton.autoseamblend.frontend.uilib.widget.ActionButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/**
 * 中文：把属性页相对坐标内容封装为滚动容器中的单一 UILib 组件。
 *
 * English: Wraps relative property-page content as one UILib component inside
 * the scroll viewport.
 */
public final class PropertyCanvas {
    private final PanelComponent root;
    private int contentBottom;

    public PropertyCanvas(int width, int height) {
        root = new PanelComponent(
                0,
                0,
                width,
                height,
                UilibWorkbenchTheme.SURFACE_PANEL);
    }

    public PanelComponent root() {
        return root;
    }

    public int width() {
        return root.getWidth();
    }

    public void addText(Component text, int x, int y, int color) {
        TextComponent component = new TextComponent(
                0,
                0,
                text.copy().withColor(color));
        component.setX(x);
        component.setY(y);
        root.addComponent(component);
        contentBottom = Math.max(
                contentBottom,
                y + Minecraft.getInstance().font.lineHeight);
    }

    public void placeButton(ActionButton button, int x, int y, int width) {
        button.setX(x);
        button.setY(y);
        button.setWidth(Math.max(1, width));
        root.addWidget(button);
        contentBottom = Math.max(
                contentBottom,
                y + button.getHeight());
    }

    public void addWidget(IWidget widget) {
        root.addWidget(widget);
        if (widget instanceof AbstractWidget abstractWidget) {
            contentBottom = Math.max(
                    contentBottom,
                    abstractWidget.getY() + abstractWidget.getHeight());
        }
    }

    public void finish(int minimumHeight, int bottomInset) {
        root.setHeight(Math.max(minimumHeight, contentBottom + bottomInset));
    }
}
