package com.kltyton.autoseamblend.frontend.uilib.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.client.gui.components.AbstractWidget;
import org.junit.jupiter.api.Test;

/**
 * 中文：1.20.1 GUI 渲染兼容合同。1.20.1 不存在 minecraft:widget/button、
 * button_highlighted、scroller、scroller_background 资源；原版按钮必须从
 * AbstractWidget.WIDGETS_LOCATION（textures/gui/widgets.png）用 blitNineSliced 绘制，
 * 其 V 偏移为 66 正常 / 46 禁用 / 86 悬停；原版滚动条用 fill 绘制黑轨道、灰滑块、浅灰高亮。
 * 本测试把这些事实固化为 RED→GREEN 合同，并拒绝兼容层源码回退到缺失的 sprite 路径。
 *
 * English:
 * 1.20.1 GUI rendering compatibility contract. 1.20.1 has no
 * minecraft:widget/button, button_highlighted, scroller, or scroller_background
 * assets; the vanilla button must be drawn from AbstractWidget.WIDGETS_LOCATION
 * (textures/gui/widgets.png) with blitNineSliced at V=66 normal / 46 disabled /
 * 86 hovered, and the vanilla scrollbar is drawn with fills: black track, gray
 * thumb, light-gray highlight. These tests pin that contract RED→GREEN and reject
 * any regression to the missing sprite paths in the compatibility layer sources.
 */
class VanillaWidgetCompatContractTest {

    @Test
    void vanillaButtonSpriteComesFromWidgetsLocation() {
        assertEquals(
                "textures/gui/widgets.png",
                AbstractWidget.WIDGETS_LOCATION.getPath());
    }

    @Test
    void buttonSpriteRowsMatchVanillaVOffsets() {
        assertEquals(66, ButtonSpriteState.buttonSpriteV(0));
        assertEquals(46, ButtonSpriteState.buttonSpriteV(1));
        assertEquals(86, ButtonSpriteState.buttonSpriteV(2));
    }

    @Test
    void actionButtonsDrawTheVanillaNineSlicedBackground() throws IOException {
        Path sourcePath = Paths.get(
                System.getProperty("user.dir"),
                "src",
                "main",
                "java",
                "com",
                "kltyton",
                "autoseamblend",
                "frontend",
                "uilib",
                "widget",
                "ActionButton.java");
        String source = Files.readString(
                sourcePath,
                StandardCharsets.UTF_8);

        assertTrue(
                source.contains("graphics.blitNineSliced("),
                "ActionButton cannot delegate its background to UILib 0.3.6 "
                        + "because AutoSeamBlendButton supplies a null texture");
        assertTrue(
                source.contains("AbstractWidget.WIDGETS_LOCATION"),
                "ActionButton must use the 1.20.1 vanilla widgets texture");
    }

    @Test
    void vanillaEditBoxRenderingCancelsUilibLocalPoseTranslation()
            throws IOException {
        Path sourcePath = Paths.get(
                System.getProperty("user.dir"),
                "src",
                "main",
                "java",
                "com",
                "kltyton",
                "autoseamblend",
                "frontend",
                "uilib",
                "component",
                "TextBoxComponent.java");
        String source = Files.readString(
                sourcePath,
                StandardCharsets.UTF_8);
        int push = source.indexOf("graphics.pose().pushPose();");
        int cancel = source.indexOf("-getTotalX()");
        int render = source.indexOf("editBox.render(");
        int pop = source.indexOf("graphics.pose().popPose();");

        assertTrue(push >= 0, "EditBox rendering must save the UILib pose");
        assertTrue(push < cancel, "pose translation must be cancelled after push");
        assertTrue(cancel < render, "pose translation must be cancelled before EditBox.render");
        assertTrue(render < pop, "the UILib pose must be restored after EditBox.render");
    }

    @Test
    void workbenchClickRouterReachesRowsInsideScrollContent()
            throws IOException {
        Path sourcePath = Paths.get(
                System.getProperty("user.dir"),
                "src",
                "main",
                "java",
                "com",
                "kltyton",
                "autoseamblend",
                "frontend",
                "uilib",
                "screen",
                "AbstractUilibWorkbenchScreen.java");
        String source = Files.readString(
                sourcePath,
                StandardCharsets.UTF_8);

        assertTrue(
                source.contains("instanceof ScrollPanelComponent"),
                "topmost routing must recognize UILib's dedicated scroll-content tree");
        assertTrue(
                source.contains("getScrollContentComponent()"),
                "target picker rows are stored outside ScrollPanelComponent.getChildren()");
        assertTrue(
                source.contains("component.getOnClickEvent() != null"),
                "inert panel surfaces must not consume clicks before picker rows");
    }

    @Test
    void widgetSourcesDoNotReferenceMissingSprites() throws IOException {
        Path widgetSources = Paths.get(
                        System.getProperty("user.dir"),
                        "src",
                        "main",
                        "java",
                        "com",
                        "kltyton",
                        "autoseamblend",
                        "frontend",
                        "uilib",
                        "widget");
        assertTrue(
                Files.isDirectory(widgetSources),
                "widget source directory not found under "
                        + widgetSources);
        List<String> missingSprites = List.of(
                "widget/button",
                "widget/button_highlighted",
                "widget/button_disabled",
                "widget/scroller",
                "widget/scroller_background");
        try (Stream<Path> files = Files.list(widgetSources)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        String source;
                        try {
                            source = Files.readString(
                                    path,
                                    StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            fail("cannot read " + path, e);
                            return;
                        }
                        for (String sprite : missingSprites) {
                            assertTrue(
                                    !source.contains(sprite),
                                    path.getFileName()
                                            + " still references missing "
                                            + "1.20.1 sprite "
                                            + sprite);
                        }
                    });
        }
    }

    @Test
    void scrollPanelClipsContentAndScrollbarToItsViewport() throws IOException {
        Path sourcePath = Paths.get(
                System.getProperty("user.dir"),
                "src",
                "main",
                "java",
                "com",
                "kltyton",
                "autoseamblend",
                "frontend",
                "uilib",
                "widget",
                "VanillaScrollPanelComponent.java");
        String source = Files.readString(
                sourcePath,
                StandardCharsets.UTF_8);
        int enable = source.indexOf("graphics.enableScissor(");
        int content = source.indexOf("super.render(");
        int scrollbar = source.indexOf("renderVanillaScrollbar(graphics);");
        int disable = source.indexOf("graphics.disableScissor();");

        assertTrue(enable >= 0, "scroll viewport must enable scissoring");
        assertTrue(enable < content, "scissoring must start before content rendering");
        assertTrue(content < scrollbar, "scrollbar must render after the clipped content");
        assertTrue(scrollbar < disable, "scissoring must end after scrollbar rendering");
    }
}
