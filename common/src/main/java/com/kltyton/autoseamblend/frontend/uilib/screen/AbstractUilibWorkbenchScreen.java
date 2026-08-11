package com.kltyton.autoseamblend.frontend.uilib.screen;

import com.daqem.uilib.client.gui.AbstractScreen;
import com.daqem.uilib.client.gui.component.TextComponent;
import com.daqem.uilib.client.gui.component.scroll.ScrollPanelComponent;
import com.daqem.uilib.client.gui.text.Text;
import com.daqem.uilib.api.client.gui.component.IComponent;
import com.kltyton.autoseamblend.frontend.uilib.component.TextBoxComponent;
import com.kltyton.autoseamblend.frontend.uilib.event.DirectPointerHandler;
import com.kltyton.autoseamblend.frontend.uilib.event.MouseReleaseHandler;
import com.kltyton.autoseamblend.frontend.uilib.layout.shell.WorkbenchChromeLayout.Action;
import com.kltyton.autoseamblend.frontend.uilib.layout.shell.WorkbenchLayoutHost;
import com.kltyton.autoseamblend.frontend.uilib.widget.ActionButton;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

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
    private int footerTop = Integer.MIN_VALUE;
    private int footerBottom = Integer.MIN_VALUE;
    private TextBoxComponent focusedInput;

    protected AbstractUilibWorkbenchScreen(
            Component title) {
        super(title);
    }

    @Override
    public final void init() {
        beforeWorkbenchClear();
        for (IComponent<?> component
                : List.copyOf(getComponents())) {
            removeComponent(component);
        }
        clearWidgets();
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
            double mouseX,
            double mouseY,
            int button) {
        if (previewMouseClicked(
                mouseX,
                mouseY,
                button)) {
            return true;
        }
        if (insideFooterRegion(mouseY)) {
            // 中文：footer 区域先路由并消费；正文子树（含被视口裁掉、几何延伸到
            // footer 下方的滚动行）绝不接收 footer 区域点击。
            // English: The footer band routes first and always consumes; body
            // subtrees (including clipped scroll rows that geometrically extend
            // under the footer) never receive footer-area clicks.
            footerBandClick(
                    getComponents(),
                    mouseX,
                    mouseY,
                    button);
            return true;
        }
        if (routeInputClick(
                getComponents(),
                mouseX,
                mouseY,
                button)) {
            return true;
        }
        if (topmostClick(
                List.copyOf(getComponents()),
                mouseX,
                mouseY,
                button)) {
            return true;
        }
        setFocusedInput(null);
        return super.mouseClicked(
                mouseX,
                mouseY,
                button);
    }

    @Override
    public final boolean keyPressed(
            int keyCode,
            int scanCode,
            int modifiers) {
        if (focusedInput != null
                && focusedInput.keyPressed(
                        keyCode,
                        scanCode,
                        modifiers)) {
            return true;
        }
        return super.keyPressed(
                keyCode,
                scanCode,
                modifiers);
    }

    @Override
    public final boolean charTyped(
            char codePoint,
            int modifiers) {
        if (focusedInput != null
                && focusedInput.charTyped(
                        codePoint,
                        modifiers)) {
            return true;
        }
        return super.charTyped(
                codePoint,
                modifiers);
    }

    private boolean routeInputClick(
            List<IComponent<?>> components,
            double mouseX,
            double mouseY,
            int button) {
        for (int index = components.size() - 1;
                index >= 0;
                index--) {
            if (routeInputClick(
                    components.get(index),
                    mouseX,
                    mouseY,
                    button)) {
                return true;
            }
        }
        return false;
    }

    private boolean routeInputClick(
            IComponent<?> component,
            double mouseX,
            double mouseY,
            int button) {
        if (component instanceof TextBoxComponent input
                && input.mouseClicked(
                        mouseX,
                        mouseY,
                        button)) {
            setFocusedInput(input);
            return true;
        }
        if (component instanceof ScrollPanelComponent panel) {
            if (!panel.isTotalHovered(mouseX, mouseY)) {
                return false;
            }
            boolean handled = panel.getScrollContentComponent()
                    .map(content -> routeInputClick(
                            content.getChildren(),
                            mouseX,
                            mouseY,
                            button))
                    .orElse(false);
            if (handled) {
                return true;
            }
        }
        return routeInputClick(
                component.getChildren(),
                mouseX,
                mouseY,
                button);
    }

    private void setFocusedInput(
            TextBoxComponent input) {
        if (focusedInput != null
                && focusedInput != input) {
            focusedInput.setFocused(false);
        }
        focusedInput = input;
    }

    @Override
    public final boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double deltaX,
            double deltaY) {
        if (previewMouseDragged(
                mouseX,
                mouseY,
                button,
                deltaX,
                deltaY)) {
            return true;
        }
        if (topmostDrag(
                List.copyOf(getComponents()),
                mouseX,
                mouseY,
                button,
                deltaX,
                deltaY)) {
            return true;
        }
        return super.mouseDragged(
                mouseX,
                mouseY,
                button,
                deltaX,
                deltaY);
    }

    @Override
    public final boolean mouseReleased(
            double mouseX,
            double mouseY,
            int button) {
        if (previewMouseReleased(
                mouseX,
                mouseY,
                button)) {
            return true;
        }
        if (topmostRelease(
                List.copyOf(getComponents()),
                mouseX,
                mouseY,
                button)) {
            return true;
        }
        return super.mouseReleased(
                mouseX,
                mouseY,
                button);
    }

    @Override
    public final boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double verticalAmount) {
        if (topmostScroll(
                List.copyOf(getComponents()),
                mouseX,
                mouseY,
                verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(
                mouseX,
                mouseY,
                verticalAmount);
    }

    /**
     * 中文：记录 footer 区域，使 footer 点击永远先于正文被处理。
     *
     * English: Records the footer band so footer clicks are always routed
     * before the body.
     */
    protected final void setFooterRegion(
            int top,
            int bottom) {
        footerTop = top;
        footerBottom = Math.max(top, bottom);
    }

    private boolean insideFooterRegion(
            double mouseY) {
        return footerBottom > footerTop
                && mouseY >= footerTop
                && mouseY < footerBottom;
    }

    /**
     * 中文：UILib 0.3.6 的 AbstractScreen 按组件插入顺序（底层优先）递归分发指针事件，
     * 与“后添加者绘制在上层”的 z-order 相反，且命中测试不遵守 scissor 裁剪；
     * 被视口裁掉的滚动行会几何延伸到 footer 下方并抢先命中。这里改为后添加者
     * （上层）优先、组件自身先于子节点、一旦 handled 立即停止，并从 footer 区域
     * 中排除正文子树。
     *
     * English: UILib 0.3.6 AbstractScreen dispatches pointer events in insertion
     * order (bottom-first), which is the reverse of its paint z-order, and its
     * hit test ignores scissor clipping, so clipped scroll rows geometrically
     * extend under the footer and win first. These helpers route topmost-first
     * (later-added components win), self before children, stopping at the first
     * handled event, and keep body subtrees out of the footer band.
     */
    private boolean footerBandClick(
            List<IComponent<?>> components,
            double mouseX,
            double mouseY,
            int button) {
        for (int index = components.size() - 1;
                index >= 0;
                index--) {
            IComponent<?> component =
                    components.get(index);
            if (component.getTotalY() >= footerBottom
                    || component.getTotalY()
                                    + component.getHeight()
                            <= footerTop) {
                continue;
            }
            if (component.isClicked(
                            mouseX,
                            mouseY,
                            button)) {
                component.preformOnClickEvent(
                        mouseX,
                        mouseY,
                        button);
                return true;
            }
        }
        return false;
    }

    static boolean topmostClick(
            List<IComponent<?>> components,
            double mouseX,
            double mouseY,
            int button) {
        for (int index = components.size() - 1;
                index >= 0;
                index--) {
            if (topmostClick(
                    components.get(index),
                    mouseX,
                    mouseY,
                    button)) {
                return true;
            }
        }
        return false;
    }

    private static boolean topmostClick(
            IComponent<?> component,
            double mouseX,
            double mouseY,
            int button) {
        if (handlesDirectClick(component)
                && component.isClicked(
                mouseX,
                mouseY,
                button)) {
            component.preformOnClickEvent(
                    mouseX,
                    mouseY,
                    button);
            return true;
        }
        if (component instanceof ScrollPanelComponent panel) {
            if (!panel.isTotalHovered(mouseX, mouseY)) {
                return false;
            }
            boolean handled = panel.getScrollContentComponent()
                    .map(content -> topmostClick(
                            content.getChildren(),
                            mouseX,
                            mouseY,
                            button))
                    .orElse(false);
            if (handled) {
                return true;
            }
        }
        return topmostClick(
                component.getChildren(),
                mouseX,
                mouseY,
                button);
    }

    private static boolean topmostDrag(
            List<IComponent<?>> components,
            double mouseX,
            double mouseY,
            int button,
            double deltaX,
            double deltaY) {
        for (int index = components.size() - 1;
                index >= 0;
                index--) {
            if (topmostDrag(
                    components.get(index),
                    mouseX,
                    mouseY,
                    button,
                    deltaX,
                    deltaY)) {
                return true;
            }
        }
        return false;
    }

    private static boolean topmostDrag(
            IComponent<?> component,
            double mouseX,
            double mouseY,
            int button,
            double deltaX,
            double deltaY) {
        if (handlesDirectDrag(component)
                && component.isDragged(
                mouseX,
                mouseY,
                button,
                deltaX,
                deltaY)) {
            component.preformOnDragEvent(
                    mouseX,
                    mouseY,
                    button,
                    deltaX,
                    deltaY);
            return true;
        }
        if (component instanceof ScrollPanelComponent panel) {
            boolean handled = panel.getScrollContentComponent()
                    .map(content -> topmostDrag(
                            content.getChildren(),
                            mouseX,
                            mouseY,
                            button,
                            deltaX,
                            deltaY))
                    .orElse(false);
            if (handled) {
                return true;
            }
        }
        return topmostDrag(
                component.getChildren(),
                mouseX,
                mouseY,
                button,
                deltaX,
                deltaY);
    }

    private static boolean topmostRelease(
            List<IComponent<?>> components,
            double mouseX,
            double mouseY,
            int button) {
        for (int index = components.size() - 1;
                index >= 0;
                index--) {
            if (topmostRelease(
                    components.get(index),
                    mouseX,
                    mouseY,
                    button)) {
                return true;
            }
        }
        return false;
    }

    private static boolean topmostRelease(
            IComponent<?> component,
            double mouseX,
            double mouseY,
            int button) {
        if (component instanceof MouseReleaseHandler handler
                && handler.preformOnMouseReleaseEvent(
                        mouseX,
                        mouseY,
                        button)) {
            return true;
        }
        if (component instanceof ScrollPanelComponent panel) {
            boolean handled = panel.getScrollContentComponent()
                    .map(content -> topmostRelease(
                            content.getChildren(),
                            mouseX,
                            mouseY,
                            button))
                    .orElse(false);
            if (handled) {
                return true;
            }
        }
        return topmostRelease(
                component.getChildren(),
                mouseX,
                mouseY,
                button);
    }

    private static boolean topmostScroll(
            List<IComponent<?>> components,
            double mouseX,
            double mouseY,
            double verticalAmount) {
        for (int index = components.size() - 1;
                index >= 0;
                index--) {
            if (topmostScroll(
                    components.get(index),
                    mouseX,
                    mouseY,
                    verticalAmount)) {
                return true;
            }
        }
        return false;
    }

    private static boolean topmostScroll(
            IComponent<?> component,
            double mouseX,
            double mouseY,
            double verticalAmount) {
        if (handlesDirectScroll(component)
                && component.isScrolled(
                mouseX,
                mouseY,
                verticalAmount)) {
            component.preformOnScrollEvent(
                    mouseX,
                    mouseY,
                    verticalAmount);
            return true;
        }
        if (component instanceof ScrollPanelComponent panel) {
            if (!panel.isTotalHovered(mouseX, mouseY)) {
                return false;
            }
            boolean handled = panel.getScrollContentComponent()
                    .map(content -> topmostScroll(
                            content.getChildren(),
                            mouseX,
                            mouseY,
                            verticalAmount))
                    .orElse(false);
            if (handled) {
                return true;
            }
        }
        return topmostScroll(
                component.getChildren(),
                mouseX,
                mouseY,
                verticalAmount);
    }

    private static boolean handlesDirectClick(
            IComponent<?> component) {
        return component.getOnClickEvent() != null
                || component instanceof DirectPointerHandler handler
                        && handler.handlesDirectClick();
    }

    private static boolean handlesDirectDrag(
            IComponent<?> component) {
        return component.getOnDragEvent() != null
                || component instanceof DirectPointerHandler handler
                        && handler.handlesDirectDrag();
    }

    private static boolean handlesDirectScroll(
            IComponent<?> component) {
        return component.getOnScrollEvent() != null
                || component instanceof DirectPointerHandler handler
                        && handler.handlesDirectScroll();
    }

    @Override
    public final int width() {
        return width;
    }

    @Override
    public final int height() {
        return height;
    }

    /**
     * 中文：按 DESIGN.md 保留 Minecraft 原版模糊/压暗背景，覆盖 UILib 默认渐变。
     *
     * English:
     * Keeps the vanilla Minecraft blur/darken backdrop per DESIGN.md, overriding the
     * UILib default gradient.
     */
    @Override
    public void renderBackground(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float delta) {
        if (WorkbenchBackdropPolicy.selected()
                == WorkbenchBackdropPolicy.Kind.VANILLA_BLUR_DARKEN) {
            renderVanillaBackdrop(graphics);
            return;
        }
        super.renderBackground(
                graphics,
                mouseX,
                mouseY,
                delta);
    }

    /** 中文：1.20.1 无 renderTransparentBackground；复刻原版压暗/脏背景语义。 / English: 1.20.1 has no renderTransparentBackground; replicates the vanilla darken/dirt backdrop. */
    private void renderVanillaBackdrop(
            GuiGraphics graphics) {
        if (Minecraft.getInstance().level != null) {
            graphics.fillGradient(
                    0,
                    0,
                    width,
                    height,
                    -1072689136,
                    -804253680);
        } else {
            renderDirtBackground(graphics);
        }
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
                new Text(
                        Minecraft.getInstance().font,
                        text.copy().withStyle(style -> style.withColor(TextColor.fromRgb(color)))));
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
        addComponent(button);
    }

    @Override
    public final void addWidget(IComponent widget) {
        addComponent(widget);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public final void addComponent(IComponent component) {
        super.addComponent(component);
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
            double mouseX,
            double mouseY,
            int button) {
        return false;
    }

    protected boolean previewMouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double deltaX,
            double deltaY) {
        return false;
    }

    protected boolean previewMouseReleased(
            double mouseX,
            double mouseY,
            int button) {
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
