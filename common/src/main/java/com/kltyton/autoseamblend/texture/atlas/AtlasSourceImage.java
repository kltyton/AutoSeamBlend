package com.kltyton.autoseamblend.texture.atlas;

import com.kltyton.autoseamblend.authoring.export.ExportSourceSnapshot;
import com.kltyton.autoseamblend.runtime.surface.SurfaceSourceProvenance;
import com.kltyton.autoseamblend.texture.budget.TextureImageBudget;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;

/**
 * 中文：在 Atlas 枚举前冻结的精确来源像素与纹理元数据。
 *
 * English: Exact source pixels and texture metadata frozen before atlas enumeration.
 */
public record AtlasSourceImage(
        Identifier spriteId,
        int sheetWidth,
        int sheetHeight,
        int frameWidth,
        int frameHeight,
        int[] straightArgb,
        boolean animated,
        boolean opaque,
        boolean framedAlpha,
        SurfaceSourceProvenance provenance,
        byte[] sourceMetadata,
        Optional<AnimationMetadataSection> animation,
        Optional<TextureMetadataSection> texture) {
    public AtlasSourceImage {
        Objects.requireNonNull(spriteId, "spriteId");
        Objects.requireNonNull(provenance, "provenance");
        byte[] metadata = Objects.requireNonNull(sourceMetadata, "sourceMetadata");
        TextureImageBudget.DEFAULT.requireMetadataLength(metadata.length);
        sourceMetadata = metadata.clone();
        animation = Objects.requireNonNull(animation, "animation");
        texture = Objects.requireNonNull(texture, "texture");
        int expectedPixels = TextureImageBudget.DEFAULT.requireImage(
                sheetWidth,
                sheetHeight,
                frameWidth,
                frameHeight);
        int[] pixels = Objects.requireNonNull(straightArgb, "straightArgb");
        TextureImageBudget.DEFAULT.requirePixelArrayLength(
                pixels.length,
                expectedPixels);
        straightArgb = pixels.clone();
    }

    @Override
    public int[] straightArgb() {
        return straightArgb.clone();
    }

    @Override
    public byte[] sourceMetadata() {
        return sourceMetadata.clone();
    }

    /**
     * 中文：在资源捕获边界转为 Common 纯像素快照。
     *
     * English: Converts at the resource-capture boundary into a common pure-pixel snapshot.
     */
    public ExportSourceSnapshot exportSnapshot() {
        return new ExportSourceSnapshot(
                spriteId.toString(),
                sheetWidth,
                sheetHeight,
                frameWidth,
                frameHeight,
                straightArgb,
                animated,
                opaque,
                framedAlpha,
                provenance.name(),
                sourceMetadata);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AtlasSourceImage value)) return false;
        return sheetWidth == value.sheetWidth
                && sheetHeight == value.sheetHeight
                && frameWidth == value.frameWidth
                && frameHeight == value.frameHeight
                && animated == value.animated
                && opaque == value.opaque
                && framedAlpha == value.framedAlpha
                && spriteId.equals(value.spriteId)
                && Arrays.equals(straightArgb, value.straightArgb)
                && provenance == value.provenance
                && Arrays.equals(sourceMetadata, value.sourceMetadata)
                && animation.equals(value.animation)
                && texture.equals(value.texture);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                spriteId,
                sheetWidth,
                sheetHeight,
                frameWidth,
                frameHeight,
                animated,
                opaque,
                framedAlpha,
                provenance,
                animation,
                texture);
        result = 31 * result + Arrays.hashCode(straightArgb);
        return 31 * result + Arrays.hashCode(sourceMetadata);
    }

}
