package com.kltyton.autoseamblend.frontend.uilib.layout.target;

import com.daqem.uilib.gui.widget.EditBoxWidget;
import com.daqem.uilib.gui.widget.ScrollContainerWidget;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchMetrics;
import com.kltyton.autoseamblend.frontend.uilib.component.PanelComponent;
import com.kltyton.autoseamblend.frontend.uilib.component.target.TargetRowComponent;
import com.kltyton.autoseamblend.frontend.uilib.layout.shell.WorkbenchLayoutHost;
import com.kltyton.autoseamblend.frontend.model.TargetRowView;
import com.kltyton.autoseamblend.frontend.uilib.widget.ActionButton;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 中文：装配目标库、选择器和窄窗口单列工具区，并持有纯视图交互状态。
 *
 * English:
 * Assembles the target library, picker, and narrow single-column tools while
 * owning view-only interaction state.
 */
public final class TargetLibraryLayout {
    private static final int MARGIN =
            UilibWorkbenchMetrics.SCREEN_MARGIN;
    private static final int GAP =
            UilibWorkbenchMetrics.PANEL_GAP;
    private static final int TOOLBAR_HEIGHT = 24;
    private static final int DOCK_THRESHOLD =
            UilibWorkbenchMetrics.NARROW_WIDTH;

    private final WorkbenchLayoutHost host;
    private final EditBoxWidget searchInput =
            new EditBoxWidget(
                    Minecraft.getInstance().font,
                    0,
                    0,
                    180,
                    20,
                    Component.translatable(
                            "gui.autoseamblend.target.search"));
    private boolean pickerOpen;
    private String expandedBlockId;

    public TargetLibraryLayout(
            WorkbenchLayoutHost host) {
        this.host = Objects.requireNonNull(host, "host");
        searchInput.setMaxLength(128);
        searchInput.setResponder(ignored -> host.rebuild());
    }

    public void assemble(
            List<TargetRowView> targets,
            List<TargetRowView> available,
            int contentTop,
            int bodyBottom,
            Runnable pickerRequested,
            Consumer<String> add,
            Consumer<String> preview,
            Consumer<String> paint,
            Consumer<String> properties) {
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(available, "available");
        Objects.requireNonNull(pickerRequested, "pickerRequested");
        Objects.requireNonNull(add, "add");
        Objects.requireNonNull(preview, "preview");
        Objects.requireNonNull(paint, "paint");
        Objects.requireNonNull(properties, "properties");
        Columns columns = Columns.within(host.width());
        assembleToolbar(
                targets,
                contentTop,
                columns,
                pickerRequested);
        int listTop = contentTop + TOOLBAR_HEIGHT + GAP;
        int listHeight = Math.max(1, bodyBottom - listTop);
        assembleList(
                pickerOpen
                        ? filteredAvailable(available)
                        : targets,
                listTop,
                listHeight,
                columns.listWidth(),
                add,
                preview,
                paint,
                properties);
        if (columns.docked()) {
            assembleDock(
                    targets.size(),
                    contentTop,
                    bodyBottom,
                    columns,
                    pickerRequested);
        }
    }

    private void assembleToolbar(
            List<TargetRowView> targets,
            int top,
            Columns columns,
            Runnable pickerRequested) {
        host.addComponent(new PanelComponent(
                MARGIN,
                top,
                columns.listWidth(),
                TOOLBAR_HEIGHT,
                UilibWorkbenchTheme.SURFACE_PANEL));
        if (columns.docked() || !pickerOpen) {
            host.addText(
                    pickerOpen
                            ? Component.translatable(
                                    "gui.autoseamblend.target.picker")
                            : Component.translatable(
                                    "gui.autoseamblend.target.count",
                                    targets.size()),
                    MARGIN + 8,
                    centeredTextY(top, TOOLBAR_HEIGHT),
                    UilibWorkbenchTheme.TEXT_SECONDARY);
        }
        if (columns.docked()) {
            return;
        }
        ActionButton addButton = pickerButton(pickerRequested);
        int buttonWidth = Math.min(
                104,
                Math.max(72, columns.listWidth() / 3));
        host.placeButton(
                addButton,
                MARGIN + columns.listWidth() - buttonWidth - 4,
                top + 4,
                buttonWidth);
        if (pickerOpen) {
            int searchLeft = MARGIN + 4;
            int searchRight = addButton.getX() - 4;
            searchInput.setX(searchLeft);
            searchInput.setY(top + 4);
            searchInput.setWidth(
                    Math.max(1, searchRight - searchLeft));
            host.addWidget(searchInput);
        }
    }

    private void assembleList(
            List<TargetRowView> visible,
            int top,
            int height,
            int width,
            Consumer<String> add,
            Consumer<String> preview,
            Consumer<String> paint,
            Consumer<String> properties) {
        host.addComponent(new PanelComponent(
                MARGIN,
                top,
                width,
                height,
                UilibWorkbenchTheme.SURFACE_INPUT,
                PanelComponent.Relief.INSET));
        ScrollContainerWidget list =
                new ScrollContainerWidget(
                        Math.max(1, width),
                        height,
                        4);
        list.setX(MARGIN);
        list.setY(top);
        if (visible.isEmpty()) {
            host.addText(
                    Component.translatable(
                            pickerOpen
                                    ? "gui.autoseamblend.target.no_match"
                                    : "gui.autoseamblend.target.empty"),
                    MARGIN + 12,
                    top + 12,
                    UilibWorkbenchTheme.TEXT_BUTTON_SECONDARY);
        } else {
            for (TargetRowView row : visible) {
                list.addComponent(
                        pickerOpen
                                ? pickerRow(row, list.getWidth(), add)
                                : targetRow(
                                        row,
                                        list.getWidth(),
                                        preview,
                                        paint,
                                        properties));
            }
        }
        host.addWidget(list);
    }

    private void assembleDock(
            int targetCount,
            int top,
            int bottom,
            Columns columns,
            Runnable pickerRequested) {
        host.addComponent(new PanelComponent(
                columns.sideLeft(),
                top,
                columns.sideWidth(),
                Math.max(1, bottom - top),
                UilibWorkbenchTheme.SURFACE_PANEL));
        ActionButton addButton = pickerButton(pickerRequested);
        host.placeButton(
                addButton,
                columns.sideLeft() + 8,
                top + 8,
                columns.sideWidth() - 16);
        host.addText(
                pickerOpen
                        ? Component.translatable(
                                "gui.autoseamblend.target.search")
                        : Component.translatable(
                                "gui.autoseamblend.target.count",
                                targetCount),
                columns.sideLeft() + 8,
                top + 38,
                UilibWorkbenchTheme.TEXT_SECONDARY);
        if (pickerOpen) {
            searchInput.setX(columns.sideLeft() + 8);
            searchInput.setY(top + 52);
            searchInput.setWidth(columns.sideWidth() - 16);
            host.addWidget(searchInput);
        }
    }

    private ActionButton pickerButton(
            Runnable pickerRequested) {
        ActionButton button = new ActionButton(
                Component.translatable(
                        pickerOpen
                                ? "gui.autoseamblend.target.finish_add"
                                : "gui.autoseamblend.target.add"));
        button.active = host.actionsEnabled();
        button.setAction(() -> {
            if (!pickerOpen) {
                pickerRequested.run();
            }
            pickerOpen = !pickerOpen;
            host.rebuild();
        });
        return button;
    }

    private TargetRowComponent targetRow(
            TargetRowView row,
            int rowWidth,
            Consumer<String> preview,
            Consumer<String> paint,
            Consumer<String> properties) {
        boolean expanded = row.entryKey().equals(expandedBlockId);
        return new TargetRowComponent(
                rowWidth - 8,
                row,
                expanded,
                () -> {
                    expandedBlockId = expanded
                            ? null
                            : row.entryKey();
                    host.rebuild();
                },
                () -> preview.accept(row.entryKey()),
                () -> paint.accept(row.entryKey()),
                () -> properties.accept(row.entryKey()));
    }

    private static TargetRowComponent pickerRow(
            TargetRowView row,
            int rowWidth,
            Consumer<String> add) {
        return new TargetRowComponent(
                rowWidth - 8,
                row,
                false,
                () -> add.accept(
                        row.receiverBlockId()
                                .orElseThrow(() ->
                                        new IllegalStateException(
                                                "TARGET_RECEIVER_REQUIRED"))),
                () -> {},
                () -> {},
                () -> {});
    }

    private List<TargetRowView> filteredAvailable(
            List<TargetRowView> available) {
        String query = searchInput.getValue()
                .trim()
                .toLowerCase(Locale.ROOT);
        return available.stream()
                .filter(row ->
                        query.isEmpty()
                                || row.entryId()
                                        .toLowerCase(Locale.ROOT)
                                        .contains(query)
                                || row.displayName()
                                        .getString()
                                        .toLowerCase(Locale.ROOT)
                                        .contains(query))
                .limit(96)
                .toList();
    }

    private static int centeredTextY(
            int top,
            int regionHeight) {
        return top
                + Math.max(
                        0,
                        (regionHeight
                                        - Minecraft.getInstance()
                                                .font.lineHeight)
                                / 2);
    }

    /**
     * 中文：目标库在 360px 以下只保留单列，搜索框只使用动作按钮左侧剩余宽度。
     *
     * English:
     * Keeps the target library single-column below 360px and confines search
     * to the width remaining left of the action button.
     */
    private record Columns(
            boolean docked,
            int listWidth,
            int sideLeft,
            int sideWidth) {
        private static Columns within(
                int screenWidth) {
            boolean docked = screenWidth >= DOCK_THRESHOLD;
            int sideWidth = docked
                    ? Math.min(
                            132,
                            Math.max(104, screenWidth / 4))
                    : 0;
            int listWidth = Math.max(
                    1,
                    screenWidth
                            - MARGIN * 2
                            - (docked
                                    ? sideWidth + GAP
                                    : 0));
            return new Columns(
                    docked,
                    listWidth,
                    MARGIN + listWidth + GAP,
                    sideWidth);
        }
    }
}
