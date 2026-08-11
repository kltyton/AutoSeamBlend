package com.kltyton.autoseamblend.frontend.uilib.screen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.daqem.uilib.api.client.gui.component.scroll.ScrollOrientation;
import com.daqem.uilib.client.gui.component.AbstractComponent;
import com.daqem.uilib.client.gui.component.scroll.ScrollContentComponent;
import com.kltyton.autoseamblend.frontend.uilib.event.DirectPointerHandler;
import com.kltyton.autoseamblend.frontend.uilib.widget.VanillaScrollPanelComponent;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.Test;

/** 中文：顶层面板不能吞掉滚动内容中的目标行点击。 / English: Top-level surfaces must not swallow target-row clicks inside scroll content. */
class PointerRoutingContractTest {

    @Test
    void inertSurfaceDoesNotBlockDirectRowInsideScrollContent() {
        ScrollContentComponent content = new ScrollContentComponent(
                0,
                0,
                4,
                ScrollOrientation.VERTICAL);
        ClickProbe row = new ClickProbe(80, 20);
        content.addChild(row);
        VanillaScrollPanelComponent panel = new VanillaScrollPanelComponent(
                10,
                10,
                100,
                60,
                content);
        InertSurface inertTopLayer = new InertSurface(
                10,
                10,
                100,
                60);

        assertTrue(AbstractUilibWorkbenchScreen.topmostClick(
                List.of(panel, inertTopLayer),
                20,
                20,
                0));
        assertTrue(row.clicked);
    }

    private static final class ClickProbe
            extends AbstractComponent<ClickProbe>
            implements DirectPointerHandler {
        private boolean clicked;

        private ClickProbe(int width, int height) {
            super(null, 0, 0, width, height);
        }

        @Override
        public boolean handlesDirectClick() {
            return true;
        }

        @Override
        public void preformOnClickEvent(
                double mouseX,
                double mouseY,
                int button) {
            clicked = true;
        }

        @Override
        public void render(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float delta) {}
    }

    private static final class InertSurface
            extends AbstractComponent<InertSurface> {
        private InertSurface(
                int x,
                int y,
                int width,
                int height) {
            super(null, x, y, width, height);
        }

        @Override
        public void render(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float delta) {}
    }
}
