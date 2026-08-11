package com.kltyton.autoseamblend.texture.atlas;

import com.kltyton.autoseamblend.mixin.minecraft.SpriteContentsImageAccessor;
import com.kltyton.autoseamblend.mixin.minecraft.SpriteSourceListAccessor;
import com.kltyton.autoseamblend.runtime.surface.SurfaceSourceProvenance;
import com.kltyton.autoseamblend.runtime.surface.SurfaceSourceSnapshot;
import com.kltyton.autoseamblend.texture.budget.TextureImageBudget;
import com.kltyton.autoseamblend.texture.budget.TextureInputBudget;
import com.kltyton.autoseamblend.texture.budget.TextureSourceBudget;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;

/**
 * 中文：在首次方块 Atlas 缝合前捕获最终直接/派生来源；Loader 只提供生成来源判定。
 *
 * English: Captures final direct and derived block-atlas sources before the first stitch; each
 * Loader only supplies its generated-source predicate.
 */
public final class InitialBlockAtlasResources {
    private static final TextureImageBudget IMAGE_BUDGET =
            TextureImageBudget.DEFAULT;

    private InitialBlockAtlasResources() {}

    /**
     * 中文：捕获所有来源，供 NeoForge 的首轮准备语义使用。
     *
     * English: Captures every source for NeoForge's first-pass preparation semantics.
     */
    public static Snapshot capture(
            ResourceManager resources,
            Predicate<SpriteSource> generatedSource) {
        return capture(
                resources,
                Optional.empty(),
                generatedSource);
    }

    /**
     * 中文：只冻结最终 required 精灵，避免无关派生 loader 占用预算。
     *
     * English: Freezes only final required sprites so unrelated derived loaders do not consume
     * the capture budget.
     */
    public static Snapshot capture(
            ResourceManager resources,
            Set<Identifier> requiredSprites,
            Predicate<SpriteSource> generatedSource) {
        Objects.requireNonNull(requiredSprites, "requiredSprites");
        LinkedHashSet<Identifier> required = new LinkedHashSet<>();
        for (Identifier spriteId : requiredSprites) {
            required.add(Objects.requireNonNull(spriteId, "required sprite"));
        }
        return capture(
                resources,
                Optional.of(Collections.unmodifiableSet(required)),
                generatedSource);
    }

    private static Snapshot capture(
            ResourceManager resources,
            Optional<Set<Identifier>> requiredSprites,
            Predicate<SpriteSource> generatedSource) {
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(requiredSprites, "requiredSprites");
        Predicate<SpriteSource> skip = Objects.requireNonNull(
                generatedSource,
                "generatedSource");
        CaptureOutput output = new CaptureOutput();
        SpriteSourceList sources = SpriteSourceList.load(
                resources,
                AtlasIds.BLOCKS);
        try {
            for (SpriteSource source
                    : ((SpriteSourceListAccessor) sources)
                            .autoseamblend$sources()) {
                if (!skip.test(source)) {
                    source.run(resources, output);
                }
            }
            return new Snapshot(output.resolve(requiredSprites));
        } finally {
            output.discardPending();
        }
    }

    /**
     * 中文：冻结读取结果并在捕获边界完成并发安全的像素值发布。
     *
     * English: Freezes read results and publishes concurrency-safe pixel values at the capture
     * boundary.
     */
    public static final class Snapshot {
        private final Map<Identifier, SourceRead> reads;

        public Snapshot(Map<Identifier, SourceRead> reads) {
            LinkedHashMap<Identifier, SourceRead> copy =
                    new LinkedHashMap<>();
            for (Map.Entry<Identifier, SourceRead> entry
                    : Objects.requireNonNull(reads, "reads").entrySet()) {
                Map.Entry<Identifier, SourceRead> checked =
                        Objects.requireNonNull(entry, "reads entry");
                Identifier spriteId = Objects.requireNonNull(
                        checked.getKey(),
                        "reads key");
                SourceRead read = Objects.requireNonNull(
                        checked.getValue(),
                        "reads value");
                validateSourceRead(read);
                copy.put(spriteId, read);
            }
            this.reads = Collections.unmodifiableMap(copy);
        }

        public Map<Identifier, SourceRead> reads() {
            return reads;
        }

        public SourceRead read(Identifier spriteId) {
            Objects.requireNonNull(spriteId, "spriteId");
            SourceRead read = reads.get(spriteId);
            return read != null
                    ? read
                    : SourceRead.unavailable(
                            ReadEvidence.NOT_DECLARED,
                            "BLOCK_ATLAS_SPRITE_NOT_DECLARED");
        }
    }

    /**
     * 中文：像素读取结果始终保留成功来源或明确失败证据，派生来源不会伪装成 PNG。
     *
     * English: Pixel reads retain exact provenance or explicit failure evidence; derived sources
     * are never presented as direct PNG resources.
     */
    public record SourceRead(
            Optional<SurfaceSourceSnapshot> image,
            ReadEvidence evidence,
            String detail) {
        public SourceRead {
            image = Objects.requireNonNull(image, "image");
            Objects.requireNonNull(evidence, "evidence");
            if (detail == null || detail.isBlank()) {
                throw new IllegalArgumentException(
                        "source-read detail must not be blank");
            }
            if (image.isPresent() != evidence.available()) {
                throw new IllegalArgumentException(
                        "source-read evidence disagrees with image availability");
            }
        }

        private static SourceRead available(
                SurfaceSourceSnapshot image,
                ReadEvidence evidence) {
            return new SourceRead(
                    Optional.of(Objects.requireNonNull(image, "image")),
                    evidence,
                    evidence.name());
        }

        private static SourceRead unavailable(
                ReadEvidence evidence,
                String detail) {
            return new SourceRead(
                    Optional.empty(),
                    evidence,
                    detail);
        }
    }

    public enum ReadEvidence {
        DIRECT_PNG(true),
        DERIVED_PIXELS(true),
        NOT_DECLARED(false),
        DERIVED_UNAVAILABLE(false),
        INVALID_FRAME_GEOMETRY(false),
        READ_FAILED(false);

        private final boolean available;

        ReadEvidence(boolean available) {
            this.available = available;
        }

        public boolean available() {
            return available;
        }
    }

    private static final class CaptureOutput
            implements SpriteSource.Output {
        private final LinkedHashMap<Identifier, PendingResource> pending =
                new LinkedHashMap<>();

        @Override
        public void add(Identifier spriteId, Resource resource) {
            replace(
                    Objects.requireNonNull(spriteId, "spriteId"),
                    new DirectResource(Objects.requireNonNull(
                            resource,
                            "resource")));
        }

        @Override
        public void add(
                Identifier spriteId,
                SpriteSource.DiscardableLoader loader) {
            replace(
                    Objects.requireNonNull(spriteId, "spriteId"),
                    new DerivedResource(Objects.requireNonNull(
                            loader,
                            "loader")));
        }

        @Override
        public void removeAll(Predicate<Identifier> predicate) {
            Predicate<Identifier> checked = Objects.requireNonNull(
                    predicate,
                    "predicate");
            pending.entrySet().removeIf(entry -> {
                if (!checked.test(entry.getKey())) {
                    return false;
                }
                entry.getValue().discard();
                return true;
            });
        }

        private void replace(
                Identifier spriteId,
                PendingResource resource) {
            PendingResource previous = pending.put(
                    spriteId,
                    resource);
            if (previous != null) {
                previous.discard();
            }
        }

        private Map<Identifier, SourceRead> resolve(
                Optional<Set<Identifier>> requiredSprites) {
            Objects.requireNonNull(requiredSprites, "requiredSprites");
            LinkedHashMap<Identifier, SourceRead> reads =
                    new LinkedHashMap<>();
            TextureSourceBudget.Accumulator budget =
                    TextureSourceBudget.DEFAULT.accumulator();
            var iterator = pending.entrySet().iterator();
            try {
                while (iterator.hasNext()) {
                    Map.Entry<Identifier, PendingResource> entry =
                            iterator.next();
                    Identifier spriteId = entry.getKey();
                    PendingResource resource = entry.getValue();
                    iterator.remove();
                    if (requiredSprites.isPresent()
                            && !requiredSprites.orElseThrow()
                                    .contains(spriteId)) {
                        resource.discard();
                        continue;
                    }
                    reads.put(
                            spriteId,
                            resource.resolve(spriteId, budget));
                }
                return reads;
            } finally {
                discardPending();
            }
        }

        private void discardPending() {
            pending.values().forEach(PendingResource::discard);
            pending.clear();
        }

        private sealed interface PendingResource
                permits DirectResource, DerivedResource {
            SourceRead resolve(
                    Identifier spriteId,
                    TextureSourceBudget.Accumulator budget);

            default void discard() {}
        }

        private record DirectResource(Resource resource)
                implements PendingResource {
            private DirectResource {
                Objects.requireNonNull(resource, "resource");
            }

            @Override
            public SourceRead resolve(
                    Identifier spriteId,
                    TextureSourceBudget.Accumulator budget) {
                return resolveDirect(spriteId, resource, budget);
            }
        }

        private record DerivedResource(
                SpriteSource.DiscardableLoader loader)
                implements PendingResource {
            private DerivedResource {
                Objects.requireNonNull(loader, "loader");
            }

            @Override
            public SourceRead resolve(
                    Identifier spriteId,
                    TextureSourceBudget.Accumulator budget) {
                return resolveDerived(spriteId, loader, budget);
            }

            @Override
            public void discard() {
                loader.discard();
            }
        }

        private static SourceRead resolveDirect(
                Identifier spriteId,
                Resource resource,
                TextureSourceBudget.Accumulator budget) {
            Objects.requireNonNull(spriteId, "spriteId");
            Objects.requireNonNull(resource, "resource");
            Objects.requireNonNull(budget, "budget");
            try (var input = resource.open()) {
                // 中文：先限制压缩 PNG 原始字节，再交给 NativeImage 解码。
                // English: Bound compressed PNG bytes before handing them to NativeImage.
                byte[] encoded = TextureInputBudget.DEFAULT.read(
                        input,
                        TextureInputBudget.InputKind.PNG,
                        "block-atlas-png:" + spriteId);
                try (NativeImage image = NativeImage.read(
                        new ByteArrayInputStream(encoded))) {
                    int sheetWidth = image.getWidth();
                    int sheetHeight = image.getHeight();
                    Optional<AnimationMetadataSection> animation =
                            resource.metadata().getSection(
                                    AnimationMetadataSection.TYPE);
                    FrameSize frame = animation
                            .map(value -> value.calculateFrameSize(
                                    sheetWidth,
                                    sheetHeight))
                            .orElseGet(() -> new FrameSize(
                                    sheetWidth,
                                    sheetHeight));
                    int pixelCount;
                    try {
                        pixelCount = IMAGE_BUDGET.requireImage(
                                sheetWidth,
                                sheetHeight,
                                frame.width(),
                                frame.height());
                    } catch (TextureImageBudget.ViolationException exception) {
                        return unavailableForBudget(
                                "DIRECT_PNG",
                                ReadEvidence.INVALID_FRAME_GEOMETRY,
                                exception);
                    }
                    budget.reserve(spriteId.toString(), pixelCount);
                    PixelAnalysis analysis = analyze(
                            image.getPixelsABGR(),
                            sheetWidth,
                            sheetHeight,
                            frame.width(),
                            frame.height(),
                            pixelCount);
                    return SourceRead.available(
                            new SurfaceSourceSnapshot(
                                    spriteId.toString(),
                                    sheetWidth,
                                    sheetHeight,
                                    frame.width(),
                                    frame.height(),
                                    analysis.straightArgb(),
                                    animation.isPresent(),
                                    analysis.opaque(),
                                    analysis.framedAlpha(),
                                    SurfaceSourceProvenance.DIRECT_RESOURCE),
                            ReadEvidence.DIRECT_PNG);
                }
            } catch (TextureImageBudget.ViolationException exception) {
                return unavailableForBudget(
                        "DIRECT_PNG",
                        ReadEvidence.INVALID_FRAME_GEOMETRY,
                        exception);
            } catch (TextureInputBudget.ViolationException
                    | TextureSourceBudget.ViolationException exception) {
                throw exception;
            } catch (IOException
                    | IllegalArgumentException
                    | IllegalStateException
                    | ArithmeticException exception) {
                return SourceRead.unavailable(
                        ReadEvidence.READ_FAILED,
                        "DIRECT_PNG_READ_FAILED:"
                                + exception.getClass().getSimpleName());
            }
        }

        private static SourceRead resolveDerived(
                Identifier spriteId,
                SpriteSource.DiscardableLoader loader,
                TextureSourceBudget.Accumulator budget) {
            Objects.requireNonNull(spriteId, "spriteId");
            Objects.requireNonNull(loader, "loader");
            Objects.requireNonNull(budget, "budget");
            try (SpriteContents contents = loader.get(
                    SpriteResourceLoader.create(Set.of()))) {
                if (contents == null) {
                    return SourceRead.unavailable(
                            ReadEvidence.DERIVED_UNAVAILABLE,
                            "DERIVED_LOADER_RETURNED_NULL");
                }
                if (!spriteId.equals(contents.name())) {
                    return SourceRead.unavailable(
                            ReadEvidence.DERIVED_UNAVAILABLE,
                            "DERIVED_SPRITE_ID_MISMATCH:"
                                    + contents.name());
                }
                NativeImage image = ((SpriteContentsImageAccessor) contents)
                        .autoseamblend$originalImage();
                int sheetWidth = image.getWidth();
                int sheetHeight = image.getHeight();
                int frameWidth = contents.width();
                int frameHeight = contents.height();
                int pixelCount = IMAGE_BUDGET.requireImage(
                        sheetWidth,
                        sheetHeight,
                        frameWidth,
                        frameHeight);
                budget.reserve(spriteId.toString(), pixelCount);
                PixelAnalysis analysis = analyze(
                        image.getPixelsABGR(),
                        sheetWidth,
                        sheetHeight,
                        frameWidth,
                        frameHeight,
                        pixelCount);
                return SourceRead.available(
                        new SurfaceSourceSnapshot(
                                spriteId.toString(),
                                sheetWidth,
                                sheetHeight,
                                frameWidth,
                                frameHeight,
                                analysis.straightArgb(),
                                contents.isAnimated(),
                                analysis.opaque(),
                                analysis.framedAlpha(),
                                SurfaceSourceProvenance.DERIVED_LOADER),
                        ReadEvidence.DERIVED_PIXELS);
            } catch (TextureImageBudget.ViolationException exception) {
                return unavailableForBudget(
                        "DERIVED",
                        ReadEvidence.DERIVED_UNAVAILABLE,
                        exception);
            } catch (TextureSourceBudget.ViolationException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                return SourceRead.unavailable(
                        ReadEvidence.DERIVED_UNAVAILABLE,
                        "DERIVED_PIXEL_RESOLUTION_FAILED:"
                                + exception.getClass().getSimpleName());
            }
        }

    }

    /**
     * 中文：把 NativeImage 的 ABGR 批量像素副本原地转换为 straight ARGB，并在同一趟循环中
     * 完成 opaque 与 framedAlpha 统计；输出与逐像素 {@code ARGB.fromABGR} 后调用
     * {@code TexturePixelAnalysis.isOpaque/hasFramedAlpha} 逐位等价。
     *
     * <p>English: Converts one NativeImage ABGR bulk pixel copy to straight ARGB in place while
     * computing the opacity and framed-alpha flags in the same pass. The result is bitwise
     * equivalent to applying {@code ARGB.fromABGR} per pixel followed by
     * {@code TexturePixelAnalysis.isOpaque} and {@code TexturePixelAnalysis.hasFramedAlpha}.
     *
     * @param abgrPixels modifiable ABGR copy from {@link NativeImage#getPixelsABGR()}
     * @param pixelCount validated sheet pixel count; must equal the array length
     */
    static PixelAnalysis analyze(
            int[] abgrPixels,
            int sheetWidth,
            int sheetHeight,
            int frameWidth,
            int frameHeight,
            int pixelCount) {
        Objects.requireNonNull(abgrPixels, "abgrPixels");
        if (abgrPixels.length != pixelCount) {
            throw new IllegalArgumentException(
                    "ABGR pixel count " + abgrPixels.length
                            + " does not match " + pixelCount);
        }
        boolean opaque = true;
        boolean trackFramed = frameWidth >= 3
                && frameHeight >= 3;
        int borderWidth = trackFramed
                ? Math.max(
                        1,
                        Math.min(
                                frameWidth,
                                frameHeight) * 3 / 16)
                : 0;
        if (trackFramed
                && (borderWidth * 2 >= frameWidth
                        || borderWidth * 2 >= frameHeight)) {
            trackFramed = false;
        }
        long borderPixels = 0;
        long opaqueBorderPixels = 0;
        long interiorPixels = 0;
        long transparentInteriorPixels = 0;
        for (int y = 0; y < sheetHeight; y++) {
            int localY = y % frameHeight;
            for (int x = 0; x < sheetWidth; x++) {
                int index = y * sheetWidth + x;
                int abgr = abgrPixels[index];
                int alpha = abgr >>> 24;
                if (alpha != 0xFF) {
                    opaque = false;
                }
                abgrPixels[index] = ARGB.fromABGR(abgr);
                if (!trackFramed) {
                    continue;
                }
                int localX = x % frameWidth;
                boolean edge = localX < borderWidth
                        || localX >= frameWidth - borderWidth
                        || localY < borderWidth
                        || localY >= frameHeight - borderWidth;
                if (edge) {
                    borderPixels++;
                    if (alpha >= 128) {
                        opaqueBorderPixels++;
                    }
                } else {
                    interiorPixels++;
                    if (alpha <= 16) {
                        transparentInteriorPixels++;
                    }
                }
            }
        }
        boolean framedAlpha = trackFramed
                && borderPixels > 0
                && interiorPixels > 0
                && opaqueBorderPixels * 4 >= borderPixels
                && transparentInteriorPixels * 5
                        >= interiorPixels * 3;
        if (opaque) {
            framedAlpha = false;
        }
        return new PixelAnalysis(
                abgrPixels,
                opaque,
                framedAlpha);
    }

    /**
     * 中文：单趟像素分析结果；straightArgb 为入参 ABGR 数组的原地转换结果。
     *
     * <p>English: Result of one-pass pixel analysis; {@code straightArgb} is the in-place
     * conversion of the caller-provided ABGR array.
     */
    record PixelAnalysis(
            int[] straightArgb,
            boolean opaque,
            boolean framedAlpha) {
        PixelAnalysis {
            Objects.requireNonNull(
                    straightArgb,
                    "straightArgb");
        }
    }

    private static void validateSourceRead(SourceRead read) {
        read.image().ifPresent(image -> {
            int expectedPixels = IMAGE_BUDGET.requireImage(
                    image.sheetWidth(),
                    image.sheetHeight(),
                    image.frameWidth(),
                    image.frameHeight());
            IMAGE_BUDGET.requirePixelArrayLength(
                    image.straightArgb().length,
                    expectedPixels);
        });
    }

    private static SourceRead unavailableForBudget(
            String sourceKind,
            ReadEvidence frameEvidence,
            TextureImageBudget.ViolationException exception) {
        ReadEvidence evidence = switch (exception.violation().code()) {
            case FRAME_DIMENSION_NON_POSITIVE,
                    FRAME_WIDTH_NOT_DIVISIBLE,
                    FRAME_HEIGHT_NOT_DIVISIBLE -> frameEvidence;
            default -> ReadEvidence.READ_FAILED;
        };
        return SourceRead.unavailable(
                evidence,
                sourceKind
                        + "_TEXTURE_IMAGE_BUDGET_VIOLATION:"
                        + exception.getMessage());
    }
}
