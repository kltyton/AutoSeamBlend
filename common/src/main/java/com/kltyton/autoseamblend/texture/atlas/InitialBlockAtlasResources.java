package com.kltyton.autoseamblend.texture.atlas;

import com.kltyton.autoseamblend.mixin.minecraft.SpriteContentsImageAccessor;
import com.kltyton.autoseamblend.mixin.minecraft.SpriteSourceListAccessor;
import com.kltyton.autoseamblend.runtime.surface.SurfaceSourceProvenance;
import com.kltyton.autoseamblend.runtime.surface.SurfaceSourceSnapshot;
import com.kltyton.autoseamblend.texture.analysis.TexturePixelAnalysis;
import com.kltyton.autoseamblend.texture.budget.TextureImageBudget;
import com.kltyton.autoseamblend.texture.budget.TextureInputBudget;
import com.kltyton.autoseamblend.texture.budget.TextureSourceBudget;
import com.kltyton.autoseamblend.texture.io.NativeArgb;
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
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 中文：在首次方块 Atlas 缝合前捕获最终直接/派生来源；Loader 只提供生成来源判定。
 *
 * English: Captures final direct and derived block-atlas sources before the first stitch; each
 * Loader only supplies its generated-source predicate.
 */
public final class InitialBlockAtlasResources {
    private static final TextureImageBudget IMAGE_BUDGET =
            TextureImageBudget.DEFAULT;
    // 中文：1.20.1 的 SpriteSourceList.load 使用 FileToIdConverter("atlases", ".json")
    // 把 Atlas 信息 ID（"minecraft:blocks"）映射到 "minecraft:atlases/blocks.json"；
    // 这里不能传 TextureAtlas.LOCATION_BLOCKS（"minecraft:textures/atlas/blocks"）。
    // English: In 1.20.1 SpriteSourceList.load maps the atlas INFO id ("minecraft:blocks")
    // through FileToIdConverter("atlases", ".json") to "minecraft:atlases/blocks.json";
    // TextureAtlas.LOCATION_BLOCKS ("minecraft:textures/atlas/blocks") must not be passed.
    private static final ResourceLocation BLOCK_ATLAS_INFO =
            new ResourceLocation("blocks");

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
            Set<ResourceLocation> requiredSprites,
            Predicate<SpriteSource> generatedSource) {
        Objects.requireNonNull(requiredSprites, "requiredSprites");
        LinkedHashSet<ResourceLocation> required = new LinkedHashSet<>();
        for (ResourceLocation spriteId : requiredSprites) {
            required.add(Objects.requireNonNull(spriteId, "required sprite"));
        }
        return capture(
                resources,
                Optional.of(Collections.unmodifiableSet(required)),
                generatedSource);
    }

    private static Snapshot capture(
            ResourceManager resources,
            Optional<Set<ResourceLocation>> requiredSprites,
            Predicate<SpriteSource> generatedSource) {
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(requiredSprites, "requiredSprites");
        Predicate<SpriteSource> skip = Objects.requireNonNull(
                generatedSource,
                "generatedSource");
        CaptureOutput output = new CaptureOutput();
        SpriteResourceLoader sources = SpriteResourceLoader.load(
                resources,
                BLOCK_ATLAS_INFO);
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
        private final Map<ResourceLocation, SourceRead> reads;

        public Snapshot(Map<ResourceLocation, SourceRead> reads) {
            LinkedHashMap<ResourceLocation, SourceRead> copy =
                    new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, SourceRead> entry
                    : Objects.requireNonNull(reads, "reads").entrySet()) {
                Map.Entry<ResourceLocation, SourceRead> checked =
                        Objects.requireNonNull(entry, "reads entry");
                ResourceLocation spriteId = Objects.requireNonNull(
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

        public Map<ResourceLocation, SourceRead> reads() {
            return reads;
        }

        public SourceRead read(ResourceLocation spriteId) {
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
        private final LinkedHashMap<ResourceLocation, PendingResource> pending =
                new LinkedHashMap<>();

        @Override
        public void add(ResourceLocation spriteId, Resource resource) {
            replace(
                    Objects.requireNonNull(spriteId, "spriteId"),
                    new DirectResource(Objects.requireNonNull(
                            resource,
                            "resource")));
        }

        @Override
        public void add(
                ResourceLocation spriteId,
                SpriteSource.SpriteSupplier loader) {
            replace(
                    Objects.requireNonNull(spriteId, "spriteId"),
                    new DerivedResource(Objects.requireNonNull(
                            loader,
                            "loader")));
        }

        @Override
        public void removeAll(Predicate<ResourceLocation> predicate) {
            Predicate<ResourceLocation> checked = Objects.requireNonNull(
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
                ResourceLocation spriteId,
                PendingResource resource) {
            PendingResource previous = pending.put(
                    spriteId,
                    resource);
            if (previous != null) {
                previous.discard();
            }
        }

        private Map<ResourceLocation, SourceRead> resolve(
                Optional<Set<ResourceLocation>> requiredSprites) {
            Objects.requireNonNull(requiredSprites, "requiredSprites");
            LinkedHashMap<ResourceLocation, SourceRead> reads =
                    new LinkedHashMap<>();
            TextureSourceBudget.Accumulator budget =
                    TextureSourceBudget.DEFAULT.accumulator();
            var iterator = pending.entrySet().iterator();
            try {
                while (iterator.hasNext()) {
                    Map.Entry<ResourceLocation, PendingResource> entry =
                            iterator.next();
                    ResourceLocation spriteId = entry.getKey();
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
                    ResourceLocation spriteId,
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
                    ResourceLocation spriteId,
                    TextureSourceBudget.Accumulator budget) {
                return resolveDirect(spriteId, resource, budget);
            }
        }

        private record DerivedResource(
                SpriteSource.SpriteSupplier loader)
                implements PendingResource {
            private DerivedResource {
                Objects.requireNonNull(loader, "loader");
            }

            @Override
            public SourceRead resolve(
                    ResourceLocation spriteId,
                    TextureSourceBudget.Accumulator budget) {
                return resolveDerived(spriteId, loader, budget);
            }

            @Override
            public void discard() {
                loader.discard();
            }
        }

        private static SourceRead resolveDirect(
                ResourceLocation spriteId,
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
                                    AnimationMetadataSection.SERIALIZER);
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
                    int[] pixels = pixels(
                            image,
                            sheetWidth,
                            sheetHeight,
                            pixelCount);
                    return SourceRead.available(
                            new SurfaceSourceSnapshot(
                                    spriteId.toString(),
                                    sheetWidth,
                                    sheetHeight,
                                    frame.width(),
                                    frame.height(),
                                    pixels,
                                    animation.isPresent(),
                                    TexturePixelAnalysis.isOpaque(pixels),
                                    TexturePixelAnalysis.hasFramedAlpha(
                                            sheetWidth,
                                            sheetHeight,
                                            frame.width(),
                                            frame.height(),
                                            pixels),
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
                ResourceLocation spriteId,
                SpriteSource.SpriteSupplier loader,
                TextureSourceBudget.Accumulator budget) {
            Objects.requireNonNull(spriteId, "spriteId");
            Objects.requireNonNull(loader, "loader");
            Objects.requireNonNull(budget, "budget");
            // 1.20.1 SpriteSource.SpriteSupplier extends Supplier<SpriteContents>; no apply(loader).
            try (SpriteContents contents = loader.get()) {
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
                int[] pixels = pixels(
                        image,
                        sheetWidth,
                        sheetHeight,
                        pixelCount);
                return SourceRead.available(
                        new SurfaceSourceSnapshot(
                                spriteId.toString(),
                                sheetWidth,
                                sheetHeight,
                                frameWidth,
                                frameHeight,
                                pixels,
                                contents.getUniqueFrames().count() > 1,
                                TexturePixelAnalysis.isOpaque(pixels),
                                TexturePixelAnalysis.hasFramedAlpha(
                                        sheetWidth,
                                        sheetHeight,
                                        frameWidth,
                                        frameHeight,
                                        pixels),
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

        private static int[] pixels(
                NativeImage image,
                int sheetWidth,
                int sheetHeight,
                int pixelCount) {
            int[] pixels = new int[pixelCount];
            for (int y = 0; y < sheetHeight; y++) {
                for (int x = 0; x < sheetWidth; x++) {
                    pixels[y * sheetWidth + x] =
                            NativeArgb.toIr(
                                    image.getPixelRGBA(x, y));
                }
            }
            return pixels;
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
