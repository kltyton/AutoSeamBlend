package com.kltyton.autoseamblend.frontend.uilib.layout.paint;

import com.daqem.uilib.gui.widget.EditBoxWidget;
import com.kltyton.autoseamblend.authoring.workbench.PaintTool;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDraftFields;
import com.kltyton.autoseamblend.frontend.controller.UilibWorkbenchController;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.ChoosePaintColor;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.ChoosePaintTool;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.CycleBrushSize;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.RedoPaint;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.SelectPaintSlot;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction.UndoPaint;
import com.kltyton.autoseamblend.frontend.model.PaintViewModel;
import com.kltyton.autoseamblend.frontend.model.WorkbenchViewLease;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchMetrics;
import com.kltyton.autoseamblend.frontend.tokens.UilibWorkbenchTheme;
import com.kltyton.autoseamblend.frontend.uilib.component.PanelComponent;
import com.kltyton.autoseamblend.frontend.uilib.component.paint.PaintColorSwatchWidget;
import com.kltyton.autoseamblend.frontend.uilib.component.paint.TextureCanvasWidget;
import com.kltyton.autoseamblend.frontend.uilib.layout.shell.WorkbenchChromeLayout.Frame;
import com.kltyton.autoseamblend.frontend.uilib.layout.shell.WorkbenchLayoutHost;
import com.kltyton.autoseamblend.frontend.uilib.widget.ActionButton;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 中文：按验收基线装配像素绘画工作区，宽/中/窄三档响应式分区，并在空间不足时把
 * 控件区置于画布上方。
 *
 * English:
 * Assembles the pixel-paint workspace per the accepted baseline with wide,
 * medium, and narrow responsive partitions, moving partitioned controls above
 * the canvas when the three-column arrangement does not fit.
 */
public final class PaintWorkspaceLayout<T extends WorkbenchDraftFields> {
    private static final int GRID =
            UilibWorkbenchMetrics.GRID;
    private static final int GAP =
            UilibWorkbenchMetrics.PANEL_GAP;
    private static final int CONTROL_HEIGHT =
            UilibWorkbenchMetrics.CONTROL_HEIGHT;
    private static final int CONTROL_STEP =
            CONTROL_HEIGHT + GRID;
    private static final int SLOT_STRIP_HEIGHT = GRID * 10;
    private static final int MIN_SLOT_STRIP_WIDTH = GRID * 24;
    private static final int MIN_TOOL_WIDTH = GRID * 22;
    private static final int MIN_CANVAS_WIDTH = GRID * 20;
    private static final int MIN_CANVAS_HEIGHT = GRID * 16;
    private static final int MIN_PALETTE_WIDTH = GRID * 24;
    private static final int MIN_TOOL_BUTTON_WIDTH = GRID * 10;
    private static final int PALETTE_CELL = GRID * 5;
    private static final int MAX_VISIBLE_SLOTS = 8;
    private static final int WIDE_MIN_WIDTH =
            MIN_TOOL_WIDTH
                    + GAP
                    + MIN_CANVAS_WIDTH
                    + GAP
                    + MIN_PALETTE_WIDTH;
    private static final List<Integer> PALETTE_COLORS =
            com.kltyton.autoseamblend.authoring.workbench.PaintColorPalette.standard();

    private final WorkbenchLayoutHost host;
    private final UilibWorkbenchController<T> controller;
    private final EditBoxWidget colorInput =
            new EditBoxWidget(
                    Minecraft.getInstance().font,
                    0,
                    0,
                    100,
                    20,
                    Component.translatable(
                            "gui.autoseamblend.paint.color"));
    private boolean syncingColorInput;

    public PaintWorkspaceLayout(
            WorkbenchLayoutHost host,
            UilibWorkbenchController<T> controller) {
        this.host = Objects.requireNonNull(
                host,
                "host");
        this.controller = Objects.requireNonNull(
                controller,
                "controller");
        colorInput.setMaxLength(9);
        setColorInput("#FFFFFFFF");
    }

    /**
     * 中文：程序化同步颜色输入框内容，不派发用户动作。
     *
     * English: Programmatically syncs the color input without dispatching a
     * user action.
     */
    private void setColorInput(String rgba) {
        syncingColorInput = true;
        try {
            colorInput.setValue(rgba);
        } finally {
            syncingColorInput = false;
        }
    }

    /**
     * 中文：进入绘画模式时把颜色输入框与当前槽位颜色对齐。
     *
     * English: Aligns the color input with the current slot color when entering
     * paint mode.
     */
    public void open(PaintViewModel paint) {
        setColorInput(
                PaintColorCodec.rgba(
                        Objects.requireNonNull(
                                        paint,
                                        "paint")
                                .color()));
    }

    public void assemble(
            PaintViewModel paint,
            WorkbenchViewLease lease,
            Frame frame) {
        Objects.requireNonNull(paint, "paint");
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(frame, "frame");
        int panelWidth = frame.width();
        int panelHeight = Math.max(
                0,
                frame.footerTop() - GAP - frame.contentTop());
        if (panelWidth <= 0 || panelHeight <= 0) {
            return;
        }
        host.addComponent(new PanelComponent(
                frame.left(),
                frame.contentTop(),
                panelWidth,
                panelHeight,
                UilibWorkbenchTheme.SURFACE_PANEL));
        int innerLeft = frame.left() + GAP;
        int innerWidth = panelWidth - GAP * 2;
        if (innerWidth < MIN_CANVAS_WIDTH) {
            return;
        }
        boolean slotStripVisible = innerWidth
                        >= MIN_SLOT_STRIP_WIDTH
                && panelHeight
                        >= SLOT_STRIP_HEIGHT
                                + GAP
                                + MIN_CANVAS_HEIGHT;
        if (slotStripVisible) {
            assembleSlotStrip(
                    paint,
                    innerLeft,
                    frame.contentTop() + GAP,
                    innerWidth);
        }
        int workspaceTop = frame.contentTop()
                + GAP
                + (slotStripVisible
                        ? SLOT_STRIP_HEIGHT + GAP
                        : 0);
        int workspaceHeight = Math.max(
                0,
                frame.contentTop()
                        + panelHeight
                        - GAP
                        - workspaceTop);
        Optional<Regions> availableRegions =
                Regions.within(
                        innerLeft,
                        workspaceTop,
                        innerWidth,
                        workspaceHeight,
                        PaintTool.values().length,
                        PALETTE_COLORS.size());
        if (availableRegions.isEmpty()) {
            return;
        }
        Regions regions =
                availableRegions.orElseThrow();
        if (regions.tools().width() > 0
                && regions.tools().height() > 0) {
            assembleTools(
                    paint,
                    regions.tools());
        }
        if (regions.palette().width() > 0
                && regions.palette().height() > 0) {
            assemblePalette(
                    paint,
                    regions.palette());
        }
        TextureCanvasWidget canvas =
                new TextureCanvasWidget(
                        regions.canvas().width(),
                        regions.canvas().height(),
                        controller,
                        paint,
                        lease);
        canvas.setX(regions.canvas().left());
        canvas.setY(regions.canvas().top());
        host.addWidget(canvas);
    }

    private void assembleSlotStrip(
            PaintViewModel paint,
            int left,
            int top,
            int width) {
        List<Integer> slots = paint.slots();
        if (slots.isEmpty()) {
            return;
        }
        int selectedIndex = Math.max(
                0,
                slots.indexOf(paint.selectedSlot()));
        host.addText(
                Component.translatable(
                        paint.selectedSynthetic()
                                ? "gui.autoseamblend.paint.slot_generated"
                                : "gui.autoseamblend.paint.slot_native",
                        paint.selectedSlot(),
                        slots.size()),
                left,
                top,
                UilibWorkbenchTheme.TEXT_SECONDARY);
        int stripTop = top + GRID * 4;
        int navigationWidth = Math.min(
                GRID * 11,
                Math.max(GRID * 6, width / 6));
        int slotAreaWidth = Math.max(
                1,
                width - navigationWidth * 2 - GAP);
        int slotsPerPage = Math.max(
                1,
                Math.min(
                        MAX_VISIBLE_SLOTS,
                        (slotAreaWidth + GRID)
                                / (GRID * 5 + GRID)));
        int page = selectedIndex / slotsPerPage;
        int firstSlot = page * slotsPerPage;
        int lastSlot = Math.min(
                slots.size(),
                firstSlot + slotsPerPage);
        ActionButton previous = button(
                "gui.autoseamblend.paint.previous_slots");
        host.placeButton(
                previous,
                left,
                stripTop,
                navigationWidth);
        previous.active = firstSlot > 0
                && host.actionsEnabled();
        previous.setAction(() -> dispatch(
                new SelectPaintSlot(
                        slots.get(
                                Math.max(
                                        0,
                                        firstSlot - 1)))));
        ActionButton next = button(
                "gui.autoseamblend.paint.next_slots");
        host.placeButton(
                next,
                left + width - navigationWidth,
                stripTop,
                navigationWidth);
        next.active = lastSlot < slots.size()
                && host.actionsEnabled();
        next.setAction(() -> dispatch(
                new SelectPaintSlot(
                        slots.get(
                                Math.min(
                                        slots.size() - 1,
                                        lastSlot)))));
        int visibleSlots = lastSlot - firstSlot;
        int slotAreaLeft = left + navigationWidth + GRID;
        int slotGap = GRID;
        int slotWidth = Math.max(
                1,
                (slotAreaWidth
                                - slotGap
                                        * Math.max(
                                                0,
                                                visibleSlots - 1))
                        / Math.max(1, visibleSlots));
        for (int offset = 0;
                offset < visibleSlots;
                offset++) {
            int slot = slots.get(firstSlot + offset);
            ActionButton slotButton =
                    new ActionButton(
                            Component.literal(
                                    Integer.toString(slot)));
            if (slot == paint.selectedSlot()) {
                slotButton.setMessage(
                        slotButton.getMessage()
                                .copy()
                                .append(" \u2713"));
            }
            slotButton.active =
                    host.actionsEnabled();
            host.placeButton(
                    slotButton,
                    slotAreaLeft
                            + offset * (slotWidth + slotGap),
                    stripTop,
                    slotWidth);
            slotButton.setAction(() -> dispatch(
                    new SelectPaintSlot(slot)));
        }
    }

    private void assembleTools(
            PaintViewModel paint,
            Region region) {
        PaintTool[] tools = PaintTool.values();
        int columns = Math.max(
                1,
                Math.min(
                        tools.length,
                        (region.width() + GRID)
                                / (MIN_TOOL_BUTTON_WIDTH + GRID)));
        int toolGap = GRID;
        int toolButtonWidth = Math.max(
                1,
                (region.width()
                                - toolGap * (columns - 1))
                        / columns);
        boolean editable = host.actionsEnabled()
                && paint.editable();
        for (int index = 0;
                index < tools.length;
                index++) {
            PaintTool tool = tools[index];
            ActionButton toolButton = button(
                    "gui.autoseamblend.paint.tool."
                            + tool.name()
                                    .toLowerCase(
                                            Locale.ROOT));
            if (tool == paint.tool()) {
                toolButton.setMessage(
                        toolButton.getMessage()
                                .copy()
                                .append(" \u2713"));
            }
            toolButton.active = editable;
            host.placeButton(
                    toolButton,
                    region.left()
                            + (index % columns)
                                    * (toolButtonWidth + toolGap),
                    region.top()
                            + (index / columns) * CONTROL_STEP,
                    toolButtonWidth);
            toolButton.setAction(() -> dispatch(
                    new ChoosePaintTool(tool)));
        }
        int toolRows = Math.max(
                1,
                (tools.length + columns - 1) / columns);
        int historyTop = region.top()
                + toolRows * CONTROL_STEP;
        int historyWidth = Math.max(
                1,
                (region.width() - GRID) / 2);
        ActionButton undo = button(
                "gui.autoseamblend.paint.undo");
        host.placeButton(
                undo,
                region.left(),
                historyTop,
                historyWidth);
        undo.active = paint.canUndo()
                && host.actionsEnabled();
        undo.setAction(() -> dispatch(
                new UndoPaint()));
        ActionButton redo = button(
                "gui.autoseamblend.paint.redo");
        host.placeButton(
                redo,
                region.left() + historyWidth + GRID,
                historyTop,
                historyWidth);
        redo.active = paint.canRedo()
                && host.actionsEnabled();
        redo.setAction(() -> dispatch(
                new RedoPaint()));
        ActionButton brush = button(
                "gui.autoseamblend.paint.brush_size");
        brush.setMessage(Component.translatable(
                "gui.autoseamblend.paint.brush_size_value",
                paint.brushSize()));
        brush.active = editable;
        host.placeButton(
                brush,
                region.left(),
                historyTop + CONTROL_STEP,
                region.width());
        brush.setAction(() -> dispatch(
                new CycleBrushSize()));
    }

    private void assemblePalette(
            PaintViewModel paint,
            Region region) {
        colorInput.setX(region.left());
        colorInput.setY(region.top());
        colorInput.setWidth(region.width());
        colorInput.active = host.actionsEnabled()
                && paint.editable();
        colorInput.setResponder(value ->
                PaintColorCodec.parseRgba(value)
                        .ifPresent(color -> {
                            if (syncingColorInput
                                    || controller.view()
                                            .paint()
                                            .map(
                                                    PaintViewModel::color)
                                            .orElse(-1)
                                            == color) {
                                return;
                            }
                            dispatch(
                                    new ChoosePaintColor(
                                            color));
                        }));
        host.addWidget(colorInput);
        int columns = Math.max(
                1,
                region.width() / PALETTE_CELL);
        boolean editable = host.actionsEnabled()
                && paint.editable();
        for (int index = 0;
                index < PALETTE_COLORS.size();
                index++) {
            int color = PALETTE_COLORS.get(index);
            PaintColorSwatchWidget swatch =
                    new PaintColorSwatchWidget(
                            color,
                            PaintColorCodec.rgba(color),
                            () -> paint.color() == color,
                            () -> {
                                dispatch(
                                        new ChoosePaintColor(
                                                color));
                                setColorInput(
                                        PaintColorCodec.rgba(
                                                color));
                            });
            swatch.active = editable;
            swatch.setX(
                    region.left()
                            + (index % columns)
                                    * PALETTE_CELL);
            swatch.setY(
                    region.top()
                            + CONTROL_STEP
                            + (index / columns)
                                    * PALETTE_CELL);
            host.addWidget(swatch);
        }
    }

    private static ActionButton button(
            String translationKey) {
        return new ActionButton(
                Component.translatable(
                        translationKey));
    }

    private void dispatch(WorkbenchAction action) {
        controller.dispatch(action);
    }

    private record Region(
            int left,
            int top,
            int width,
            int height) {}

    /**
     * 中文：宽屏保持 86/80/96 三列下限；空间不足时先并排控件区，再按需完全堆叠。
     *
     * English:
     * Keeps the 86/80/96 wide-layout minima, then partitions controls above
     * the canvas and fully stacks them when needed.
     */
    private record Regions(
            Region tools,
            Region canvas,
            Region palette) {
        private static Optional<Regions> within(
                int left,
                int top,
                int width,
                int height,
                int toolCount,
                int paletteCount) {
            int safeWidth = Math.max(0, width);
            int safeHeight = Math.max(0, height);
            if (safeWidth < MIN_CANVAS_WIDTH
                    || safeHeight < MIN_CANVAS_HEIGHT) {
                return Optional.empty();
            }
            if (safeWidth >= WIDE_MIN_WIDTH) {
                int toolWidth = Math.min(
                        GRID * 28,
                        Math.max(
                                MIN_TOOL_WIDTH,
                                safeWidth / 4));
                int paletteWidth = Math.min(
                        GRID * 29,
                        Math.max(
                                MIN_PALETTE_WIDTH,
                                safeWidth / 4));
                int canvasWidth = Math.max(
                        MIN_CANVAS_WIDTH,
                        safeWidth
                                - toolWidth
                                - paletteWidth
                                - GAP * 2);
                int requiredHeight = Math.max(
                        MIN_CANVAS_HEIGHT,
                        Math.max(
                                toolHeight(
                                        toolWidth,
                                        toolCount),
                                paletteHeight(
                                        paletteWidth,
                                        paletteCount)));
                if (safeHeight >= requiredHeight) {
                    int canvasLeft =
                            left + toolWidth + GAP;
                    return Optional.of(new Regions(
                            new Region(
                                    left,
                                    top,
                                    toolWidth,
                                    safeHeight),
                            new Region(
                                    canvasLeft,
                                    top,
                                    canvasWidth,
                                    safeHeight),
                            new Region(
                                    canvasLeft
                                            + canvasWidth
                                            + GAP,
                                    top,
                                    paletteWidth,
                                    safeHeight)));
                }
            }
            if (safeWidth
                    >= MIN_TOOL_WIDTH
                            + GAP
                            + MIN_PALETTE_WIDTH) {
                int toolWidth = MIN_TOOL_WIDTH;
                int paletteWidth =
                        safeWidth - toolWidth - GAP;
                int controlHeight = Math.max(
                        toolHeight(
                                toolWidth,
                                toolCount),
                        paletteHeight(
                                paletteWidth,
                                paletteCount));
                if (safeHeight
                        >= controlHeight
                                + GAP
                                + MIN_CANVAS_HEIGHT) {
                    int canvasTop =
                            top + controlHeight + GAP;
                    return Optional.of(new Regions(
                            new Region(
                                    left,
                                    top,
                                    toolWidth,
                                    controlHeight),
                            new Region(
                                    left,
                                    canvasTop,
                                    safeWidth,
                                    safeHeight
                                            - controlHeight
                                            - GAP),
                            new Region(
                                    left + toolWidth + GAP,
                                    top,
                                    paletteWidth,
                                    controlHeight)));
                }
            }
            int toolHeight = toolHeight(
                    safeWidth,
                    toolCount);
            int paletteHeight = paletteHeight(
                    safeWidth,
                    paletteCount);
            int paletteTop = top + toolHeight + GAP;
            int canvasTop =
                    paletteTop + paletteHeight + GAP;
            if (safeHeight
                    >= toolHeight
                            + paletteHeight
                            + GAP * 2
                            + MIN_CANVAS_HEIGHT) {
                return Optional.of(new Regions(
                        new Region(
                                left,
                                top,
                                safeWidth,
                                toolHeight),
                        new Region(
                                left,
                                canvasTop,
                                safeWidth,
                                safeHeight
                                        - toolHeight
                                        - paletteHeight
                                        - GAP * 2),
                        new Region(
                                left,
                                paletteTop,
                                safeWidth,
                                paletteHeight)));
            }
            return Optional.of(new Regions(
                    new Region(left, top, 0, 0),
                    new Region(
                            left,
                            top,
                            safeWidth,
                            safeHeight),
                    new Region(left, top, 0, 0)));
        }

        private static int toolHeight(
                int width,
                int count) {
            int columns = Math.max(
                    1,
                    Math.min(
                            count,
                            (width + GRID)
                                    / (MIN_TOOL_BUTTON_WIDTH
                                            + GRID)));
            int rows = Math.max(
                    1,
                    (count + columns - 1) / columns);
            return rows * CONTROL_STEP
                    + CONTROL_STEP * 2;
        }

        private static int paletteHeight(
                int width,
                int count) {
            int columns = Math.max(
                    1,
                    width / PALETTE_CELL);
            int rows = Math.max(
                    1,
                    (count + columns - 1) / columns);
            return CONTROL_STEP
                    + rows * PALETTE_CELL;
        }
    }
}
