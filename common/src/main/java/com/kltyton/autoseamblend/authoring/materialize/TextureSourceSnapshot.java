package com.kltyton.autoseamblend.authoring.materialize;

import com.kltyton.autoseamblend.mixin.minecraft.SpriteContentsImageAccessor;
import com.kltyton.autoseamblend.runtime.render.ProceduralConnectionPlan;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileMaterializer;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe;
import com.kltyton.autoseamblend.texture.generation.ProceduralPlanMaterializer;
import com.kltyton.autoseamblend.texture.io.NativeArgb;
import com.kltyton.autoseamblend.texture.io.StraightArgbPngEncoder;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 中文：在渲染线程捕获一个已解码源纹理；PNG 编码和生成纹理块只使用此不可变副本，因此显式导出绝不会在工作线程读取 Atlas 或资源管理器。
 *
 * English:
 * Render-thread capture of one decoded source texture.
 *
 * <p>PNG encoding and generated-tile work operate only on this immutable copy,
 * so explicit export never reads the atlas or resource manager from a worker
 * thread.
 */
public final class TextureSourceSnapshot {
    private static final int MAX_DIMENSION = 4096;
    private static final int MAX_PIXELS =
            MAX_DIMENSION * MAX_DIMENSION;
    private static final int MAX_METADATA_BYTES =
            1024 * 1024;

    private final String sourceTextureId;
    private final int sheetWidth;
    private final int sheetHeight;
    private final int frameWidth;
    private final int frameHeight;
    private final boolean animated;
    private final byte[] sourceMetadata;
    private final int[] frameIndices;
    private final int[] straightArgb;

    private TextureSourceSnapshot(
            String sourceTextureId,
            int sheetWidth,
            int sheetHeight,
            int frameWidth,
            int frameHeight,
            boolean animated,
            byte[] sourceMetadata,
            int[] frameIndices,
            int[] straightArgb) {
        this.sourceTextureId = Objects.requireNonNull(
                sourceTextureId,
                "sourceTextureId");
        this.sheetWidth = sheetWidth;
        this.sheetHeight = sheetHeight;
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.animated = animated;
        this.sourceMetadata = Objects.requireNonNull(
                sourceMetadata,
                "sourceMetadata").clone();
        this.frameIndices = frameIndices.clone();
        this.straightArgb = straightArgb.clone();
    }

    public static TextureSourceSnapshot capture(
            SpriteContents contents) {
        try {
            return capture(contents, null);
        } catch (IOException impossible) {
            throw new IllegalStateException(
                    "metadata-free source capture failed",
                    impossible);
        }
    }

    public static TextureSourceSnapshot capture(
            SpriteContents contents,
            ResourceManager resources) throws IOException {
        Objects.requireNonNull(contents, "contents");
        NativeImage image =
                ((SpriteContentsImageAccessor) contents)
                        .autoseamblend$originalImage();
        int sheetWidth = image.getWidth();
        int sheetHeight = image.getHeight();
        int frameWidth = contents.width();
        int frameHeight = contents.height();
        validateDimensions(
                sheetWidth,
                sheetHeight,
                frameWidth,
                frameHeight);

        int[] pixels = new int[Math.multiplyExact(
                sheetWidth,
                sheetHeight)];
        for (int y = 0; y < sheetHeight; y++) {
            for (int x = 0; x < sheetWidth; x++) {
                pixels[y * sheetWidth + x] =
                        NativeArgb.toIr(
                                image.getPixelRGBA(x, y));
            }
        }
        int[] frames = contents.getUniqueFrames().count() > 1
                ? contents.getUniqueFrames()
                        .toArray()
                : new int[] {0};
        if (frames.length == 0) {
            frames = new int[] {0};
        }
        validateFrames(
                frames,
                sheetWidth / frameWidth,
                sheetHeight / frameHeight);
        byte[] metadata = resources == null
                ? new byte[0]
                : readMetadata(
                        contents.name(),
                        resources);
        return new TextureSourceSnapshot(
                contents.name().toString(),
                sheetWidth,
                sheetHeight,
                frameWidth,
                frameHeight,
                contents.getUniqueFrames().count() > 1,
                metadata,
                frames,
                pixels);
    }

    /**
     * 中文：从资源栈直接捕获原生 PNG 载体；这允许编辑未作为普通 Atlas 精灵暴露的共享纹理表。
     *
     * English: Captures a native PNG carrier directly from the resource stack,
     * including shared sheets that are not exposed as ordinary atlas sprites.
     */
    public static TextureSourceSnapshot capture(
            ResourceManager resources,
            ResourceLocation textureId) throws IOException {
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(textureId, "textureId");
        ResourceLocation file = SpriteSource.TEXTURE_ID_CONVERTER
                .idToFile(textureId);
        Resource resource = resources.getResource(file)
                .orElseThrow(() -> new IOException(
                        "SOURCE_TEXTURE_RESOURCE_MISSING:"
                                + textureId));
        try (var input = resource.open();
                NativeImage image = NativeImage.read(input)) {
            int sheetWidth = image.getWidth();
            int sheetHeight = image.getHeight();
            Optional<AnimationMetadataSection> animation =
                    resource.metadata().getSection(
                            AnimationMetadataSection.SERIALIZER);
            FrameSize frame = animation
                    .map(value -> value.calculateFrameSize(
                            sheetWidth,
                            sheetHeight))
                    .orElseGet(() -> new FrameSize(
                            sheetWidth,
                            sheetHeight));
            validateDimensions(
                    sheetWidth,
                    sheetHeight,
                    frame.width(),
                    frame.height());
            int[] pixels = new int[Math.multiplyExact(
                    sheetWidth,
                    sheetHeight)];
            for (int y = 0; y < sheetHeight; y++) {
                for (int x = 0; x < sheetWidth; x++) {
                    pixels[y * sheetWidth + x] =
                            NativeArgb.toIr(
                                    image.getPixelRGBA(x, y));
                }
            }
            int frameCount = Math.multiplyExact(
                    sheetWidth / frame.width(),
                    sheetHeight / frame.height());
            it.unimi.dsi.fastutil.ints.IntArrayList frameIndices =
                    new it.unimi.dsi.fastutil.ints.IntArrayList();
            animation.ifPresent(value -> value.forEachFrame(
                    (index, time) -> frameIndices.add(index)));
            int[] frames;
            if (frameIndices.isEmpty()) {
                frames = sequentialFrames(frameCount);
            } else {
                frames = frameIndices.stream()
                        .distinct()
                        .mapToInt(Integer::intValue)
                        .toArray();
            }
            validateFrames(
                    frames,
                    sheetWidth / frame.width(),
                    sheetHeight / frame.height());
            return new TextureSourceSnapshot(
                    textureId.toString(),
                    sheetWidth,
                    sheetHeight,
                    frame.width(),
                    frame.height(),
                    animation.isPresent(),
                    readMetadata(textureId, resources),
                    frames,
                    pixels);
        } catch (IllegalArgumentException
                | IllegalStateException
                | ArithmeticException exception) {
            throw new IOException(
                    "SOURCE_TEXTURE_RESOURCE_INVALID:"
                            + textureId,
                    exception);
        }
    }

    public MaterializedTexture materialize(
            GeneratedTileRecipe recipe) throws IOException {
        Objects.requireNonNull(recipe, "recipe");
        return materializeFrames(
                (sourceFrame) ->
                        GeneratedTileMaterializer
                                .materializeStraightArgb(
                                        frameWidth,
                                        frameHeight,
                                        sourceFrame,
                                        recipe));
    }

    /** 中文：用冻结的表面轮廓实体化全部动画帧。 / English: Materializes every animation frame with the frozen surface profile. */
    public MaterializedTexture materialize(
            GeneratedTileRecipe recipe,
            OverlayCutoutProfile overlayProfile)
            throws IOException {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(
                overlayProfile,
                "overlayProfile");
        return materializeFrames(
                sourceFrame -> GeneratedTileMaterializer
                        .materializeStraightArgb(
                                frameWidth,
                                frameHeight,
                                sourceFrame,
                                recipe,
                                overlayProfile));
    }

    /**
     * 中文：在内存中把每个动画帧转换为一个可编辑连接纹理槽位，不编码 PNG，也不访问资源系统。
     *
     * English:
     * Transforms every animation frame into one editable connected-texture
     * slot in memory, without PNG encoding or resource access.
     */
    public TextureSourceSnapshot transformTo(
            String outputTextureId,
            GeneratedTileRecipe recipe) {
        return transformTo(
                outputTextureId,
                recipe,
                Optional.empty());
    }

    /** 中文：以冻结的表面轮廓转换每个动画帧，供编辑草稿与运行时 Atlas 共用。 / English: Transforms every animation frame with the frozen surface profile shared by authoring and the runtime Atlas. */
    public TextureSourceSnapshot transformTo(
            String outputTextureId,
            GeneratedTileRecipe recipe,
            OverlayCutoutProfile overlayProfile) {
        return transformTo(
                outputTextureId,
                recipe,
                Optional.of(Objects.requireNonNull(
                        overlayProfile,
                        "overlayProfile")));
    }

    private TextureSourceSnapshot transformTo(
            String outputTextureId,
            GeneratedTileRecipe recipe,
            Optional<OverlayCutoutProfile> overlayProfile) {
        if (outputTextureId == null
                || outputTextureId.isBlank()) {
            throw new IllegalArgumentException(
                    "outputTextureId must not be blank");
        }
        Objects.requireNonNull(recipe, "recipe");
        int columns = sheetWidth / frameWidth;
        int[] output = straightArgb.clone();
        for (int frame : frameIndices) {
            int originX =
                    frame % columns * frameWidth;
            int originY =
                    frame / columns * frameHeight;
            int[] generated =
                    overlayProfile
                            .map(profile -> GeneratedTileMaterializer
                                    .materializeStraightArgb(
                                            frameWidth,
                                            frameHeight,
                                            sourceFrame(
                                                    frame,
                                                    columns),
                                            recipe,
                                            profile))
                            .orElseGet(() -> GeneratedTileMaterializer
                                    .materializeStraightArgb(
                                            frameWidth,
                                            frameHeight,
                                            sourceFrame(
                                                    frame,
                                                    columns),
                                            recipe));
            for (int y = 0;
                    y < frameHeight;
                    y++) {
                System.arraycopy(
                        generated,
                        y * frameWidth,
                        output,
                        (originY + y) * sheetWidth
                                + originX,
                        frameWidth);
            }
        }
        return new TextureSourceSnapshot(
                outputTextureId,
                sheetWidth,
                sheetHeight,
                frameWidth,
                frameHeight,
                animated,
                sourceMetadata,
                frameIndices,
                output);
    }

    /**
     * 中文：以程序连接计划生成一个不可变编辑草稿，同时保留原始动画布局和元数据。
     *
     * English: Produces an immutable procedural connected-texture draft while
     * retaining the source animation layout and metadata.
     */
    public TextureSourceSnapshot transformTo(
            String outputTextureId,
            ProceduralConnectionPlan plan) {
        if (outputTextureId == null
                || outputTextureId.isBlank()) {
            throw new IllegalArgumentException(
                    "outputTextureId must not be blank");
        }
        Objects.requireNonNull(plan, "plan");
        int columns = sheetWidth / frameWidth;
        int[] output = straightArgb.clone();
        for (int frame : frameIndices) {
            int originX = frame % columns * frameWidth;
            int originY = frame / columns * frameHeight;
            int[] generated = ProceduralPlanMaterializer
                    .materializeStraightArgb(
                            frameWidth,
                            frameHeight,
                            sourceFrame(frame, columns),
                            plan);
            for (int y = 0; y < frameHeight; y++) {
                System.arraycopy(
                        generated,
                        y * frameWidth,
                        output,
                        (originY + y) * sheetWidth
                                + originX,
                        frameWidth);
            }
        }
        return new TextureSourceSnapshot(
                outputTextureId,
                sheetWidth,
                sheetHeight,
                frameWidth,
                frameHeight,
                animated,
                sourceMetadata,
                frameIndices,
                output);
    }

    public MaterializedTexture materialize(
            ProceduralConnectionPlan plan) throws IOException {
        Objects.requireNonNull(plan, "plan");
        return materializeFrames(
                (sourceFrame) ->
                        ProceduralPlanMaterializer
                                .materializeStraightArgb(
                                        frameWidth,
                                        frameHeight,
                                        sourceFrame,
                        plan));
    }

    public int firstFrameIndex() {
        return frameIndices[0];
    }

    public int[] firstFrameStraightArgb() {
        return sourceFrame(
                firstFrameIndex(),
                sheetWidth / frameWidth);
    }

    /**
     * 中文：读取首个动画帧内的一个物理单元，不把共享纹理表误作单个可编辑帧。
     *
     * English: Reads one physical cell inside the first animation frame instead
     * of treating an entire shared sheet as one editable frame.
     */
    public int[] firstFrameRegion(
            int x,
            int y,
            int width,
            int height) {
        validateRegion(x, y, width, height);
        int frame = firstFrameIndex();
        int columns = sheetWidth / frameWidth;
        int frameOriginX = frame % columns * frameWidth;
        int frameOriginY = frame / columns * frameHeight;
        int[] region = new int[Math.multiplyExact(
                width,
                height)];
        for (int row = 0; row < height; row++) {
            System.arraycopy(
                    straightArgb,
                    (frameOriginY + y + row) * sheetWidth
                            + frameOriginX + x,
                    region,
                    row * width,
                    width);
        }
        return region;
    }

    /**
     * 中文：在一个不可变载体副本中合并多个首帧物理单元编辑；未触及单元和其他动画帧保持原样。
     *
     * English: Merges multiple physical-cell edits into one immutable carrier
     * copy while preserving untouched cells and all other animation frames.
     */
    public TextureSourceSnapshot withRegions(
            List<RegionEdit> edits) {
        edits = List.copyOf(
                Objects.requireNonNull(edits, "edits"));
        int[] output = straightArgb.clone();
        int frame = firstFrameIndex();
        int columns = sheetWidth / frameWidth;
        int frameOriginX = frame % columns * frameWidth;
        int frameOriginY = frame / columns * frameHeight;
        for (RegionEdit edit : edits) {
            validateRegion(
                    edit.x(),
                    edit.y(),
                    edit.width(),
                    edit.height());
            int[] pixels = edit.straightArgb();
            for (int row = 0;
                    row < edit.height();
                    row++) {
                System.arraycopy(
                        pixels,
                        row * edit.width(),
                        output,
                        (frameOriginY + edit.y() + row)
                                        * sheetWidth
                                + frameOriginX + edit.x(),
                        edit.width());
            }
        }
        return new TextureSourceSnapshot(
                sourceTextureId,
                sheetWidth,
                sheetHeight,
                frameWidth,
                frameHeight,
                animated,
                sourceMetadata,
                frameIndices,
                output);
    }

    /** 中文：一次编码合并后的完整载体。 / English: Encodes a merged complete carrier exactly once. */
    public MaterializedTexture materializeCarrier()
            throws IOException {
        return new MaterializedTexture(
                sourceTextureId,
                sheetWidth,
                sheetHeight,
                frameWidth,
                frameHeight,
                animated,
                StraightArgbPngEncoder.encode(
                        sheetWidth,
                        sheetHeight,
                        straightArgb));
    }

    /**
     * 中文：显式编辑器保存操作；只替换已选帧，并保留未改动的动画帧和原始纹理表布局。
     *
     * English:
     * Explicit editor-save operation. Only the selected frame is replaced;
     * untouched animation frames and the original sheet layout are preserved.
     */
    public MaterializedTexture replaceFirstFrame(
            int[] frameStraightArgb)
            throws IOException {
        frameStraightArgb =
                Objects.requireNonNull(
                                frameStraightArgb,
                                "frameStraightArgb")
                        .clone();
        if (frameStraightArgb.length
                != Math.multiplyExact(
                        frameWidth,
                        frameHeight)) {
            throw new IllegalArgumentException(
                    "edited frame pixel count differs from source frame");
        }
        int columns =
                sheetWidth / frameWidth;
        int frame = firstFrameIndex();
        int originX =
                frame % columns * frameWidth;
        int originY =
                frame / columns * frameHeight;
        int[] output =
                straightArgb.clone();
        for (int y = 0;
                y < frameHeight;
                y++) {
            System.arraycopy(
                    frameStraightArgb,
                    y * frameWidth,
                    output,
                    (originY + y) * sheetWidth
                            + originX,
                    frameWidth);
        }
        return new MaterializedTexture(
                sourceTextureId,
                sheetWidth,
                sheetHeight,
                frameWidth,
                frameHeight,
                animated,
                StraightArgbPngEncoder.encode(
                        sheetWidth,
                        sheetHeight,
                        output));
    }

    /**
     * 中文：为显式导出冻结一个仅替换首帧的内存快照，不执行 PNG 编码或资源 I/O。
     *
     * English:
     * Freezes an in-memory snapshot with only the first frame replaced for an
     * explicit export, without PNG encoding or resource I/O.
     */
    public TextureSourceSnapshot withFirstFrame(
            int[] frameStraightArgb) {
        frameStraightArgb =
                Objects.requireNonNull(
                                frameStraightArgb,
                                "frameStraightArgb")
                        .clone();
        if (frameStraightArgb.length
                != Math.multiplyExact(
                        frameWidth,
                        frameHeight)) {
            throw new IllegalArgumentException(
                    "edited frame pixel count differs from source frame");
        }
        int columns =
                sheetWidth / frameWidth;
        int frame = firstFrameIndex();
        int originX =
                frame % columns * frameWidth;
        int originY =
                frame / columns * frameHeight;
        int[] output =
                straightArgb.clone();
        for (int y = 0;
                y < frameHeight;
                y++) {
            System.arraycopy(
                    frameStraightArgb,
                    y * frameWidth,
                    output,
                    (originY + y) * sheetWidth
                            + originX,
                    frameWidth);
        }
        return new TextureSourceSnapshot(
                sourceTextureId,
                sheetWidth,
                sheetHeight,
                frameWidth,
                frameHeight,
                animated,
                sourceMetadata,
                frameIndices,
                output);
    }

    public MaterializedTexture materializeSheet(
            int tileColumns,
            int tileRows,
            List<Optional<ProceduralConnectionPlan>>
                    tilePlans) throws IOException {
        return materializeCompositeSheet(
                tileColumns,
                tileRows,
                tilePlans.stream()
                        .map(plan -> plan.map(value ->
                                new SheetTile(
                                        this,
                                        value)))
                        .toList());
    }

    /**
     * 中文：仅供显式导出的纹理表合成，各纹理块可使用不同的已捕获源纹理；接收方拥有输出动画布局，备用来源按帧序号确定性采样，并在帧尺寸不同时使用最近邻缩放。
     *
     * English:
     * Explicit export-only sheet composition whose tiles may use distinct
     * captured source textures. The receiver owns the output animation layout;
     * alternate sources are sampled deterministically by frame ordinal and
     * resized with nearest-neighbor sampling when their frame dimensions differ.
     */
    public MaterializedTexture materializeCompositeSheet(
            int tileColumns,
            int tileRows,
            List<Optional<SheetTile>>
                    tiles) throws IOException {
        return compositeSheetTo(
                        sourceTextureId,
                        tileColumns,
                        tileRows,
                        tiles)
                .materializeCarrier();
    }

    /**
     * 中文：把共享表合成结果冻结为不可变载体，使绘画槽位可以先切片、再按载体一次保存。
     *
     * English: Freezes a composite sheet as an immutable carrier so paint slots
     * can be sliced and later saved once per carrier.
     */
    public TextureSourceSnapshot compositeSheetTo(
            String outputTextureId,
            int tileColumns,
            int tileRows,
            List<Optional<SheetTile>>
                    tiles) {
        if (outputTextureId == null
                || outputTextureId.isBlank()) {
            throw new IllegalArgumentException(
                    "outputTextureId must not be blank");
        }
        if (tileColumns <= 0 || tileRows <= 0) {
            throw new IllegalArgumentException(
                    "generated sheet layout must be positive");
        }
        tiles = List.copyOf(
                Objects.requireNonNull(
                        tiles,
                        "tiles"));
        if (tiles.size()
                != Math.multiplyExact(
                        tileColumns,
                        tileRows)) {
            throw new IllegalArgumentException(
                    "generated sheet tile count differs from layout");
        }
        int sourceColumns = sheetWidth / frameWidth;
        int sourceRows = sheetHeight / frameHeight;
        int outputFrameWidth = Math.multiplyExact(
                frameWidth,
                tileColumns);
        int outputFrameHeight = Math.multiplyExact(
                frameHeight,
                tileRows);
        int outputWidth = Math.multiplyExact(
                sourceColumns,
                outputFrameWidth);
        int outputHeight = Math.multiplyExact(
                sourceRows,
                outputFrameHeight);
        validateGeneratedDimensions(
                outputWidth,
                outputHeight);
        int[] output = new int[Math.multiplyExact(
                outputWidth,
                outputHeight)];
        for (int frameOrdinal = 0;
                frameOrdinal < frameIndices.length;
                frameOrdinal++) {
            int frame = frameIndices[frameOrdinal];
            int frameOriginX =
                    frame % sourceColumns
                            * outputFrameWidth;
            int frameOriginY =
                    frame / sourceColumns
                            * outputFrameHeight;
            for (int tile = 0;
                    tile < tiles.size();
                    tile++) {
                Optional<SheetTile> tileValue =
                        tiles.get(tile);
                if (tileValue.isEmpty()) {
                    continue;
                }
                SheetTile selected =
                        tileValue.orElseThrow();
                TextureSourceSnapshot
                        tileSource = selected.source();
                int tileFrame =
                        tileSource.frameIndices[
                                frameOrdinal
                                        % tileSource
                                                .frameIndices
                                                .length];
                int[] sourceFrame =
                        tileSource.sourceFrame(
                                tileFrame,
                                tileSource.sheetWidth
                                        / tileSource.frameWidth);
                int[] generated = selected.materialize(
                        sourceFrame);
                if (tileSource.frameWidth
                                != frameWidth
                        || tileSource.frameHeight
                                != frameHeight) {
                    generated = resizeNearest(
                            generated,
                            tileSource.frameWidth,
                            tileSource.frameHeight,
                            frameWidth,
                            frameHeight);
                }
                int tileOriginX = frameOriginX
                        + tile % tileColumns
                                * frameWidth;
                int tileOriginY = frameOriginY
                        + tile / tileColumns
                                * frameHeight;
                for (int y = 0;
                        y < frameHeight;
                        y++) {
                    System.arraycopy(
                            generated,
                            y * frameWidth,
                            output,
                            (tileOriginY + y)
                                            * outputWidth
                                    + tileOriginX,
                            frameWidth);
                }
            }
        }
        return new TextureSourceSnapshot(
                outputTextureId,
                outputWidth,
                outputHeight,
                outputFrameWidth,
                outputFrameHeight,
                animated,
                sourceMetadata,
                frameIndices,
                output);
    }

    /**
     * 中文：在载体底部追加 rows 行不参与播放的空动画帧（全透明像素），复制原像素并保持
     * id、帧尺寸、animated、frameIndices、sourceColumns 与元数据不变；sourceRows 增加 rows。
     * 用于让整图恰好方形的动画 FULL 载体非方形，以通过 Fusion 1.3.12 的整图宽高校验，同时
     * 新空帧不在 frameIndices 中，因此不会播放。
     *
     * English: Appends rows of empty (fully transparent) animation frames at the bottom of the
     * carrier, copying the original pixels while keeping the id, frame sizes, animated flag,
     * frameIndices, sourceColumns, and metadata unchanged; sourceRows gains rows. This makes a
     * whole-image-square animated FULL carrier non-square to pass Fusion 1.3.12's whole-sheet
     * size check, while the new empty frame stays outside frameIndices and never plays.
     */
    public TextureSourceSnapshot appendEmptyFrameRows(
            int rows) {
        if (rows <= 0) {
            throw new IllegalArgumentException(
                    "appended empty frame rows must be positive");
        }
        int addedHeight = Math.multiplyExact(
                rows,
                frameHeight);
        int newSheetHeight = Math.addExact(
                sheetHeight,
                addedHeight);
        validateGeneratedDimensions(
                sheetWidth,
                newSheetHeight);
        int[] output = new int[Math.multiplyExact(
                sheetWidth,
                newSheetHeight)];
        for (int y = 0; y < sheetHeight; y++) {
            System.arraycopy(
                    straightArgb,
                    y * sheetWidth,
                    output,
                    y * sheetWidth,
                    sheetWidth);
        }
        return new TextureSourceSnapshot(
                sourceTextureId,
                sheetWidth,
                newSheetHeight,
                frameWidth,
                frameHeight,
                animated,
                sourceMetadata,
                frameIndices,
                output);
    }

    private static int[] resizeNearest(
            int[] source,
            int sourceWidth,
            int sourceHeight,
            int targetWidth,
            int targetHeight) {
        int[] resized = new int[Math.multiplyExact(
                targetWidth,
                targetHeight)];
        for (int y = 0; y < targetHeight; y++) {
            int sourceY = Math.min(
                    sourceHeight - 1,
                    (int) ((long) y
                            * sourceHeight
                            / targetHeight));
            for (int x = 0; x < targetWidth; x++) {
                int sourceX = Math.min(
                        sourceWidth - 1,
                        (int) ((long) x
                                * sourceWidth
                                / targetWidth));
                resized[y * targetWidth + x] =
                        source[sourceY
                                        * sourceWidth
                                + sourceX];
            }
        }
        return resized;
    }

    private MaterializedTexture materializeFrames(
            FrameMaterializer materializer) throws IOException {
        int columns = sheetWidth / frameWidth;
        int[] output = straightArgb.clone();
        for (int frame : frameIndices) {
            int originX =
                    frame % columns * frameWidth;
            int originY =
                    frame / columns * frameHeight;
            int[] sourceFrame = sourceFrame(
                    frame,
                    columns);
            int[] generated =
                    materializer.materialize(
                            sourceFrame);
            for (int y = 0; y < frameHeight; y++) {
                System.arraycopy(
                        generated,
                        y * frameWidth,
                        output,
                        (originY + y) * sheetWidth
                                + originX,
                        frameWidth);
            }
        }
        return new MaterializedTexture(
                sourceTextureId,
                sheetWidth,
                sheetHeight,
                frameWidth,
                frameHeight,
                animated,
                StraightArgbPngEncoder.encode(
                        sheetWidth,
                        sheetHeight,
                        output));
    }

    private int[] sourceFrame(
            int frame,
            int columns) {
        int originX =
                frame % columns * frameWidth;
        int originY =
                frame / columns * frameHeight;
        int[] sourceFrame = new int[Math.multiplyExact(
                frameWidth,
                frameHeight)];
        for (int y = 0; y < frameHeight; y++) {
            System.arraycopy(
                    straightArgb,
                    (originY + y) * sheetWidth
                            + originX,
                    sourceFrame,
                    y * frameWidth,
                    frameWidth);
        }
        return sourceFrame;
    }

    private void validateRegion(
            int x,
            int y,
            int width,
            int height) {
        if (x < 0
                || y < 0
                || width <= 0
                || height <= 0
                || x + width > frameWidth
                || y + height > frameHeight) {
            throw new IllegalArgumentException(
                    "SOURCE_TEXTURE_REGION_INVALID");
        }
    }

    public String sourceTextureId() {
        return sourceTextureId;
    }

    public int sheetWidth() {
        return sheetWidth;
    }

    public int sheetHeight() {
        return sheetHeight;
    }

    public int frameWidth() {
        return frameWidth;
    }

    public int frameHeight() {
        return frameHeight;
    }

    public boolean animated() {
        return animated;
    }

    public int[] frameIndices() {
        return frameIndices.clone();
    }

    public byte[] sourceMetadata() {
        return sourceMetadata.clone();
    }

    /**
     * 中文：比较完整载体身份，包括全部帧像素、动画帧索引与原始元数据。
     *
     * English: Compares complete carrier identity, including all frame pixels,
     * animation frame indices, and original metadata.
     */
    public boolean sameCarrierContent(
            TextureSourceSnapshot other) {
        return other != null
                && sourceTextureId.equals(
                        other.sourceTextureId)
                && sheetWidth == other.sheetWidth
                && sheetHeight == other.sheetHeight
                && frameWidth == other.frameWidth
                && frameHeight == other.frameHeight
                && animated == other.animated
                && Arrays.equals(
                        sourceMetadata,
                        other.sourceMetadata)
                && Arrays.equals(
                        frameIndices,
                        other.frameIndices)
                && Arrays.equals(
                        straightArgb,
                        other.straightArgb);
    }

    /**
     * 中文：按引擎原生载体策略重新解释同一不可变整图的动画帧边界。
     *
     * English: Reinterprets animation-frame bounds over the same immutable
     * full-sheet pixels according to an engine-native carrier policy.
     */
    public TextureSourceSnapshot withFrameLayout(
            int replacementFrameWidth,
            int replacementFrameHeight,
            boolean replacementAnimated,
            int[] replacementFrameIndices) {
        validateDimensions(
                sheetWidth,
                sheetHeight,
                replacementFrameWidth,
                replacementFrameHeight);
        int[] frames = Objects.requireNonNull(
                replacementFrameIndices,
                "replacementFrameIndices").clone();
        validateFrames(
                frames,
                sheetWidth / replacementFrameWidth,
                sheetHeight / replacementFrameHeight);
        return new TextureSourceSnapshot(
                sourceTextureId,
                sheetWidth,
                sheetHeight,
                replacementFrameWidth,
                replacementFrameHeight,
                replacementAnimated,
                sourceMetadata,
                frames,
                straightArgb);
    }

    /** 中文：以保留像素和动画布局的方式替换载体元数据。 / English: Replaces carrier metadata while preserving pixels and animation layout. */
    public TextureSourceSnapshot withSourceMetadata(
            byte[] metadata) {
        return new TextureSourceSnapshot(
                sourceTextureId,
                sheetWidth,
                sheetHeight,
                frameWidth,
                frameHeight,
                animated,
                Objects.requireNonNull(
                        metadata,
                        "metadata"),
                frameIndices,
                straightArgb);
    }

    private static byte[] readMetadata(
            ResourceLocation sourceTexture,
            ResourceManager resources) throws IOException {
        ResourceLocation metadataId =
                ResourceLocation.fromNamespaceAndPath(
                        sourceTexture.getNamespace(),
                        "textures/"
                                + sourceTexture.getPath()
                                + ".png.mcmeta");
        Resource resource = resources
                .getResource(metadataId)
                .orElse(null);
        if (resource == null) {
            return new byte[0];
        }
        try (var input = resource.open()) {
            byte[] bytes = input.readNBytes(
                    MAX_METADATA_BYTES + 1);
            if (bytes.length > MAX_METADATA_BYTES) {
                throw new IOException(
                        "SOURCE_TEXTURE_METADATA_TOO_LARGE");
            }
            return bytes;
        }
    }

    private static void validateDimensions(
            int sheetWidth,
            int sheetHeight,
            int frameWidth,
            int frameHeight) {
        long pixels = (long) sheetWidth * sheetHeight;
        if (sheetWidth <= 0
                || sheetHeight <= 0
                || frameWidth <= 0
                || frameHeight <= 0
                || sheetWidth > MAX_DIMENSION
                || sheetHeight > MAX_DIMENSION
                || pixels > MAX_PIXELS) {
            throw new IllegalArgumentException(
                    "SOURCE_TEXTURE_DIMENSIONS_UNSUPPORTED");
        }
        if (sheetWidth % frameWidth != 0
                || sheetHeight % frameHeight != 0) {
            throw new IllegalArgumentException(
                    "SOURCE_TEXTURE_FRAME_LAYOUT_INVALID");
        }
    }

    private static void validateGeneratedDimensions(
            int width,
            int height) {
        long pixels = (long) width * height;
        if (width <= 0
                || height <= 0
                || width > MAX_DIMENSION
                || height > MAX_DIMENSION
                || pixels > MAX_PIXELS) {
            throw new IllegalArgumentException(
                    "GENERATED_TEXTURE_DIMENSIONS_UNSUPPORTED");
        }
    }

    private static int[] sequentialFrames(
            int frameCount) {
        int[] frames = new int[frameCount];
        for (int frame = 0; frame < frameCount; frame++) {
            frames[frame] = frame;
        }
        return frames;
    }

    private static void validateFrames(
            int[] frameIndices,
            int columns,
            int rows) {
        int frameCount = Math.multiplyExact(
                columns,
                rows);
        if (Arrays.stream(frameIndices)
                .anyMatch(frame ->
                        frame < 0
                                || frame >= frameCount)) {
            throw new IllegalArgumentException(
                    "SOURCE_TEXTURE_FRAME_INDEX_INVALID");
        }
    }

    public record MaterializedTexture(
            String sourceTextureId,
            int sheetWidth,
            int sheetHeight,
            int frameWidth,
            int frameHeight,
            boolean animated,
            byte[] png) {
        public MaterializedTexture {
            Objects.requireNonNull(
                    sourceTextureId,
                    "sourceTextureId");
            png = Objects.requireNonNull(
                    png,
                    "png").clone();
        }

        @Override
        public byte[] png() {
            return png.clone();
        }
    }

    public static final class SheetTile {
        private final TextureSourceSnapshot source;
        private final ProceduralConnectionPlan plan;
        private final GeneratedTileRecipe recipe;
        private final OverlayCutoutProfile overlayProfile;

        public SheetTile(
                TextureSourceSnapshot source,
                ProceduralConnectionPlan plan) {
            this.source = Objects.requireNonNull(
                    source,
                    "source");
            this.plan = Objects.requireNonNull(
                    plan,
                    "plan");
            this.recipe = null;
            this.overlayProfile = null;
        }

        public SheetTile(
                TextureSourceSnapshot source,
                GeneratedTileRecipe recipe) {
            this.source = Objects.requireNonNull(
                    source,
                    "source");
            this.plan = null;
            this.recipe = Objects.requireNonNull(
                    recipe,
                    "recipe");
            this.overlayProfile = null;
        }

        public SheetTile(
                TextureSourceSnapshot source,
                GeneratedTileRecipe recipe,
                OverlayCutoutProfile overlayProfile) {
            this.source = Objects.requireNonNull(
                    source,
                    "source");
            this.plan = null;
            this.recipe = Objects.requireNonNull(
                    recipe,
                    "recipe");
            this.overlayProfile = Objects.requireNonNull(
                    overlayProfile,
                    "overlayProfile");
        }

        public TextureSourceSnapshot source() {
            return source;
        }

        private int[] materialize(
                int[] sourceFrame) {
            if (recipe != null) {
                if (overlayProfile != null) {
                    return GeneratedTileMaterializer
                            .materializeStraightArgb(
                                    source.frameWidth,
                                    source.frameHeight,
                                    sourceFrame,
                                    recipe,
                                    overlayProfile);
                }
                return GeneratedTileMaterializer
                        .materializeStraightArgb(
                                source.frameWidth,
                                source.frameHeight,
                                sourceFrame,
                                recipe);
            }
            return ProceduralPlanMaterializer
                    .materializeStraightArgb(
                            source.frameWidth,
                            source.frameHeight,
                            sourceFrame,
                            plan);
        }
    }

    /**
     * 中文：首动画帧中的一个不可变矩形编辑。
     *
     * English: One immutable rectangular edit within the first animation frame.
     *
     * @param x 中文：矩形左上角 X。 / English: X of the rectangle top-left.
     * @param y 中文：矩形左上角 Y。 / English: Y of the rectangle top-left.
     * @param width 中文：矩形宽度。 / English: Rectangle width.
     * @param height 中文：矩形高度。 / English: Rectangle height.
     * @param straightArgb 中文：直通 ARGB 像素。 / English: Straight-ARGB pixels.
     */
    public record RegionEdit(
            int x,
            int y,
            int width,
            int height,
            int[] straightArgb) {
        public RegionEdit {
            straightArgb = Objects.requireNonNull(
                            straightArgb,
                            "straightArgb")
                    .clone();
            if (x < 0
                    || y < 0
                    || width <= 0
                    || height <= 0
                    || straightArgb.length
                            != Math.multiplyExact(
                                    width,
                                    height)) {
                throw new IllegalArgumentException(
                        "invalid texture region edit");
            }
        }

        @Override
        public int[] straightArgb() {
            return straightArgb.clone();
        }
    }

    @FunctionalInterface
    private interface FrameMaterializer {
        int[] materialize(int[] sourceFrame);
    }
}
