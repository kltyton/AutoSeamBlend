package com.kltyton.autoseamblend.frontend.controller;

import com.kltyton.autoseamblend.authoring.workbench.PaintTool;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchMode;
import com.kltyton.autoseamblend.frontend.model.PreviewViewModel.NeighborPosition;
import java.util.Objects;

/** 中文：UILib 控件可以提交、但不能自行执行的受控动作集合。 / English: Controlled actions UILib widgets may submit but never execute themselves. */
public sealed interface WorkbenchAction {
    /** 中文：原生选择器的稳定语义类别。 / English: Stable semantic kind for native selectors. */
    enum NativeSelectorKind { MATCHING, CONNECTION }

    record AddTarget(String blockId) implements WorkbenchAction {
        public AddTarget {
            if (blockId == null || blockId.isBlank()) {
                throw new IllegalArgumentException(
                        "target block id must be nonblank");
            }
        }
    }

    record ShowMode(String entryKey, WorkbenchMode mode)
            implements WorkbenchAction {
        public ShowMode {
            if (entryKey == null || entryKey.isBlank()) {
                throw new IllegalArgumentException(
                        "workbench entry key must be nonblank");
            }
            mode = Objects.requireNonNull(mode, "mode");
        }
    }

    record ToggleNeighbor(NeighborPosition position)
            implements WorkbenchAction {
        public ToggleNeighbor {
            position = Objects.requireNonNull(position, "position");
        }
    }

    record ObserveFace(net.minecraft.core.Direction face)
            implements WorkbenchAction {
        public ObserveFace {
            face = Objects.requireNonNull(face, "face");
        }
    }

    /** 中文：循环中心接收方块状态。 / English: Cycles the center receiver block state. */
    record CycleReceiver() implements WorkbenchAction {}

    /** 中文：清空预览中已放置的全部邻接方块。 / English: Clears every placed neighbor from the preview. */
    record ClearNeighbors() implements WorkbenchAction {}

    record ChoosePaintTool(PaintTool tool)
            implements WorkbenchAction {
        public ChoosePaintTool {
            tool = Objects.requireNonNull(tool, "tool");
        }
    }

    record ChoosePaintColor(int straightArgb)
            implements WorkbenchAction {}

    record SelectPaintSlot(int slot)
            implements WorkbenchAction {}

    /** 中文：切换六面工作室当前编辑的方块面，不从纹理槽位推断方向。 / English: Selects the block face edited by the six-face studio without inferring it from a texture slot. */
    record SelectPaintFace(net.minecraft.core.Direction face)
            implements WorkbenchAction {
        public SelectPaintFace {
            face = Objects.requireNonNull(face, "face");
        }
    }

    record PaintStrokeStarted() implements WorkbenchAction {}

    record PaintPixel(int x, int y) implements WorkbenchAction {
        public PaintPixel {
            if (x < 0 || y < 0) {
                throw new IllegalArgumentException(
                        "paint coordinate must be nonnegative");
            }
        }
    }

    record PaintStrokeEnded() implements WorkbenchAction {}

    record CycleBrushSize() implements WorkbenchAction {}

    record UndoPaint() implements WorkbenchAction {}

    record RedoPaint() implements WorkbenchAction {}

    record SetNativeProperty(String fieldId, String valueToken)
            implements WorkbenchAction {
        public SetNativeProperty {
            fieldId = requireText(fieldId, "fieldId");
            valueToken = Objects.requireNonNull(
                    valueToken,
                    "valueToken");
        }
    }

    record SetNativeEntryId(String value) implements WorkbenchAction {
        public SetNativeEntryId { value = Objects.requireNonNull(value, "value"); }
    }
    record ToggleNativeFace(net.minecraft.core.Direction face) implements WorkbenchAction {
        public ToggleNativeFace { face = Objects.requireNonNull(face, "face"); }
    }
    record CycleNativeConnectionBasis() implements WorkbenchAction {}
    record CycleNativeRenderLayer() implements WorkbenchAction {}
    record SetNativeTintBlock(String blockId) implements WorkbenchAction {
        public SetNativeTintBlock {
            blockId = requireText(blockId, "blockId");
        }
    }
    record AddNativeSelectorBlock(NativeSelectorKind kind, String blockId)
            implements WorkbenchAction {
        public AddNativeSelectorBlock {
            kind = Objects.requireNonNull(kind, "kind");
            blockId = requireText(blockId, "blockId");
        }
    }
    record RemoveNativeSelectorEntry(NativeSelectorKind kind, int index)
            implements WorkbenchAction {
        public RemoveNativeSelectorEntry {
            kind = Objects.requireNonNull(kind, "kind");
            requireIndex(index);
        }
    }
    record MoveNativeSelectorEntry(NativeSelectorKind kind, int index, int delta)
            implements WorkbenchAction {
        public MoveNativeSelectorEntry {
            kind = Objects.requireNonNull(kind, "kind");
            requireIndex(index);
            if (delta == 0) throw new IllegalArgumentException("selector move delta must not be zero");
        }
    }
    record ToggleNativeSelectorProperty(
            NativeSelectorKind kind, int index, String propertyName, String value)
            implements WorkbenchAction {
        public ToggleNativeSelectorProperty {
            kind = Objects.requireNonNull(kind, "kind");
            requireIndex(index);
            propertyName = requireText(propertyName, "propertyName");
            value = requireText(value, "value");
        }
    }
    record CycleAthenaConnection() implements WorkbenchAction {}

    /** 中文：脏草稿只有显式确认后才允许丢弃。 / English: A dirty draft may be discarded only after explicit confirmation. */
    record CancelRequested(boolean discardConfirmed)
            implements WorkbenchAction {}

    record ExportRequested() implements WorkbenchAction {}

    record SaveRequested() implements WorkbenchAction {}

    private static String requireText(String value, String name) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return normalized;
    }

    private static void requireIndex(int index) {
        if (index < 0) throw new IllegalArgumentException("selector index must be nonnegative");
    }
}
