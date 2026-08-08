package com.kltyton.autoseamblend.frontend.paint;

import com.kltyton.autoseamblend.authoring.workbench.PaintTool;
import com.kltyton.autoseamblend.engine.ownership.NativeSlotIntent;
import com.kltyton.autoseamblend.frontend.paint.CarrierEditPlan.RegionEdit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 中文：Loader 无关的逐槽可撤销直通 ARGB 绘画文档。
 *
 * English: Loader-neutral undoable per-slot straight-ARGB paint document.
 */
public final class TexturePaintDocument {
    private static final int MAX_HISTORY = 64;

    private final LinkedHashMap<Integer, LogicalLayer>
            slots = new LinkedHashMap<>();
    private final String tilesExpression;
    private int selectedSlot;
    private PaintTool tool = PaintTool.BRUSH;
    private int color = 0xFFFFFFFF;
    private int brushSize = 1;

    public TexturePaintDocument(
            PaintDocumentSource source) {
        source = Objects.requireNonNull(source, "source");
        tilesExpression = source.tilesExpression();
        for (PaintDocumentSource.Slot slot : source.slots()) {
            slots.computeIfAbsent(
                            slot.logicalIndex(),
                            LogicalLayer::new)
                    .add(slot);
        }
        selectedSlot = slots.keySet()
                .stream()
                .findFirst()
                .orElse(-1);
    }

    public boolean available() {
        return selectedSlot >= 0;
    }

    public String tilesExpression() {
        return tilesExpression;
    }

    public List<Integer> slotIndices() {
        return List.copyOf(slots.keySet());
    }

    public int selectedSlot() {
        if (!available()) {
            throw new IllegalStateException(
                    "paint document has no slot");
        }
        return selectedSlot;
    }

    public NativeSlotIntent selectedNativeIntent() {
        return layer().nativeIntent();
    }

    public boolean selectedSynthetic() {
        return layer().synthetic();
    }

    /**
     * 中文：当前选中逻辑层是否可编辑；无槽位时安全返回 false。
     * English: Whether the selected logical layer is editable; safely false when no slot is selected.
     */
    public boolean selectedEditable() {
        return available() && layer().editable();
    }

    /**
     * 中文：当前选中逻辑层是否可撤销，由真实历史栈只读派生；无槽位时安全返回 false。
     * English: Whether the selected logical layer can undo, read-only derived from its real history stack; safely false when no slot is selected.
     */
    public boolean canUndo() {
        return available() && layer().undoAvailable();
    }

    /**
     * 中文：当前选中逻辑层是否可重做，由真实历史栈只读派生；无槽位时安全返回 false。
     * English: Whether the selected logical layer can redo, read-only derived from its real history stack; safely false when no slot is selected.
     */
    public boolean canRedo() {
        return available() && layer().redoAvailable();
    }

    public void selectSlot(int slot) {
        if (!slots.containsKey(slot)) {
            throw new IllegalArgumentException(
                    "connected-texture slot is unavailable");
        }
        selectedSlot = slot;
    }

    public int width() {
        return layer().width();
    }

    public int height() {
        return layer().height();
    }

    public int colorAt(int x, int y) {
        requireCoordinate(x, y);
        return layer().colorAt(x, y);
    }

    public PaintTool tool() {
        return tool;
    }

    public void setTool(PaintTool value) {
        tool = Objects.requireNonNull(value, "value");
    }

    public int color() {
        return color;
    }

    public void setColor(int value) {
        color = value;
    }

    public int brushSize() {
        return brushSize;
    }

    public void cycleBrushSize() {
        brushSize = switch (brushSize) {
            case 1 -> 2;
            case 2 -> 4;
            default -> 1;
        };
    }

    public boolean apply(int x, int y) {
        requireCoordinate(x, y);
        if (tool == PaintTool.PICKER) {
            color = colorAt(x, y);
            return false;
        }
        LogicalLayer layer = layer();
        if (!layer.editable()) {
            return false;
        }
        if (tool == PaintTool.FILL) {
            if (!layer.pixelWouldChange(x, y, color)) {
                return false;
            }
            layer.rememberForEdit();
            layer.fill(x, y, color);
            return true;
        }
        int paint = tool == PaintTool.ERASER ? 0 : color;
        int radius = brushSize / 2;
        boolean changed = false;
        for (int offsetY = 0;
                offsetY < brushSize && !changed;
                offsetY++) {
            for (int offsetX = 0;
                    offsetX < brushSize;
                    offsetX++) {
                int pixelX = x + offsetX - radius;
                int pixelY = y + offsetY - radius;
                if (pixelX >= 0
                        && pixelX < width()
                        && pixelY >= 0
                        && pixelY < height()
                        && layer.pixelWouldChange(
                                pixelX,
                                pixelY,
                                paint)) {
                    changed = true;
                    break;
                }
            }
        }
        if (!changed) {
            return false;
        }
        layer.rememberForEdit();
        for (int offsetY = 0;
                offsetY < brushSize;
                offsetY++) {
            for (int offsetX = 0;
                    offsetX < brushSize;
                    offsetX++) {
                int pixelX = x + offsetX - radius;
                int pixelY = y + offsetY - radius;
                if (pixelX >= 0
                        && pixelX < width()
                        && pixelY >= 0
                        && pixelY < height()) {
                    layer.paint(pixelX, pixelY, paint);
                }
            }
        }
        return true;
    }

    /** 中文：一个连续指针笔划只建立一次撤销快照。 / English: Creates one undo snapshot for one continuous pointer stroke. */
    public void beginStroke() {
        if (tool != PaintTool.PICKER
                && layer().editable()) {
            layer().beginStroke();
        }
    }

    /** 中文：结束当前连续笔划。 / English: Ends the current continuous stroke. */
    public void endStroke() {
        if (available()) {
            layer().endStroke();
        }
    }

    public boolean undo() {
        return layer().undo();
    }

    public boolean redo() {
        return layer().redo();
    }

    public boolean dirty() {
        return slots.values()
                .stream()
                .anyMatch(LogicalLayer::dirty);
    }

    /**
     * 中文：按原生 PNG 载体规划物理单元修改，并拒绝内容或单元冲突。
     *
     * English: Plans physical-cell changes per native PNG carrier and rejects
     * carrier-content or cell conflicts.
     */
    public List<CarrierEditPlan> carrierEdits() {
        if (!dirty()) {
            return List.of();
        }
        LinkedHashMap<String, CarrierEditBuilder> carriers =
                new LinkedHashMap<>();
        for (LogicalLayer logical : slots.values()) {
            for (Layer layer : logical.layers) {
                // 中文：未修改的合成层只有在槽位可填充（OMITTED/DECLARED_MISSING）
                // 时才随保存物化；UNKNOWN/default/skip 即使其他槽位已修改也保持保护，
                // 不得因 synthetic=true 被刷写进导出或 Managed 载体文件。
                // English: Unmodified synthetic layers materialize on save only
                // when the slot is fillable (OMITTED/DECLARED_MISSING).
                // UNKNOWN/default/skip stay protected even when another slot is
                // dirty; synthetic=true alone never allows them to be written.
                if (!layer.dirty()
                        && !(layer.synthetic
                                && layer.nativeIntent
                                        .fillable())) {
                    continue;
                }
                CarrierEditBuilder carrier =
                        carriers.computeIfAbsent(
                                layer.outputPath,
                                ignored -> new CarrierEditBuilder(
                                        layer.outputPath,
                                        layer.carrierContentKey));
                carrier.add(layer);
            }
        }
        return carriers.values()
                .stream()
                .map(CarrierEditBuilder::freeze)
                .toList();
    }

    private LogicalLayer layer() {
        return Objects.requireNonNull(
                slots.get(selectedSlot),
                "selected layer");
    }

    private void requireCoordinate(int x, int y) {
        if (x < 0
                || y < 0
                || x >= width()
                || y >= height()) {
            throw new IndexOutOfBoundsException(
                    "pixel outside source frame");
        }
    }

    private static void floodFill(
            int[] pixels,
            int width,
            int height,
            int startX,
            int startY,
            int replacement) {
        int target = pixels[startY * width + startX];
        if (target == replacement) {
            return;
        }
        int[] pending = new int[
                Math.multiplyExact(width, height)];
        int head = 0;
        int tail = 0;
        int start = startY * width + startX;
        pending[tail++] = start;
        pixels[start] = replacement;
        while (head < tail) {
            int index = pending[head++];
            int x = index % width;
            int y = index / width;
            if (x > 0 && pixels[index - 1] == target) {
                pixels[index - 1] = replacement;
                pending[tail++] = index - 1;
            }
            if (x + 1 < width
                    && pixels[index + 1] == target) {
                pixels[index + 1] = replacement;
                pending[tail++] = index + 1;
            }
            if (y > 0
                    && pixels[index - width] == target) {
                pixels[index - width] = replacement;
                pending[tail++] = index - width;
            }
            if (y + 1 < height
                    && pixels[index + width] == target) {
                pixels[index + width] = replacement;
                pending[tail++] = index + width;
            }
        }
    }

    /**
     * 中文：一个 canonical 逻辑槽可对应多个物理单元；编辑和历史同步作用于整组。
     *
     * English: One canonical logical slot may map to multiple physical cells;
     * edits and history therefore operate on the complete group.
     */
    private static final class LogicalLayer {
        private final int logicalIndex;
        private final ArrayList<Layer> layers =
                new ArrayList<>();

        private LogicalLayer(int logicalIndex) {
            this.logicalIndex = logicalIndex;
        }

        private void add(PaintDocumentSource.Slot slot) {
            if (slot.logicalIndex() != logicalIndex) {
                throw new IllegalArgumentException(
                        "CONNECTION_TEXTURE_LOGICAL_SLOT_CONFLICT:"
                                + logicalIndex);
            }
            Layer layer = new Layer(slot);
            if (!layers.isEmpty()
                    && (width() != layer.cellWidth
                            || height() != layer.cellHeight
                            || nativeIntent()
                                    != layer.nativeIntent
                            || synthetic()
                                    != layer.synthetic)) {
                throw new IllegalArgumentException(
                        "CONNECTION_TEXTURE_LOGICAL_SLOT_CONFLICT:"
                                + logicalIndex);
            }
            layers.add(layer);
        }

        private Layer representative() {
            if (layers.isEmpty()) {
                throw new IllegalStateException(
                        "logical paint layer is empty");
            }
            return layers.getFirst();
        }

        private int width() {
            return representative().cellWidth;
        }

        private int height() {
            return representative().cellHeight;
        }

        private int colorAt(int x, int y) {
            return representative().pixels[y * width() + x];
        }

        private NativeSlotIntent nativeIntent() {
            return representative().nativeIntent;
        }

        private boolean synthetic() {
            return representative().synthetic;
        }

        private boolean editable() {
            return switch (nativeIntent()) {
                case PRESENT, DECLARED_MISSING, OMITTED -> true;
                default -> false;
            };
        }

        private boolean pixelWouldChange(
                int x,
                int y,
                int replacement) {
            int index = y * width() + x;
            return layers.stream()
                    .anyMatch(layer ->
                            layer.pixels[index]
                                    != replacement);
        }

        private void rememberForEdit() {
            layers.forEach(Layer::rememberForEdit);
        }

        private void fill(
                int x,
                int y,
                int replacement) {
            int[] result = representative().pixels.clone();
            floodFill(
                    result,
                    width(),
                    height(),
                    x,
                    y,
                    replacement);
            for (Layer layer : layers) {
                layer.pixels = result.clone();
            }
        }

        private void paint(
                int x,
                int y,
                int replacement) {
            int index = y * width() + x;
            for (Layer layer : layers) {
                layer.pixels[index] = replacement;
            }
        }

        private void beginStroke() {
            layers.forEach(Layer::beginStroke);
        }

        private void endStroke() {
            layers.forEach(Layer::endStroke);
        }

        private boolean undo() {
            if (layers.stream()
                    .anyMatch(layer -> layer.undo.isEmpty())) {
                return false;
            }
            layers.forEach(Layer::undo);
            return true;
        }

        private boolean redo() {
            if (layers.stream()
                    .anyMatch(layer -> layer.redo.isEmpty())) {
                return false;
            }
            layers.forEach(Layer::redo);
            return true;
        }

        /**
         * 中文：只读判断整组是否都可撤销，不执行任何历史操作。
         * English: Read-only check that every physical layer can undo; executes no history mutation.
         */
        private boolean undoAvailable() {
            return layers.stream()
                    .allMatch(layer ->
                            !layer.undo.isEmpty());
        }

        /**
         * 中文：只读判断整组是否都可重做，不执行任何历史操作。
         * English: Read-only check that every physical layer can redo; executes no history mutation.
         */
        private boolean redoAvailable() {
            return layers.stream()
                    .allMatch(layer ->
                            !layer.redo.isEmpty());
        }

        private boolean dirty() {
            return layers.stream().anyMatch(Layer::dirty);
        }
    }

    private static final class Layer {
        private final String outputPath;
        private final String carrierContentKey;
        private final int cellX;
        private final int cellY;
        private final int cellWidth;
        private final int cellHeight;
        private final NativeSlotIntent nativeIntent;
        private final boolean synthetic;
        private final int[] original;
        private int[] pixels;
        private final ArrayDeque<int[]> undo =
                new ArrayDeque<>();
        private final ArrayDeque<int[]> redo =
                new ArrayDeque<>();
        private boolean strokeActive;
        private boolean strokeRemembered;

        private Layer(PaintDocumentSource.Slot slot) {
            Objects.requireNonNull(slot, "slot");
            outputPath = slot.outputPath();
            carrierContentKey = slot.carrierContentKey();
            cellX = slot.cellX();
            cellY = slot.cellY();
            cellWidth = slot.cellWidth();
            cellHeight = slot.cellHeight();
            nativeIntent = slot.nativeIntent();
            synthetic = slot.synthetic();
            original = slot.straightArgb();
            pixels = original.clone();
        }

        private void remember() {
            undo.addLast(pixels.clone());
            while (undo.size() > MAX_HISTORY) {
                undo.removeFirst();
            }
            redo.clear();
        }

        private void beginStroke() {
            if (!strokeActive) {
                strokeActive = true;
                strokeRemembered = false;
            }
        }

        private void endStroke() {
            strokeActive = false;
            strokeRemembered = false;
        }

        private void rememberForEdit() {
            if (!strokeActive || !strokeRemembered) {
                remember();
                strokeRemembered = strokeActive;
            }
        }

        private boolean undo() {
            if (undo.isEmpty()) {
                return false;
            }
            redo.addLast(pixels.clone());
            pixels = undo.removeLast();
            return true;
        }

        private boolean redo() {
            if (redo.isEmpty()) {
                return false;
            }
            undo.addLast(pixels.clone());
            pixels = redo.removeLast();
            return true;
        }

        private boolean dirty() {
            return !Arrays.equals(original, pixels);
        }
    }

    private static final class CarrierEditBuilder {
        private final String outputPath;
        private final String carrierContentKey;
        private final ArrayList<RegionEdit> regions =
                new ArrayList<>();

        private CarrierEditBuilder(
                String outputPath,
                String carrierContentKey) {
            this.outputPath = outputPath;
            this.carrierContentKey = carrierContentKey;
        }

        private void add(Layer layer) {
            if (!carrierContentKey.equals(
                    layer.carrierContentKey)) {
                throw new IllegalArgumentException(
                        "CONNECTION_TEXTURE_CARRIER_CONFLICT:"
                                + outputPath);
            }
            RegionEdit candidate = new RegionEdit(
                    layer.cellX,
                    layer.cellY,
                    layer.cellWidth,
                    layer.cellHeight,
                    layer.pixels);
            for (RegionEdit existing : regions) {
                if (existing.x() == candidate.x()
                        && existing.y() == candidate.y()
                        && existing.width()
                                == candidate.width()
                        && existing.height()
                                == candidate.height()) {
                    if (Arrays.equals(
                            existing.straightArgb(),
                            candidate.straightArgb())) {
                        return;
                    }
                    throw new IllegalArgumentException(
                            "CONNECTION_TEXTURE_CELL_CONFLICT:"
                                    + outputPath);
                }
            }
            regions.add(candidate);
        }

        private CarrierEditPlan freeze() {
            return new CarrierEditPlan(
                    outputPath,
                    carrierContentKey,
                    regions);
        }
    }
}
