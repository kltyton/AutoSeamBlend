package com.kltyton.autoseamblend.frontend.model;

import com.kltyton.autoseamblend.authoring.workbench.PaintTool;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/** 中文：静态 RGBA 槽位编辑器的不可变像素视图。 / English: Immutable pixel view for the static RGBA slot editor. */
public final class PaintViewModel {
    private static final List<Direction> FACE_ORDER = List.of(
            Direction.UP,
            Direction.DOWN,
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST);

    private final int width;
    private final int height;
    private final int[] straightArgb;
    private final List<Integer> slots;
    private final Direction selectedFace;
    private final int selectedSlot;
    private final boolean selectedSynthetic;
    private final PaintTool tool;
    private final int color;
    private final int brushSize;
    private final boolean editable;
    private final boolean canUndo;
    private final boolean canRedo;
    private final Component status;

    public PaintViewModel(
            int width,
            int height,
            int[] straightArgb,
            Direction selectedFace,
            List<Integer> slots,
            int selectedSlot,
            boolean selectedSynthetic,
            PaintTool tool,
            int color,
            int brushSize,
            boolean editable,
            boolean canUndo,
            boolean canRedo,
            Component status) {
        if (width <= 0
                || height <= 0
                || (long) width * height != straightArgb.length) {
            throw new IllegalArgumentException(
                    "invalid paint dimensions");
        }
        if (brushSize <= 0) {
            throw new IllegalArgumentException(
                    "brush size must be positive");
        }
        this.width = width;
        this.height = height;
        this.straightArgb = straightArgb.clone();
        this.selectedFace = Objects.requireNonNull(
                selectedFace,
                "selectedFace");
        this.slots = List.copyOf(
                Objects.requireNonNull(slots, "slots"));
        if (!this.slots.contains(selectedSlot)) {
            throw new IllegalArgumentException(
                    "selected paint slot is unavailable");
        }
        this.selectedSlot = selectedSlot;
        this.selectedSynthetic = selectedSynthetic;
        this.tool = Objects.requireNonNull(tool, "tool");
        this.color = color;
        this.brushSize = brushSize;
        this.editable = editable;
        this.canUndo = canUndo;
        this.canRedo = canRedo;
        this.status = Objects.requireNonNull(status, "status");
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int colorAt(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IndexOutOfBoundsException(
                    "paint coordinate outside image");
        }
        return straightArgb[y * width + x];
    }

    public int[] copyStraightArgb() {
        return straightArgb.clone();
    }

    public List<Integer> slots() {
        return slots;
    }

    public List<Direction> faces() {
        return FACE_ORDER;
    }

    public Direction selectedFace() {
        return selectedFace;
    }

    public int selectedSlot() {
        return selectedSlot;
    }

    public boolean selectedSynthetic() {
        return selectedSynthetic;
    }

    public PaintTool tool() {
        return tool;
    }

    public int color() {
        return color;
    }

    public int brushSize() {
        return brushSize;
    }

    public boolean editable() {
        return editable;
    }

    public boolean canUndo() {
        return canUndo;
    }

    public boolean canRedo() {
        return canRedo;
    }

    public Component status() {
        return status;
    }
}
