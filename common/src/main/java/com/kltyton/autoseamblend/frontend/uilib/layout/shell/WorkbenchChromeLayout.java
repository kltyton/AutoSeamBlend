package com.kltyton.autoseamblend.frontend.uilib.layout.shell;

import com.daqem.uilib.client.gui.component.TextComponent;
import com.daqem.uilib.client.gui.text.Text;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchMode;
import com.kltyton.autoseamblend.frontend.layout.ThreeColumnActionGrid;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchMetrics;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import com.kltyton.autoseamblend.frontend.uilib.component.PanelComponent;
import com.kltyton.autoseamblend.frontend.uilib.widget.ActionButton;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

/**
 * 中文：持有工作台固定顶栏、状态栏与底部动作栏，并为领域布局计算无重叠的正文边界。
 *
 * English: Owns the workbench's fixed header, status bar, and footer while
 * calculating non-overlapping body bounds for domain layouts.
 */
public final class WorkbenchChromeLayout {
    private static final int GRID = UilibWorkbenchMetrics.GRID;
    private static final int MARGIN = UilibWorkbenchMetrics.SCREEN_MARGIN;
    private static final int GAP = UilibWorkbenchMetrics.PANEL_GAP;
    private static final int HEADER_HEIGHT = UilibWorkbenchMetrics.HEADER_HEIGHT;
    private static final int FOOTER_HEIGHT = UilibWorkbenchMetrics.FOOTER_HEIGHT;
    private static final int STATUS_HEIGHT = UilibWorkbenchMetrics.STATUS_BAR_HEIGHT;
    private static final int CONTROL_HEIGHT = UilibWorkbenchMetrics.CONTROL_HEIGHT;
    private static final int FOOTER_COLUMNS = 3;

    private final WorkbenchLayoutHost host;
    private final TextComponent engineValue = mutableText();
    private final TextComponent statusValue = mutableText();
    private Component engine = Component.empty();
    private Component status = Component.empty();

    public WorkbenchChromeLayout(WorkbenchLayoutHost host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    /** 中文：统一四种工作台模式的标题翻译。 / English: Shared translated title for all four workbench modes. */
    public static Component modeTitle(WorkbenchMode mode) {
        return Component.translatable(switch (Objects.requireNonNull(mode, "mode")) {
            case TARGET_LIBRARY -> "gui.autoseamblend.title";
            case CONNECTION_PREVIEW -> "gui.autoseamblend.preview.title";
            case TEXTURE_PAINT -> "gui.autoseamblend.paint.title";
            case NATIVE_PROPERTIES -> "gui.autoseamblend.property.title";
        });
    }

    public void setEngine(Component value) {
        engine = Objects.requireNonNull(value, "value");
    }

    public void setStatus(Component value) {
        status = Objects.requireNonNull(value, "value");
    }

    public Frame begin(Component title, boolean withStatus) {
        Frame frame = Frame.within(
                host.width(),
                host.height(),
                withStatus);
        assembleHeader(
                Objects.requireNonNull(title, "title"),
                frame);
        return frame;
    }

    public void status(Frame frame) {
        Objects.requireNonNull(frame, "frame");
        if (frame.statusHeight() <= 0 || frame.width() <= 0) {
            return;
        }
        host.addComponent(new PanelComponent(
                frame.left(),
                frame.statusTop(),
                frame.width(),
                frame.statusHeight(),
                UilibWorkbenchTheme.SURFACE_HEADER,
                PanelComponent.Relief.FLAT));
        Font font = Minecraft.getInstance().font;
        int inset = boundedInset(frame.width(), GRID);
        int textWidth = Math.max(
                0,
                frame.width() - inset * 2);
        Component statusClipped = fit(
                status,
                textWidth,
                UilibWorkbenchTheme.TEXT_INVERSE);
        statusValue.setText(new Text(
                font,
                statusClipped));
        statusValue.setWidth(
                font.width(statusClipped));
        statusValue.setHeight(
                font.lineHeight);
        statusValue.setX(frame.left() + inset);
        statusValue.setY(centeredTextY(
                frame.statusTop(),
                frame.statusHeight(),
                font));
        host.addComponent(statusValue);
    }

    public void footer(
            Frame frame,
            Action left,
            Action middle,
            Action right) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(middle, "middle");
        Objects.requireNonNull(right, "right");
        if (frame.footerHeight() <= 0 || frame.width() <= 0) {
            return;
        }
        host.addComponent(new PanelComponent(
                frame.left(),
                frame.footerTop(),
                frame.width(),
                frame.footerHeight(),
                UilibWorkbenchTheme.SURFACE_RAISED));
        if (frame.footerHeight() < CONTROL_HEIGHT
                || frame.width() < FOOTER_COLUMNS) {
            return;
        }
        ThreeColumnActionGrid grid = ThreeColumnActionGrid.within(
                frame.left(),
                frame.width(),
                MARGIN,
                GAP);
        int buttonTop = frame.footerTop()
                + (frame.footerHeight() - CONTROL_HEIGHT) / 2;
        place(left, grid, 0, buttonTop);
        place(middle, grid, 1, buttonTop);
        place(right, grid, 2, buttonTop);
    }

    private void assembleHeader(Component title, Frame frame) {
        if (frame.headerHeight() <= 0 || frame.width() <= 0) {
            return;
        }
        host.addComponent(new PanelComponent(
                frame.left(),
                frame.headerTop(),
                frame.width(),
                frame.headerHeight(),
                UilibWorkbenchTheme.SURFACE_HEADER,
                PanelComponent.Relief.FLAT));
        Font font = Minecraft.getInstance().font;
        if (frame.headerHeight() < font.lineHeight) {
            return;
        }
        int inset = boundedInset(frame.width(), GAP);
        int available = Math.max(
                0,
                frame.width() - inset * 2);
        int engineWidth = Math.min(
                font.width(engine),
                available / 2);
        int gap = engineWidth > 0
                ? Math.min(
                        GAP,
                        Math.max(0, available - engineWidth))
                : 0;
        int titleWidth = Math.max(
                0,
                available - engineWidth - gap);
        int textY = centeredTextY(
                frame.headerTop(),
                frame.headerHeight(),
                font);
        if (titleWidth > 0) {
            host.addText(
                    fit(
                            title,
                            titleWidth,
                            UilibWorkbenchTheme.TEXT_INVERSE),
                    frame.left() + inset,
                    textY,
                    UilibWorkbenchTheme.TEXT_INVERSE);
        }
        if (engineWidth <= 0) {
            return;
        }
        Component engineClipped = fit(
                engine,
                engineWidth,
                UilibWorkbenchTheme.TEXT_INVERSE);
        engineValue.setText(new Text(
                font,
                engineClipped));
        engineValue.setWidth(
                font.width(engineClipped));
        engineValue.setHeight(
                font.lineHeight);
        int renderedWidth = font.width(engineClipped);
        engineValue.setX(
                frame.left()
                        + frame.width()
                        - inset
                        - renderedWidth);
        engineValue.setY(textY);
        host.addComponent(engineValue);
    }

    private void place(
            Action action,
            ThreeColumnActionGrid grid,
            int column,
            int top) {
        ActionButton button = new ActionButton(action.label());
        button.setAction(action.execute());
            button.setActive(action.enabled());
        host.placeButton(
                button,
                grid.x(column),
                top,
                grid.width(column));
    }

    private static int boundedInset(int width, int desired) {
        return Math.min(
                desired,
                Math.max(0, (width - 1) / 2));
    }

    private static Component fit(
            Component value,
            int width,
            int color) {
        if (width <= 0) {
            return Component.empty();
        }
        Font font = Minecraft.getInstance().font;
        if (font.width(value) <= width) {
            return value.copy().withStyle(style -> style.withColor(TextColor.fromRgb(color)));
        }
        return Component.literal(
                        font.plainSubstrByWidth(
                                value.getString(),
                                width))
                .withStyle(style -> style.withColor(TextColor.fromRgb(color)));
    }

    private static int centeredTextY(
            int top,
            int height,
            Font font) {
        return top
                + Math.max(
                        0,
                        (height - font.lineHeight) / 2);
    }

    private static TextComponent mutableText() {
        return new TextComponent(
                0,
                0,
                new Text(
                        Minecraft.getInstance().font,
                        Component.empty()));
    }

    /**
     * 中文：底部动作的不可变视图描述。
     *
     * English: Immutable view description of one footer action.
     */
    public record Action(
            Component label,
            Runnable execute,
            boolean enabled) {
        public Action {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(execute, "execute");
        }
    }

    /**
     * 中文：在可用高度不足时依次收缩间距与固定区，保证各区域不交叉也不产生负尺寸。
     *
     * English: Shrinks gaps and fixed regions in order when height is scarce,
     * keeping every region non-overlapping and free of negative dimensions.
     */
    public record Frame(
            int left,
            int width,
            int headerTop,
            int headerHeight,
            int contentTop,
            int bodyBottom,
            int statusTop,
            int statusHeight,
            int footerTop,
            int footerHeight) {
        private static Frame within(
                int screenWidth,
                int screenHeight,
                boolean withStatus) {
            int safeWidth = Math.max(0, screenWidth);
            int safeHeight = Math.max(0, screenHeight);
            int horizontalMargin = Math.min(
                    MARGIN,
                    Math.max(0, (safeWidth - 1) / 2));
            int fixedMinimum = HEADER_HEIGHT
                    + FOOTER_HEIGHT
                    + (withStatus ? STATUS_HEIGHT : 0);
            int verticalMargin = Math.min(
                    MARGIN,
                    Math.max(
                            0,
                            (safeHeight - fixedMinimum) / 2));
            int usableHeight = Math.max(
                    0,
                    safeHeight - verticalMargin * 2);
            int headerHeight = Math.min(
                    HEADER_HEIGHT,
                    usableHeight);
            int remaining = usableHeight - headerHeight;
            int footerHeight = Math.min(
                    FOOTER_HEIGHT,
                    remaining);
            remaining -= footerHeight;
            int statusHeight = withStatus
                    ? Math.min(STATUS_HEIGHT, remaining)
                    : 0;
            remaining -= statusHeight;
            int gapCount = withStatus ? 3 : 2;
            int responsiveGap = Math.min(
                    GAP,
                    remaining / gapCount);
            int headerTop = verticalMargin;
            int footerTop = verticalMargin
                    + usableHeight
                    - footerHeight;
            int statusTop = footerTop
                    - responsiveGap
                    - statusHeight;
            int contentTop = headerTop
                    + headerHeight
                    + responsiveGap;
            int bodyBottom = withStatus
                    ? statusTop - responsiveGap
                    : footerTop - responsiveGap;
            return new Frame(
                    horizontalMargin,
                    Math.max(
                            0,
                            safeWidth - horizontalMargin * 2),
                    headerTop,
                    headerHeight,
                    contentTop,
                    Math.max(contentTop, bodyBottom),
                    statusTop,
                    statusHeight,
                    footerTop,
                    footerHeight);
        }

        public int bodyHeight() {
            return Math.max(0, bodyBottom - contentTop);
        }
    }
}
