package com.kltyton.autoseamblend.frontend.uilib.screen;

import com.daqem.uilib.client.gui.AbstractScreen;
import com.daqem.uilib.client.gui.component.TextComponent;
import com.daqem.uilib.client.gui.text.Text;
import com.daqem.uilib.api.client.gui.component.IComponent;
import com.kltyton.autoseamblend.frontend.uilib.layout.shell.WorkbenchChromeLayout.Action;
import com.kltyton.autoseamblend.frontend.uilib.layout.shell.WorkbenchLayoutHost;
import com.kltyton.autoseamblend.frontend.uilib.widget.ActionButton;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
    private int footerTop = Integer.MIN_VALUE;
    private int footerBottom = Integer.MIN_VALUE;

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
        if (topmostClick(
                List.copyOf(getComponents()),
                mouseX,
                mouseY,
                button)) {
            return true;
        }
        return super.mouseClicked(
                mouseX,
                mouseY,
                button);
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
            double horizontalAmount,
            double verticalAmount) {
        if (topmostScroll(
                List.copyOf(getComponents()),
                mouseX,
                mouseY,
                horizontalAmount,
                verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(
                mouseX,
                mouseY,
                horizontalAmount,
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
     * 中文：UILib 9.0.0 的 AbstractScreen 按组件插入顺序（底层优先）递归分发指针事件，
     * 与“后添加者绘制在上层”的 z-order 相反，且命中测试不遵守 scissor 裁剪；
     * 被视口裁掉的滚动行会几何延伸到 footer 下方并抢先命中。这里改为后添加者
     * （上层）优先、组件自身先于子节点、一旦 handled 立即停止，并从 footer 区域
     * 中排除正文子树。
     *
     * English: UILib 9.0.0 AbstractScreen dispatches pointer events in insertion
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
            if (component.preformOnClickEvent(
                    mouseX,
                    mouseY,
                    button)) {
                return true;
            }
        }
        return false;
    }

    private static boolean topmostClick(
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
        if (component.preformOnClickEvent(
                mouseX,
                mouseY,
                button)) {
            return true;
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
        if (component.preformOnDragEvent(
                mouseX,
                mouseY,
                button,
                deltaX,
                deltaY)) {
            return true;
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
        if (component.preformOnMouseReleaseEvent(
                mouseX,
                mouseY,
                button)) {
            return true;
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
            double horizontalAmount,
            double verticalAmount) {
        for (int index = components.size() - 1;
                index >= 0;
                index--) {
            if (topmostScroll(
                    components.get(index),
                    mouseX,
                    mouseY,
                    horizontalAmount,
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
            double horizontalAmount,
            double verticalAmount) {
        if (component.preformOnScrollEvent(
                mouseX,
                mouseY,
                horizontalAmount,
                verticalAmount)) {
            return true;
        }
        return topmostScroll(
                component.getChildren(),
                mouseX,
                mouseY,
                horizontalAmount,
                verticalAmount);
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
            renderTransparentBackground(graphics);
            return;
        }
        super.renderBackground(
                graphics,
                mouseX,
                mouseY,
                delta);
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
                        text.copy().withColor(color)));
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
