package com.kltyton.autoseamblend.frontend.uilib.widget;

import com.daqem.uilib.api.client.gui.component.scroll.ScrollOrientation;
import com.daqem.uilib.client.gui.component.scroll.ScrollContentComponent;
import com.daqem.uilib.client.gui.component.scroll.ScrollPanelComponent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 中文：与 26.1.2 UILib 20.1.4 一致的原版风格滚动面板：不再使用 UILib 自定义滚动条，
 * 仅在内容溢出时绘制 6px 原版 scroller/scroller_background，并支持拖拽。
 *
 * English:
 * Vanilla-style scroll panel matching 26.1.2 UILib 20.1.4: the UILib custom scrollbar
 * is dropped, the 6px vanilla scroller/scroller_background is drawn only when content
 * overflows, and dragging is supported.
 */
public final class VanillaScrollPanelComponent
        extends ScrollPanelComponent {
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int MIN_SCROLLER_HEIGHT = 32;
    private static final ResourceLocation SCROLLER =
            ResourceLocation.withDefaultNamespace(
                    "widget/scroller");
    private static final ResourceLocation SCROLLER_BACKGROUND =
            ResourceLocation.withDefaultNamespace(
                    "widget/scroller_background");

    private boolean dragging;
    private double dragStartY;
    private double dragStartScroll;

    public VanillaScrollPanelComponent(
            int x,
            int y,
            int width,
            int height,
            ScrollContentComponent content) {
        super(
                null,
                x,
                y,
                width,
                height,
                ScrollOrientation.VERTICAL,
                content,
                null);
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float delta) {
        super.render(
                graphics,
                mouseX,
                mouseY,
                delta);
        renderVanillaScrollbar(graphics);
    }

    @Override
    public boolean preformOnClickEvent(
            double mouseX,
            double mouseY,
            int button) {
        if (overScroller(mouseX, mouseY)) {
            dragging = true;
            dragStartY = mouseY;
            dragStartScroll =
                    currentScroll();
            return true;
        }
        return super.preformOnClickEvent(
                mouseX,
                mouseY,
                button);
    }

    @Override
    public boolean preformOnDragEvent(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY) {
        ScrollContentComponent content =
                getScrollContentComponent()
                        .orElse(null);
        if (dragging && content != null) {
            int viewport = getHeight();
            int maxScroll = Math.max(
                    0,
                    contentHeight() - viewport);
            int scrollerHeight =
                    scrollerHeight();
            double delta = mouseY - dragStartY;
            double scroll = dragStartScroll
                    + delta * maxScroll
                            / Math.max(
                                    1,
                                    viewport
                                            - scrollerHeight);
            content.setY(-(int) Mth.clamp(
                    scroll,
                    0,
                    maxScroll));
            return true;
        }
        return super.preformOnDragEvent(
                mouseX,
                mouseY,
                button,
                dragX,
                dragY);
    }

    @Override
    public boolean preformOnMouseReleaseEvent(
            double mouseX,
            double mouseY,
            int button) {
        dragging = false;
        return super.preformOnMouseReleaseEvent(
                mouseX,
                mouseY,
                button);
    }

    /**
     * 中文：UILib 9.0.0 只在 startRenderable() 里注册滚轮回调，而工作台每次重建都会
     * 新建滚动面板且不再调用 startRenderable，导致滚轮永远失效。这里直接在视口悬停
     * 时按真实滚轮增量更新内容偏移并消费事件。
     *
     * English: UILib 9.0.0 wires the wheel only in startRenderable(), which is
     * skipped for panels created by workbench rebuilds, so the wheel never
     * scrolls. This override scrolls with the real wheel delta while the
     * pointer hovers the viewport and consumes the event.
     */
    @Override
    public boolean preformOnScrollEvent(
            double mouseX,
            double mouseY,
            double amountX,
            double amountY) {
        if (amountY == 0.0D
                || !isTotalHovered(
                        mouseX,
                        mouseY)) {
            return super.preformOnScrollEvent(
                    mouseX,
                    mouseY,
                    amountX,
                    amountY);
        }
        ScrollContentComponent content =
                getScrollContentComponent()
                        .orElse(null);
        int viewport = getHeight();
        if (content == null
                || content.getHeight() <= viewport) {
            return super.preformOnScrollEvent(
                    mouseX,
                    mouseY,
                    amountX,
                    amountY);
        }
        int maxScroll = content.getHeight()
                - viewport;
        int next = (int) Mth.clamp(
                content.getY()
                        + amountY * getScrollSpeed(),
                -maxScroll,
                0);
        if (next == content.getY()) {
            return super.preformOnScrollEvent(
                    mouseX,
                    mouseY,
                    amountX,
                    amountY);
        }
        content.setY(next);
        return true;
    }

    private void renderVanillaScrollbar(
            GuiGraphics graphics) {
        ScrollContentComponent content =
                getScrollContentComponent()
                        .orElse(null);
        int viewport = getHeight();
        if (content == null
                || content.getHeight() <= viewport) {
            return;
        }
        int maxScroll = content.getHeight()
                - viewport;
        int scrollerHeight =
                scrollerHeight();
        int barX = getWidth()
                - SCROLLBAR_WIDTH;
        int barY = maxScroll <= 0
                ? 0
                : (int) ((double) currentScroll()
                        * (viewport
                                - scrollerHeight)
                        / maxScroll);
        // 中文：blitSprite 使用屏幕绝对坐标；先把 pose 归一到屏幕原点（减去当前平移），
        // 再按面板绝对位置绘制，使滚动条与 overScroller 的绝对命中区域一致，无论
        // UILib 祖先平移状态如何都落在面板右侧可见列。thumb 位置仍由
        // content.getY()/maxScroll 单一来源计算。
        //
        // English: blitSprite takes absolute screen coordinates; normalize the
        // pose to the screen origin (removing the current translation) before
        // drawing at the panel's absolute position, so the visible scrollbar
        // matches overScroller's absolute hit area regardless of UILib ancestor
        // translations. The thumb position still comes from the single
        // content.getY()/maxScroll source.
        PoseStack pose = graphics.pose();
        float originX = pose.last().pose().m30();
        float originY = pose.last().pose().m31();
        pose.pushPose();
        pose.translate(
                -originX,
                -originY,
                0.0F);
        graphics.blitSprite(
                SCROLLER_BACKGROUND,
                getTotalX() + barX,
                getTotalY(),
                SCROLLBAR_WIDTH,
                viewport);
        graphics.blitSprite(
                SCROLLER,
                getTotalX() + barX,
                getTotalY() + barY,
                SCROLLBAR_WIDTH,
                scrollerHeight);
        pose.popPose();
    }

    private boolean overScroller(
            double mouseX,
            double mouseY) {
        ScrollContentComponent content =
                getScrollContentComponent()
                        .orElse(null);
        int viewport = getHeight();
        if (content == null
                || content.getHeight() <= viewport) {
            return false;
        }
        int maxScroll = content.getHeight()
                - viewport;
        int scrollerHeight =
                scrollerHeight();
        int barX = getWidth()
                - SCROLLBAR_WIDTH;
        int barY = maxScroll <= 0
                ? 0
                : (int) ((double) currentScroll()
                        * (viewport
                                - scrollerHeight)
                        / maxScroll);
        return mouseX >= getTotalX() + barX
                && mouseX
                        <= getTotalX()
                                + barX
                                + SCROLLBAR_WIDTH
                && mouseY >= getTotalY() + barY
                && mouseY
                        <= getTotalY()
                                + barY
                                + scrollerHeight;
    }

    private int contentHeight() {
        return getScrollContentComponent()
                .map(ScrollContentComponent::getHeight)
                .orElse(0);
    }

    private int currentScroll() {
        return getScrollContentComponent()
                .map(content -> Math.max(
                        0,
                        -content.getY()))
                .orElse(0);
    }

    private int scrollerHeight() {
        int viewport = getHeight();
        int contentHeight = contentHeight();
        if (contentHeight <= 0) {
            return viewport;
        }
        return Mth.clamp(
                viewport * viewport
                        / contentHeight,
                MIN_SCROLLER_HEIGHT,
                Math.max(
                        MIN_SCROLLER_HEIGHT,
                        viewport - 8));
    }
}
