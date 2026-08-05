package com.kltyton.autoseamblend.frontend.uilib.screen;

import com.daqem.uilib.gui.AbstractScreen;
import com.daqem.uilib.gui.component.text.TextComponent;
import com.kltyton.autoseamblend.frontend.uilib.layout.shell.WorkbenchChromeLayout.Action;
import com.kltyton.autoseamblend.frontend.uilib.layout.shell.WorkbenchLayoutHost;
import com.kltyton.autoseamblend.frontend.uilib.widget.ActionButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 中文：Fabric 与 NeoForge 工作台共享的 UILib Screen 生命周期、Host 提交和指针事件模板。
 *
 * English: Shared UILib screen lifecycle, host submission, and pointer-event
 * template for the Fabric and NeoForge workbenches.
 */
public abstract class AbstractUilibWorkbenchScreen
        extends AbstractScreen
        implements WorkbenchLayoutHost {
    private boolean initialized;
    private boolean rebuildPending;

    protected AbstractUilibWorkbenchScreen(
            Component title) {
        super(title);
    }

    @Override
    public final void init() {
        beforeWorkbenchClear();
        clear();
        afterWorkbenchClear();
        rebuildPending = false;
        initialized = true;
        assembleWorkbench();
        super.init();
    }

    @Override
    public final void tick() {
        super.tick();
        beforeWorkbenchRebuildTick();
        if (rebuildPending) {
            init();
        }
        afterWorkbenchRebuildTick();
    }

    @Override
    public final boolean isPauseScreen() {
        return false;
    }

    @Override
    public final void mouseMoved(
            double mouseX,
            double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        previewMouseMoved(mouseX, mouseY);
    }

    @Override
    public final boolean mouseClicked(
            MouseButtonEvent event,
            boolean doubleClick) {
        if (previewMouseClicked(event, doubleClick)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public final boolean mouseDragged(
            MouseButtonEvent event,
            double deltaX,
            double deltaY) {
        if (previewMouseDragged(
                event,
                deltaX,
                deltaY)) {
            return true;
        }
        return super.mouseDragged(
                event,
                deltaX,
                deltaY);
    }

    @Override
    public final boolean mouseReleased(
            MouseButtonEvent event) {
        if (previewMouseReleased(event)) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public final int width() {
        return width;
    }

    @Override
    public final int height() {
        return height;
    }

    @Override
    public final void addText(
            Component text,
            int x,
            int y,
            int color) {
        TextComponent component = new TextComponent(
                0,
                0,
                text.copy().withColor(color));
        component.setX(x);
        component.setY(y);
        addComponent(component);
    }

    @Override
    public final void placeButton(
            ActionButton button,
            int x,
            int y,
            int width) {
        button.setX(x);
        button.setY(y);
        button.setWidth(Math.max(1, width));
        addWidget(button);
    }

    @Override
    public final void rebuild() {
        if (initialized) {
            rebuildPending = true;
        }
    }

    protected void beforeWorkbenchClear() {}

    protected void afterWorkbenchClear() {}

    protected abstract void assembleWorkbench();

    protected void beforeWorkbenchRebuildTick() {}

    protected void afterWorkbenchRebuildTick() {}

    protected void previewMouseMoved(
            double mouseX,
            double mouseY) {}

    protected boolean previewMouseClicked(
            MouseButtonEvent event,
            boolean doubleClick) {
        return false;
    }

    protected boolean previewMouseDragged(
            MouseButtonEvent event,
            double deltaX,
            double deltaY) {
        return false;
    }

    protected boolean previewMouseReleased(
            MouseButtonEvent event) {
        return false;
    }

    protected static Action action(
            String translationKey,
            Runnable execute,
            boolean enabled) {
        return new Action(
                Component.translatable(translationKey),
                execute,
                enabled);
    }
}
