package com.kltyton.autoseamblend.compat.continuity.authoring.materialize;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.selection.method.MethodSlotDomain;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileMaterializer;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe;
import com.kltyton.autoseamblend.texture.io.StraightArgbPngEncoder;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;

/**
 * 中文：使用同一冻结源像素和项目自有槽位配方生成预览或 PNG 字节。
 * English: Materializes preview pixels or PNG bytes from one frozen source and a project-owned slot recipe.
 *
 * <p>The native Continuity adapter resolves the recipe and passes it in; this class owns only
 * deterministic pixel and animation-sheet materialization.</p>
 */
public final class ContinuityTileMaterializer {
    private ContinuityTileMaterializer() {}

    public static MaterializedTile materialize(
            ConnectionMethod concreteMethod,
            int slot,
            int width,
            int height,
            int[] straightArgb,
            OverlayCutoutProfile overlayProfile,
            GeneratedTileRecipe recipe) {
        Objects.requireNonNull(concreteMethod, "concreteMethod");
        Objects.requireNonNull(straightArgb, "straightArgb");
        Objects.requireNonNull(overlayProfile, "overlayProfile");
        Objects.requireNonNull(recipe, "recipe");
        int[] pixels = GeneratedTileMaterializer.materializeStraightArgb(
                width,
                height,
                straightArgb,
                recipe,
                overlayProfile);
        return new MaterializedTile(concreteMethod, slot, width, height, pixels);
    }

    public static byte[] materializePng(
            ConnectionMethod concreteMethod,
            int slot,
            int width,
            int height,
            int[] straightArgb,
            OverlayCutoutProfile overlayProfile,
            GeneratedTileRecipe recipe)
            throws IOException {
        MaterializedTile tile = materialize(
                concreteMethod,
                slot,
                width,
                height,
                straightArgb,
                overlayProfile,
                recipe);
        return StraightArgbPngEncoder.encode(tile.width(), tile.height(), tile.straightArgb());
    }

    /**
     * 中文：逐帧实体化完整动画表，避免把相邻动画帧误当作同一张连接纹理。
     * English: Materializes a complete animation sheet frame by frame so adjacent frames are never treated as one connected texture.
     *
     * <p>Time complexity is {@code O(sheetWidth * sheetHeight)} and auxiliary space is one
     * output sheet plus one source/generated frame.</p>
     */
    public static MaterializedSheet materializeSheet(
            ConnectionMethod concreteMethod,
            int slot,
            int sheetWidth,
            int sheetHeight,
            int frameWidth,
            int frameHeight,
            int[] straightArgb,
            OverlayCutoutProfile overlayProfile,
            GeneratedTileRecipe recipe) {
        return materializeSheet(
                concreteMethod,
                slot,
                sheetWidth,
                sheetHeight,
                frameWidth,
                frameHeight,
                straightArgb,
                overlayProfile,
                Optional.empty(),
                recipe);
    }

    /** 中文：仅转换元数据实际引用的帧，未引用的共享表单元逐像素保留。 / English: Transforms only metadata-referenced frames and preserves unused sheet cells byte-for-byte. */
    public static MaterializedSheet materializeSheet(
            ConnectionMethod concreteMethod,
            int slot,
            int sheetWidth,
            int sheetHeight,
            int frameWidth,
            int frameHeight,
            int[] straightArgb,
            OverlayCutoutProfile overlayProfile,
            Optional<AnimationMetadataSection> animation,
            GeneratedTileRecipe recipe) {
        Objects.requireNonNull(concreteMethod, "concreteMethod");
        Objects.requireNonNull(straightArgb, "straightArgb");
        Objects.requireNonNull(overlayProfile, "overlayProfile");
        Objects.requireNonNull(animation, "animation");
        Objects.requireNonNull(recipe, "recipe");
        if (sheetWidth <= 0
                || sheetHeight <= 0
                || frameWidth <= 0
                || frameHeight <= 0
                || sheetWidth % frameWidth != 0
                || sheetHeight % frameHeight != 0
                || straightArgb.length != Math.multiplyExact(sheetWidth, sheetHeight)) {
            throw new IllegalArgumentException("invalid animation sheet geometry");
        }
        int columns = sheetWidth / frameWidth;
        int rows = sheetHeight / frameHeight;
        int frameCount = Math.multiplyExact(columns, rows);
        int[] frameIndices = animation
                .flatMap(AnimationMetadataSection::frames)
                .map(frames -> frames.stream()
                        .mapToInt(frame -> frame.index())
                        .distinct()
                        .toArray())
                .filter(indices -> indices.length > 0)
                .orElseGet(() -> IntStream.range(0, frameCount).toArray());
        if (IntStream.of(frameIndices).anyMatch(frame -> frame < 0 || frame >= frameCount)) {
            throw new IllegalArgumentException("animation frame index is outside the sheet");
        }
        int[] output = straightArgb.clone();
        int[] sourceFrame = new int[Math.multiplyExact(frameWidth, frameHeight)];
        for (int frame : frameIndices) {
            int frameX = frame % columns * frameWidth;
            int frameY = frame / columns * frameHeight;
            copyFrame(
                    straightArgb,
                    sheetWidth,
                    frameX,
                    frameY,
                    frameWidth,
                    frameHeight,
                    sourceFrame);
            MaterializedTile generated = materialize(
                    concreteMethod,
                    slot,
                    frameWidth,
                    frameHeight,
                    sourceFrame,
                    overlayProfile,
                    recipe);
            writeFrame(
                    generated.straightArgb(),
                    output,
                    sheetWidth,
                    frameX,
                    frameY,
                    frameWidth,
                    frameHeight);
        }
        return new MaterializedSheet(
                concreteMethod,
                slot,
                sheetWidth,
                sheetHeight,
                frameWidth,
                frameHeight,
                output);
    }

    public static byte[] materializeSheetPng(
            ConnectionMethod concreteMethod,
            int slot,
            int sheetWidth,
            int sheetHeight,
            int frameWidth,
            int frameHeight,
            int[] straightArgb,
            OverlayCutoutProfile overlayProfile,
            GeneratedTileRecipe recipe)
            throws IOException {
        return materializeSheetPng(
                concreteMethod,
                slot,
                sheetWidth,
                sheetHeight,
                frameWidth,
                frameHeight,
                straightArgb,
                overlayProfile,
                Optional.empty(),
                recipe);
    }

    public static byte[] materializeSheetPng(
            ConnectionMethod concreteMethod,
            int slot,
            int sheetWidth,
            int sheetHeight,
            int frameWidth,
            int frameHeight,
            int[] straightArgb,
            OverlayCutoutProfile overlayProfile,
            Optional<AnimationMetadataSection> animation,
            GeneratedTileRecipe recipe)
            throws IOException {
        MaterializedSheet sheet = materializeSheet(
                concreteMethod,
                slot,
                sheetWidth,
                sheetHeight,
                frameWidth,
                frameHeight,
                straightArgb,
                overlayProfile,
                animation,
                recipe);
        return StraightArgbPngEncoder.encode(
                sheet.sheetWidth(),
                sheet.sheetHeight(),
                sheet.straightArgb());
    }

    private static void copyFrame(
            int[] sheet,
            int sheetWidth,
            int frameX,
            int frameY,
            int frameWidth,
            int frameHeight,
            int[] destination) {
        for (int y = 0; y < frameHeight; y++) {
            System.arraycopy(
                    sheet,
                    (frameY + y) * sheetWidth + frameX,
                    destination,
                    y * frameWidth,
                    frameWidth);
        }
    }

    private static void writeFrame(
            int[] frame,
            int[] sheet,
            int sheetWidth,
            int frameX,
            int frameY,
            int frameWidth,
            int frameHeight) {
        for (int y = 0; y < frameHeight; y++) {
            System.arraycopy(
                    frame,
                    y * frameWidth,
                    sheet,
                    (frameY + y) * sheetWidth + frameX,
                    frameWidth);
        }
    }

    /** 中文：不持有 Minecraft、Atlas 或引擎对象的不可变像素快照。 / English: Immutable pixel snapshot containing no Minecraft, atlas, or engine objects. */
    public record MaterializedTile(
            ConnectionMethod method,
            int slot,
            int width,
            int height,
            int[] straightArgb) {
        public MaterializedTile {
            method = Objects.requireNonNull(method, "method");
            straightArgb = Objects.requireNonNull(straightArgb, "straightArgb").clone();
            if (method == ConnectionMethod.AUTO || method == ConnectionMethod.NONE) {
                throw new IllegalArgumentException("materialized method must have native slots");
            }
            if (slot < 0 || !MethodSlotDomain.of(method).slots().contains(slot)) {
                throw new IllegalArgumentException("slot is outside method domain");
            }
            if (width <= 0
                    || height <= 0
                    || (long) width * height != straightArgb.length) {
                throw new IllegalArgumentException("pixel count does not match dimensions");
            }
        }

        @Override
        public int[] straightArgb() {
            return straightArgb.clone();
        }
    }

    /** 中文：保留原动画几何的完整槽位像素。 / English: Complete slot pixels retaining the source animation geometry. */
    public record MaterializedSheet(
            ConnectionMethod method,
            int slot,
            int sheetWidth,
            int sheetHeight,
            int frameWidth,
            int frameHeight,
            int[] straightArgb) {
        public MaterializedSheet {
            method = Objects.requireNonNull(method, "method");
            straightArgb = Objects.requireNonNull(straightArgb, "straightArgb").clone();
            if (method == ConnectionMethod.AUTO || method == ConnectionMethod.NONE) {
                throw new IllegalArgumentException("materialized method must have native slots");
            }
            if (slot < 0 || !MethodSlotDomain.of(method).slots().contains(slot)) {
                throw new IllegalArgumentException("slot is outside method domain");
            }
            if (sheetWidth <= 0
                    || sheetHeight <= 0
                    || frameWidth <= 0
                    || frameHeight <= 0
                    || sheetWidth % frameWidth != 0
                    || sheetHeight % frameHeight != 0
                    || straightArgb.length != Math.multiplyExact(sheetWidth, sheetHeight)) {
                throw new IllegalArgumentException("pixel count does not match animation geometry");
            }
        }

        @Override
        public int[] straightArgb() {
            return straightArgb.clone();
        }
    }
}
