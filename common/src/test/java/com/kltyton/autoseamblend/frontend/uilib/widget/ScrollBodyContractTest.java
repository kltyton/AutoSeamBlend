package com.kltyton.autoseamblend.frontend.uilib.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.daqem.uilib.client.gui.component.scroll.ScrollContentComponent;
import com.kltyton.autoseamblend.frontend.uilib.component.PanelComponent;
import org.junit.jupiter.api.Test;

/**
 * 中文：真实状态传递合同测试——capture 返回当前滚动偏移，重建产生的新 ScrollBody 通过
 * restore(offset) 恢复旧偏移；三个互不共享的滚动体互不串值。
 *
 * English:
 * Real state-passing contract tests: capture() returns the live scroll offset, a rebuild's
 * new ScrollBody restores the old offset via restore(offset), and three independent scroll
 * bodies never cross-contaminate each other's offsets.
 */
class ScrollBodyContractTest {
    @Test
    void captureReturnsCurrentOffsetOfRealScrollBody() {
        ScrollBody body = bodyWithRows(42, 42, 42);

        content(body).setY(-40);

        assertEquals(-40, body.capture());
    }

    @Test
    void capturedOffsetRestoresIntoNewScrollBody() {
        ScrollBody original = bodyWithRows(42, 42, 42);
        content(original).setY(-30);
        int captured = original.capture();

        ScrollBody rebuilt = bodyWithRows(42, 42, 42);
        rebuilt.restore(captured);

        assertEquals(-30, contentY(rebuilt));
    }

    @Test
    void restoreClampsToZeroWhenContentFits() {
        ScrollBody rebuilt = bodyWithRows(42);

        rebuilt.restore(-10);

        assertEquals(0, contentY(rebuilt));
    }

    @Test
    void threeIndependentScrollBodiesDoNotShareOffsets() {
        ScrollBody property = bodyWithRows(42, 42, 42);
        ScrollBody picker = bodyWithRows(42, 42, 42, 42);
        ScrollBody editor = bodyWithRows(42, 42);
        content(property).setY(-20);
        content(picker).setY(-60);
        content(editor).setY(-5);

        ScrollBody newProperty = bodyWithRows(42, 42, 42);
        ScrollBody newPicker = bodyWithRows(42, 42, 42, 42);
        ScrollBody newEditor = bodyWithRows(42, 42);
        newProperty.restore(property.capture());
        newPicker.restore(picker.capture());
        newEditor.restore(editor.capture());

        assertEquals(-20, contentY(newProperty));
        assertEquals(-60, contentY(newPicker));
        assertEquals(-5, contentY(newEditor));
        assertNotEquals(
                contentY(newProperty),
                contentY(newPicker));
        assertNotEquals(
                contentY(newPicker),
                contentY(newEditor));
    }

    private static ScrollBody bodyWithRows(
            int... heights) {
        ScrollBody body = new ScrollBody(
                0,
                0,
                100,
                80,
                4);
        for (int height : heights) {
            body.addChild(new PanelComponent(
                    0,
                    0,
                    100,
                    height,
                    0xFF000000,
                    PanelComponent.Relief.FLAT));
        }
        return body;
    }

    private static ScrollContentComponent content(
            ScrollBody body) {
        return body.panel()
                .getScrollContentComponent()
                .orElseThrow();
    }

    private static int contentY(
            ScrollBody body) {
        return content(body).getY();
    }

}
