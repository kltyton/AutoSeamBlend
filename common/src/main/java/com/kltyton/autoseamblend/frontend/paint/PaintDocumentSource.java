package com.kltyton.autoseamblend.frontend.paint;

import com.kltyton.autoseamblend.engine.ownership.NativeSlotIntent;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * 中文：Loader 适配器交给公共绘画核心的不可变直通 ARGB 槽位来源。
 *
 * English: Immutable straight-ARGB slot input supplied to the common paint
 * core by a Loader adapter.
 */
public record PaintDocumentSource(
        String tilesExpression,
        List<Slot> slots) {
    public PaintDocumentSource {
        if (tilesExpression == null
                || tilesExpression.isBlank()) {
            throw new IllegalArgumentException(
                    "tilesExpression must not be blank");
        }
        slots = List.copyOf(
                Objects.requireNonNull(slots, "slots"));
        HashSet<PhysicalAddress> addresses = new HashSet<>();
        for (Slot slot : slots) {
            if (!addresses.add(new PhysicalAddress(
                    slot.logicalIndex(),
                    slot.physicalIndex()))) {
                throw new IllegalArgumentException(
                        "paint physical slots must be unique");
            }
        }
    }

    /**
     * 中文：像素采用非预乘 ARGB；不得直接传入 {@code ArgbImage} 的预乘像素。
     *
     * English: Pixels use straight ARGB and must not receive premultiplied
     * pixels copied directly from {@code ArgbImage}.
     */
    public record Slot(
            int logicalIndex,
            int physicalIndex,
            String outputPath,
            String carrierContentKey,
            int cellX,
            int cellY,
            int cellWidth,
            int cellHeight,
            NativeSlotIntent nativeIntent,
            boolean synthetic,
            int[] straightArgb) {
        public Slot {
            if (logicalIndex < 0 || physicalIndex < 0) {
                throw new IllegalArgumentException(
                        "slot indices must be nonnegative");
            }
            if (outputPath == null
                    || outputPath.isBlank()
                    || outputPath.indexOf('\\') >= 0) {
                throw new IllegalArgumentException(
                        "outputPath must be a normalized resource-pack path");
            }
            if (carrierContentKey == null
                    || carrierContentKey.isBlank()) {
                throw new IllegalArgumentException(
                        "carrierContentKey must not be blank");
            }
            if (cellX < 0
                    || cellY < 0
                    || cellWidth <= 0
                    || cellHeight <= 0) {
                throw new IllegalArgumentException(
                        "slot cell address must be positive");
            }
            nativeIntent = Objects.requireNonNull(
                    nativeIntent,
                    "nativeIntent");
            straightArgb = Objects.requireNonNull(
                            straightArgb,
                            "straightArgb")
                    .clone();
            if (straightArgb.length
                    != Math.multiplyExact(
                            cellWidth,
                            cellHeight)) {
                throw new IllegalArgumentException(
                        "slot pixels do not match cell dimensions");
            }
        }

        @Override
        public int[] straightArgb() {
            return straightArgb.clone();
        }
    }

    private record PhysicalAddress(
            int logicalIndex,
            int physicalIndex) {}
}
