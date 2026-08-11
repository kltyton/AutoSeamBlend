package com.kltyton.autoseamblend.authoring.materialize;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.ownership.NativeSlotIntent;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 中文：一个原生连接纹理文档的可编辑逐槽像素来源，不把目标方块基础面误当成连接纹理。
 *
 * English:
 * Editable per-slot pixel sources for one native connected-texture document,
 * without treating the target block's base face as a connection texture.
 *
 * @param family 中文：目标引擎族。 / English: Target engine family.
 * @param tilesExpression 中文：原生 tiles 表达式。 / English: Native tiles expression.
 * @param slots 中文：排序后的可编辑逐槽像素来源。 / English: Sorted editable per-slot pixel sources.
 */
public record ConnectionTextureSet(
        EngineFamily family,
        String tilesExpression,
        List<Slot> slots) {
    public ConnectionTextureSet {
        Objects.requireNonNull(family, "family");
        if (tilesExpression == null
                || tilesExpression.isBlank()) {
            throw new IllegalArgumentException(
                    "tilesExpression must not be blank");
        }
        slots = List.copyOf(
                Objects.requireNonNull(
                                slots,
                                "slots")
                        .stream()
                        .sorted(Comparator.comparingInt(
                                        Slot::logicalIndex)
                                .thenComparingInt(
                                        Slot::physicalIndex))
                        .toList());
        if (slots.isEmpty()
                || slots.stream()
                        .map(slot -> List.of(
                                slot.logicalIndex(),
                                slot.physicalIndex()))
                        .distinct()
                        .count()
                        != slots.size()) {
            throw new IllegalArgumentException(
                    "connection texture physical slots must be non-empty and unique");
        }
    }

    public record Slot(
            int index,
            int logicalIndex,
            int physicalIndex,
            CarrierKind carrierKind,
            String outputPath,
            int cellX,
            int cellY,
            int cellWidth,
            int cellHeight,
            NativeSlotIntent nativeIntent,
            boolean synthetic,
            TextureSourceSnapshot source) {
        /**
         * 中文：兼容每槽独立 PNG 的简写构造；物理地址覆盖完整首帧。
         *
         * English: Convenience constructor for an independent slot PNG whose
         * physical address covers the complete first frame.
         */
        public Slot(
                int index,
                String outputPath,
                NativeSlotIntent nativeIntent,
                boolean synthetic,
                TextureSourceSnapshot source) {
            this(
                    index,
                    index,
                    0,
                    CarrierKind.INDEPENDENT_PNG,
                    outputPath,
                    0,
                    0,
                    Objects.requireNonNull(
                                    source,
                                    "source")
                            .frameWidth(),
                    source.frameHeight(),
                    nativeIntent,
                    synthetic,
                    source);
        }

        public Slot {
            if (index < 0
                    || logicalIndex < 0
                    || physicalIndex < 0) {
                throw new IllegalArgumentException(
                        "slot indices must be non-negative");
            }
            Objects.requireNonNull(
                    carrierKind,
                    "carrierKind");
            if (outputPath == null
                    || outputPath.isBlank()
                    || outputPath.indexOf('\\') >= 0) {
                throw new IllegalArgumentException(
                        "outputPath must be a normalized resource-pack path");
            }
            if (cellX < 0
                    || cellY < 0
                    || cellWidth <= 0
                    || cellHeight <= 0) {
                throw new IllegalArgumentException(
                        "slot cell address must be positive");
            }
            Objects.requireNonNull(
                    nativeIntent,
                    "nativeIntent");
            Objects.requireNonNull(source, "source");
            if (cellX + cellWidth
                            > source.frameWidth()
                    || cellY + cellHeight
                            > source.frameHeight()) {
                throw new IllegalArgumentException(
                        "slot cell exceeds the carrier frame");
            }
            if (carrierKind
                            == CarrierKind.INDEPENDENT_PNG
                    && (physicalIndex != 0
                            || cellX != 0
                            || cellY != 0
                            || cellWidth
                                    != source.frameWidth()
                            || cellHeight
                                    != source.frameHeight())) {
                throw new IllegalArgumentException(
                        "independent PNG slot must address its complete frame");
            }
        }

        public boolean pngPresent() {
            return nativeIntent.pngResourcePresent();
        }
    }

    /**
     * 中文：区分每槽独立 PNG 与多个逻辑槽共享的原生纹理表载体。
     *
     * English: Distinguishes independent slot PNGs from native sheets shared
     * by multiple logical slots.
     */
    public enum CarrierKind {
        INDEPENDENT_PNG,
        SHARED_SHEET
    }
}
