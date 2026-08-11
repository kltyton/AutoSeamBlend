package com.kltyton.autoseamblend.frontend.uilib.widget;

import com.daqem.uilib.api.client.gui.component.IComponent;
import com.daqem.uilib.api.client.gui.component.scroll.ScrollOrientation;
import com.daqem.uilib.client.gui.component.scroll.ScrollContentComponent;
import com.daqem.uilib.client.gui.component.scroll.ScrollPanelComponent;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchMetrics;
import java.util.Objects;

/**
 * 中文：跨工作模式复用的纵向滚动正文；行按 4px 网格由 UILib 原生 ScrollContentComponent
 * 非重叠定位，面板原生 scissor 裁剪，滚轮与原版风格滚动条滚动，重建后恢复滚动位置。
 *
 * English:
 * Reusable vertical scroll body shared by the workbench modes; rows are positioned without
 * overlap on the 4px grid by the native UILib ScrollContentComponent, clipped by the panel's
 * native scissor, scrolled by the wheel and a vanilla-style scroll bar, and the offset
 * survives screen rebuilds.
 */
public final class ScrollBody {
    private final ScrollPanelComponent panel;
    private final ScrollContentComponent content;

    public ScrollBody(
            int x,
            int y,
            int width,
            int height,
            int contentSpacing) {
        content = new ScrollContentComponent(
                0,
                0,
                contentSpacing,
                ScrollOrientation.VERTICAL);
        panel = new VanillaScrollPanelComponent(
                x,
                y,
                Math.max(1, width),
                Math.max(1, height),
                content);
    }

    public ScrollPanelComponent panel() {
        return panel;
    }

    public void addChild(IComponent<?> child) {
        content.addChild(
                Objects.requireNonNull(
                        child,
                        "child"));
    }

    /**
     * 中文：返回当前滚动偏移，供外部 state holder 在重建前保存；旧 ScrollBody 随后销毁，
     * 新 ScrollBody 用返回值恢复位置。
     *
     * English:
     * Returns the current scroll offset so an external state holder can save it before the
     * rebuild; the old ScrollBody is then destroyed and the new ScrollBody restores the
     * position from the returned value.
     */
    public int capture() {
        int offset = content.getY();
        return offset;
    }

    /**
     * 中文：重建后按视口与内容尺寸裁剪并恢复外部保存的滚动偏移。
     *
     * English:
     * Clamps and restores an externally saved scroll offset after a rebuild,
     * using the current content and viewport sizes.
     */
    public void restore(int capturedOffset) {
        int restored = ScrollBodyLayout.clampOffset(
                capturedOffset,
                content.getHeight(),
                panel.getHeight());
        content.setY(restored);
    }
}
