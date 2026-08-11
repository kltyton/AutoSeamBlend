package com.kltyton.autoseamblend.authoring.export;

import java.util.Objects;

/**
 * 中文：显式保存或导出前冻结的纯像素输入；不保留 Minecraft、Atlas 或 Loader 元数据对象。
 *
 * English: Pure pixel input frozen before an explicit save or export. It keeps
 * no Minecraft, atlas, or loader metadata objects.
 *
 * @param spriteId 中文：冻结源精灵的规范资源 ID。 / English: Canonical resource id of the frozen source sprite.
 * @param sheetWidth 中文：整张纹理表的像素宽度。 / English: Full sheet width in pixels.
 * @param sheetHeight 中文：整张纹理表的像素高度。 / English: Full sheet height in pixels.
 * @param frameWidth 中文：单帧宽度。 / English: Single frame width.
 * @param frameHeight 中文：单帧高度。 / English: Single frame height.
 * @param straightArgb 中文：直通 ARGB 像素数据（可能多帧）。 / English: Straight-ARGB pixel data (possibly multi-frame).
 * @param animated 中文：是否携带动画帧。 / English: Whether animation frames are present.
 * @param opaque 中文：像素是否完全不透明。 / English: Whether the pixels are fully opaque.
 * @param framedAlpha 中文：是否使用带透明通道的帧。 / English: Whether alpha frames are used.
 * @param provenance 中文：来源等级与诊断标识。 / English: Provenance tier and diagnostic marker.
 * @param sourceMetadata 中文：保留的源元数据字节（不含 Loader 对象）。 / English: Preserved source metadata bytes without Loader objects.
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
