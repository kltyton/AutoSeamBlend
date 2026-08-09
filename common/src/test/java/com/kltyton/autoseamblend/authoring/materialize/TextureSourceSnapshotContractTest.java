package com.kltyton.autoseamblend.authoring.materialize;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kltyton.autoseamblend.compat.fusion.authoring.materialize.FusionNativeCarrierPlanning;
import com.kltyton.autoseamblend.compat.fusion.authoring.materialize.FusionNativeEvidenceLayout;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe;
import com.kltyton.autoseamblend.texture.io.NativeArgb;
import com.kltyton.autoseamblend.texture.io.StraightArgbPngEncoder;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 合同——合成 3x4 动画网格（16x16 帧）的动画 FULL 载体恰好为 384x384 方形，
 * Fusion 1.3.12 createTexture 会先按整图宽高拒绝 FULL+animated（读取动画帧尺寸之前）；
 * 通过最窄规划入口 avoidAnimatedFullSquareSheet 后，载体必须在底部追加一个空动画帧行，
 * 使整图非方形，同时 sheetHeight 增加一个 frameHeight、sourceRows+1、sourceColumns 不变、
 * frameIndices 不变、原像素不变。
 *
 * English: RED contract -- an animated FULL carrier composited from a 3x4 animation grid of
 * 16x16 frames is exactly 384x384 square, which Fusion 1.3.12 createTexture rejects for
 * FULL+animated based on the whole-sheet size before reading animation frame metadata; through
 * the narrowest planning entry avoidAnimatedFullSquareSheet the carrier must gain one empty
 * animation-frame row at the bottom, so the sheet is no longer square while sheetHeight grows
 * by one frameHeight, sourceRows gains one, sourceColumns stays, frameIndices stay, and
 * original pixels stay.
 */
class TextureSourceSnapshotContractTest {
    @Test
    void animatedFullSquareSheetAppendsOneEmptyFrameRow()
            throws IOException {
        TextureSourceSnapshot source = animatedSource(3, 4, true);
        TextureSourceSnapshot carrier = fullCarrier(source);

        // RED 前置：合成后的动画 FULL 载体整图必须为方形（384x384），这正是 Fusion 拒绝的形状。
        // English RED precondition: the composited animated FULL carrier must be square
        // (384x384), the exact shape Fusion rejects.
        assertEquals(carrier.sheetWidth(), carrier.sheetHeight());
        int originalHeight = carrier.sheetHeight();
        int originalWidth = carrier.sheetWidth();
        int frameWidth = carrier.frameWidth();
        int frameHeight = carrier.frameHeight();
        int[] originalIndices = carrier.frameIndices();
        int[] originalFirstFrame = carrier.firstFrameStraightArgb();

        TextureSourceSnapshot planned =
                FusionNativeCarrierPlanning.avoidAnimatedFullSquareSheet(
                        carrier,
                        fullLayout());

        // 中文：底部追加一整行空帧：高度增加一个 frameHeight，宽度不变，行数+1、列数不变，
        // 帧索引与首帧像素原样保留，且必须是新的不可变实例。
        // English: One full empty frame row is appended at the bottom: height grows by one
        // frameHeight, width stays, rows gain one while columns stay, frame indices and first
        // frame pixels are preserved, and the result must be a fresh immutable instance.
        assertEquals(
                originalHeight + frameHeight,
                planned.sheetHeight());
        assertEquals(originalWidth, planned.sheetWidth());
        assertEquals(
                originalWidth / frameWidth,
                planned.sheetWidth() / planned.frameWidth());
        assertEquals(
                originalHeight / frameHeight + 1,
                planned.sheetHeight() / planned.frameHeight());
        assertArrayEquals(
                originalIndices,
                planned.frameIndices());
        assertArrayEquals(
                originalFirstFrame,
                planned.firstFrameStraightArgb());
        assertNotSame(carrier, planned);
    }

    @Test
    void appendEmptyFrameRowsPreservesPixelsAndAppendsTransparentRow()
            throws IOException {
        TextureSourceSnapshot source = animatedSource(3, 4, true);
        TextureSourceSnapshot carrier = fullCarrier(source);
        int[] originalPixels = decodeStraightArgb(
                carrier.materializeCarrier().png());

        TextureSourceSnapshot padded =
                carrier.appendEmptyFrameRows(1);

        int paddingPixels = Math.multiplyExact(
                carrier.sheetWidth(),
                carrier.frameHeight());
        int[] paddedPixels = decodeStraightArgb(
                padded.materializeCarrier().png());
        assertEquals(
                originalPixels.length + paddingPixels,
                paddedPixels.length);
        for (int index = 0;
                index < originalPixels.length;
                index++) {
            assertEquals(
                    originalPixels[index],
                    paddedPixels[index],
                    "original pixel " + index
                            + " must be preserved");
        }
        for (int index = originalPixels.length;
                index < paddedPixels.length;
                index++) {
            assertEquals(
                    0,
                    paddedPixels[index] >>> 24,
                    "padding pixel " + index
                            + " must be fully transparent");
        }
    }

    @Test
    void appendEmptyFrameRowsLocksIdentityAndLayout()
            throws IOException {
        TextureSourceSnapshot source = animatedSource(3, 4, true);
        TextureSourceSnapshot carrier = fullCarrier(source);
        String id = carrier.sourceTextureId();
        int frameWidth = carrier.frameWidth();
        int frameHeight = carrier.frameHeight();
        boolean animated = carrier.animated();
        byte[] metadata = carrier.sourceMetadata();
        int[] indices = carrier.frameIndices();
        int sourceColumns =
                carrier.sheetWidth() / frameWidth;
        int sourceRows =
                carrier.sheetHeight() / frameHeight;

        TextureSourceSnapshot padded =
                carrier.appendEmptyFrameRows(1);

        assertEquals(id, padded.sourceTextureId());
        assertEquals(frameWidth, padded.frameWidth());
        assertEquals(frameHeight, padded.frameHeight());
        assertEquals(animated, padded.animated());
        assertArrayEquals(metadata, padded.sourceMetadata());
        assertArrayEquals(indices, padded.frameIndices());
        assertEquals(
                sourceColumns,
                padded.sheetWidth() / padded.frameWidth());
        assertEquals(
                sourceRows + 1,
                padded.sheetHeight() / padded.frameHeight());
        assertNotSame(carrier, padded);
    }

    @Test
    void appendEmptyFrameRowsRejectsNonPositiveRowCount()
            throws IOException {
        TextureSourceSnapshot carrier = fullCarrier(
                animatedSource(3, 4, true));
        assertThrows(
                IllegalArgumentException.class,
                () -> carrier.appendEmptyFrameRows(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> carrier.appendEmptyFrameRows(-1));
    }

    @Test
    void avoidAnimatedFullSquareSheetPassesThroughNonSquareCases()
            throws IOException {
        // 非方形动画 FULL：3x3 网格合成 384x288，不触发追加。
        // English: Non-square animated FULL composites to 384x288 and must pass through.
        TextureSourceSnapshot nonSquare = fullCarrier(
                animatedSource(3, 3, true));
        assertEquals(
                false,
                nonSquare.sheetWidth() == nonSquare.sheetHeight());
        assertSame(
                nonSquare,
                FusionNativeCarrierPlanning.avoidAnimatedFullSquareSheet(
                        nonSquare,
                        fullLayout()));

        // 静态 FULL：保留 8 行 padding，合成 384x512，动画标志为 false，必须原样返回。
        // English: Static FULL keeps 8 padding rows, composites to 384x512, and the animated
        // flag is false, so it must return the same instance.
        TextureSourceSnapshot staticCarrier = fullCarrier(
                staticSource());
        assertSame(
                staticCarrier,
                FusionNativeCarrierPlanning.avoidAnimatedFullSquareSheet(
                        staticCarrier,
                        fullLayout()));

        // 其他布局：4x1 horizontal 不是 8x6，必须原样返回。
        // English: Other layouts such as 4x1 horizontal are not 8x6 and must pass through.
        TextureSourceSnapshot horizontal = carrier(
                animatedSource(3, 4, true),
                4,
                1);
        FusionNativeEvidenceLayout horizontalLayout =
                new FusionNativeEvidenceLayout(
                        4,
                        1,
                        0,
                        List.of(
                                List.of(0),
                                List.of(1),
                                List.of(2),
                                List.of(3)));
        assertSame(
                horizontal,
                FusionNativeCarrierPlanning.avoidAnimatedFullSquareSheet(
                        horizontal,
                        horizontalLayout));
    }

    @Test
    void paddedCarrierMetadataExcludesEmptyFrames()
            throws IOException {
        // 源动画元数据没有 frames 数组：padding 后必须显式写出原帧索引，排除新空帧。
        // English: The source animation metadata has no frames array; after padding the
        // original frame indices must be written explicitly so the empty frame never plays.
        TextureSourceSnapshot source = animatedSource(3, 4, false);
        TextureSourceSnapshot carrier = fullCarrier(source);
        TextureSourceSnapshot padded =
                FusionNativeCarrierPlanning.avoidAnimatedFullSquareSheet(
                        carrier,
                        fullLayout());
        byte[] metadata =
                FusionNativeCarrierPlanning.generatedConnectingMetadata(
                        ConnectionMethod.CTM,
                        false,
                        "full",
                        source.sourceMetadata(),
                        padded.frameWidth(),
                        padded.frameHeight(),
                        padded.animated(),
                        padded.frameIndices(),
                        true);
        JsonObject animation = JsonParser.parseString(
                        new String(
                                metadata,
                                StandardCharsets.UTF_8))
                .getAsJsonObject()
                .getAsJsonObject("animation");
        JsonArray frames = animation.getAsJsonArray("frames");
        assertEquals(
                List.of(
                        0, 1, 2, 3, 4, 5,
                        6, 7, 8, 9, 10, 11),
                frames.asList().stream()
                        .map(JsonElement::getAsInt)
                        .toList(),
                "explicit frames must list exactly the original indices");
        assertEquals(
                padded.frameWidth(),
                animation.get("width").getAsInt());
        assertEquals(
                padded.frameHeight(),
                animation.get("height").getAsInt());
    }

    private static FusionNativeEvidenceLayout fullLayout() {
        return new FusionNativeEvidenceLayout(
                8,
                6,
                0,
                List.of(
                        List.of(0),
                        List.of(1)));
    }

    private static TextureSourceSnapshot fullCarrier(
            TextureSourceSnapshot source) {
        return carrier(source, 8, 6);
    }

    private static TextureSourceSnapshot carrier(
            TextureSourceSnapshot source,
            int tileColumns,
            int tileRows) {
        ArrayList<Optional<TextureSourceSnapshot.SheetTile>> tiles =
                new ArrayList<>();
        tiles.add(Optional.of(
                new TextureSourceSnapshot.SheetTile(
                        source,
                        GeneratedTileRecipe.Source.INSTANCE)));
        while (tiles.size()
                < tileColumns * tileRows) {
            tiles.add(Optional.empty());
        }
        return source.compositeSheetTo(
                "test:generated/sheet",
                tileColumns,
                tileRows,
                tiles);
    }

    /** 中文：无动画元数据的静态源，经真实资源解析路径捕获。 / English: Static source without animation metadata captured through the real resource-parsing path. */
    private static TextureSourceSnapshot staticSource()
            throws IOException {
        return capturedSource(3, 4, "{}");
    }

    private static int[] decodeStraightArgb(byte[] png)
            throws IOException {
        try (NativeImage image = NativeImage.read(
                new ByteArrayInputStream(png))) {
            int[] pixels = new int[Math.multiplyExact(
                    image.getWidth(),
                    image.getHeight())];
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    pixels[y * image.getWidth() + x] =
                            NativeArgb.toIr(
                                    image.getPixelRGBA(
                                            x,
                                            y));
                }
            }
            return pixels;
        }
    }

    /** 中文：经真实资源解析路径捕获任意网格的 16x16 动画源。 / English: Captures a 16x16-frame animated source over the requested grid through the real resource-parsing path. */
    private static TextureSourceSnapshot animatedSource(
            int columns,
            int rows,
            boolean withFramesArray) throws IOException {
        int frameCount = columns * rows;
        StringBuilder frames = new StringBuilder();
        if (withFramesArray) {
            frames.append(",\"frames\":[");
            for (int frame = 0; frame < frameCount; frame++) {
                if (frame > 0) {
                    frames.append(',');
                }
                frames.append(frame);
            }
            frames.append(']');
        }
        String mcmeta = "{\"animation\":{\"width\":16,\"height\":16,\"frametime\":2"
                + frames + "}}";
        return capturedSource(columns, rows, mcmeta);
    }

    private static TextureSourceSnapshot capturedSource(
            int columns,
            int rows,
            String mcmeta) throws IOException {
        int width = Math.multiplyExact(columns, 16);
        int height = Math.multiplyExact(rows, 16);
        int[] pixels = new int[Math.multiplyExact(
                width,
                height)];
        for (int frame = 0;
                frame < columns * rows;
                frame++) {
            int color = 0xFF000000 | (frame + 1) * 0x111111;
            int originX = frame % columns * 16;
            int originY = frame / columns * 16;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    pixels[(originY + y) * width
                            + originX + x] = color;
                }
            }
        }
        byte[] png = StraightArgbPngEncoder.encode(
                width,
                height,
                pixels);
        byte[] metadata = mcmeta.getBytes(
                StandardCharsets.UTF_8);
        ResourceLocation texture = ResourceLocation
                .fromNamespaceAndPath(
                        "test",
                        "source");
        ResourceLocation file = SpriteSource.TEXTURE_ID_CONVERTER
                .idToFile(texture);
        ResourceLocation metadataFile = ResourceLocation
                .fromNamespaceAndPath(
                        "test",
                        "textures/source.png.mcmeta");
        Resource pngResource = new Resource(
                null,
                () -> new ByteArrayInputStream(png),
                () -> ResourceMetadata.fromJsonStream(
                        new ByteArrayInputStream(metadata)));
        Resource metadataResource = new Resource(
                null,
                () -> new ByteArrayInputStream(metadata));
        ResourceManager manager = new MapResourceManager(
                Map.of(
                        file,
                        pngResource,
                        metadataFile,
                        metadataResource));
        return TextureSourceSnapshot.capture(
                manager,
                texture);
    }

    /** 中文：只服务 PNG 与 .mcmeta 读取的最小资源管理器替身。 / English: Minimal resource manager double serving only PNG and .mcmeta reads. */
    private static final class MapResourceManager
            implements ResourceManager {
        private final Map<ResourceLocation, Resource> resources;

        private MapResourceManager(
                Map<ResourceLocation, Resource> resources) {
            this.resources = Map.copyOf(resources);
        }

        @Override
        public Optional<Resource> getResource(
                ResourceLocation location) {
            return Optional.ofNullable(
                    resources.get(location));
        }

        @Override
        public Set<String> getNamespaces() {
            return Set.of("test");
        }

        @Override
        public List<Resource> getResourceStack(
                ResourceLocation location) {
            return Optional.ofNullable(
                            resources.get(location))
                    .map(List::of)
                    .orElseGet(List::of);
        }

        @Override
        public Map<ResourceLocation, Resource> listResources(
                String namespace,
                Predicate<ResourceLocation> predicate) {
            return Map.of();
        }

        @Override
        public Map<ResourceLocation, List<Resource>>
                listResourceStacks(
                        String namespace,
                        Predicate<ResourceLocation> predicate) {
            return Map.of();
        }

        @Override
        public Stream<PackResources> listPacks() {
            return Stream.of();
        }
    }
}
