package com.kltyton.autoseamblend.frontend.uilib.layout.shell;

import com.daqem.uilib.api.client.gui.component.IComponent;
import com.daqem.uilib.api.client.gui.component.IComponent;
import com.kltyton.autoseamblend.frontend.uilib.widget.ActionButton;
import net.minecraft.network.chat.Component;

/**
 * 中文：定义领域布局向工作台壳层提交 UILib 组件所需的最小接口。
 *
 * English: Defines the minimum shell boundary used by domain layouts to submit
 * UILib components to the workbench.
 */
public interface WorkbenchLayoutHost {
    int width();

    int height();

    boolean actionsEnabled();

    void addComponent(IComponent component);

    void addWidget(IComponent widget);

    void addText(
            Component text,
            int x,
            int y,
            int color);

    void placeButton(
            ActionButton button,
            int x,
            int y,
            int width);

    void rebuild();
}
