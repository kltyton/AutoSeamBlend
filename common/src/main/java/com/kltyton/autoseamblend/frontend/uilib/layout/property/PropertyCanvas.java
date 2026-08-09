package com.kltyton.autoseamblend.frontend.uilib.layout.property;

import com.daqem.uilib.api.client.gui.component.IComponent;
import com.daqem.uilib.client.gui.component.TextComponent;
import com.daqem.uilib.client.gui.text.Text;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import com.kltyton.autoseamblend.frontend.uilib.component.PanelComponent;
import com.kltyton.autoseamblend.frontend.uilib.widget.ActionButton;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 中文：把属性页相对坐标内容封装为滚动容器中的单一 UILib 组件。
 *
 * English: Wraps relative property-page content as one UILib component inside
 * the scroll viewport.
 */
public final class PropertyCanvas {
    /**
     * 中文：右侧为 VanillaScrollPanelComponent 的 6px 原版滚动条预留列（含 2px 间隙），
     * 防止内容全宽填充把滚动条列盖住；与 target 列表行缩进策略一致。
     *
     * English: Reserves the 6px vanilla scrollbar column at the panel's right
     * edge (plus a 2px gap) so a full-width content fill never covers the
     * scrollbar; matches the target-list row inset policy.
     */
    private static final int SCROLLBAR_COLUMN = 8;

    private final PanelComponent root;
    private int contentBottom;

    public PropertyCanvas(int width, int height) {
        root = new PanelComponent(
                0,
                0,
                Math.max(1, width - SCROLLBAR_COLUMN),
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
                new Text(
                        Minecraft.getInstance().font,
                        text.copy().withColor(color)));
        component.setX(x);
        component.setY(y);
        root.addChild(component);
        contentBottom = Math.max(
                contentBottom,
                y + Minecraft.getInstance().font.lineHeight);
    }

    public void placeButton(ActionButton button, int x, int y, int width) {
        button.setX(x);
        button.setY(y);
        button.setWidth(Math.max(1, width));
        root.addChild(button);
        contentBottom = Math.max(
                contentBottom,
                y + button.getHeight());
    }

    public void addChild(IComponent widget) {
        root.addChild(widget);
        contentBottom = Math.max(
                contentBottom,
                widget.getY() + widget.getHeight());
    }

    public void finish(int minimumHeight, int bottomInset) {
        root.setHeight(Math.max(minimumHeight, contentBottom + bottomInset));
    }
}
