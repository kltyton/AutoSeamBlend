package com.kltyton.autoseamblend.authoring.export;

import java.util.Objects;

/**
 * 中文：显式保存或导出前冻结的纯像素输入；不保留 Minecraft、Atlas 或 Loader 元数据对象。
 *
 * English: Pure pixel input frozen before an explicit save or export. It keeps
 * no Minecraft, atlas, or loader metadata objects.
 */
public record ExportSourceSnapshot(
        String spriteId,
        int sheetWidth,
        int sheetHeight,
        int frameWidth,
        int frameHeight,
        int[] straightArgb,
        boolean animated,
        boolean opaque,
        boolean framedAlpha,
        String provenance,
        byte[] sourceMetadata) {
    public ExportSourceSnapshot {
        requireText(spriteId, "spriteId");
        requireText(provenance, "provenance");
        if (sheetWidth <= 0
                || sheetHeight <= 0
                || frameWidth <= 0
                || frameHeight <= 0
                || sheetWidth % frameWidth != 0
                || sheetHeight % frameHeight != 0) {
            throw new IllegalArgumentException(
                    "invalid export source dimensions");
        }
        straightArgb = Objects.requireNonNull(
                        straightArgb,
                        "straightArgb")
                .clone();
        sourceMetadata = Objects.requireNonNull(
                        sourceMetadata,
                        "sourceMetadata")
                .clone();
        if (straightArgb.length
                != Math.multiplyExact(sheetWidth, sheetHeight)) {
            throw new IllegalArgumentException(
                    "export source pixel count differs from dimensions");
        }
    }

    @Override
    public int[] straightArgb() {
        return straightArgb.clone();
    }

    @Override
    public byte[] sourceMetadata() {
        return sourceMetadata.clone();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be blank");
        }
    }
}
