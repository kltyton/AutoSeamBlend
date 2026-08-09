package com.kltyton.autoseamblend.texture.generation;

import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：统一 Continuity 生成精灵的需求、键、ID 与项目自有像素配方计划；不接触 Loader 或第三方 API。
 * English: Centralizes Continuity generated-sprite requirements, keys, IDs, and project-owned pixel recipes without Loader or third-party APIs.
 */
public final class ContinuityGeneratedSpritePlan {
    public static final String OWNER = "continuity";

    private ContinuityGeneratedSpritePlan() {}

    public static SetPlan plan(
            ResourceLocation source,
            ConnectionMethod method,
            OverlayCutoutProfile overlayProfile,
            Function<ConnectionMethod, List<Integer>> slotResolver,
            BiFunction<ConnectionMethod, Integer, GeneratedTileRecipe> recipeResolver) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(overlayProfile, "overlayProfile");
        Objects.requireNonNull(slotResolver, "slotResolver");
        Objects.requireNonNull(recipeResolver, "recipeResolver");
        if (method == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException("auto must be resolved before sprite planning");
        }
        if (GeneratedSpriteIdentity.isGenerated(source)) {
            throw new IllegalArgumentException("generated sprites cannot recursively seed planning");
        }
        List<TilePlan> tiles = requiresGeneratedSprites(method)
                ? List.copyOf(Objects.requireNonNull(slotResolver.apply(method), "slots")).stream()
                        .map(
                                slot ->
                                        new TilePlan(
                                                slot,
                                                generatedId(source, method, overlayProfile, slot),
                                                recipeResolver.apply(method, slot)))
                        .toList()
                : List.of();
        return new SetPlan(
                key(source, method, overlayProfile),
                source,
                method,
                overlayProfile,
                tiles);
    }

    public static boolean requiresGeneratedSprites(ConnectionMethod method) {
        return switch (Objects.requireNonNull(method, "method")) {
            case RUNTIME_BLEND,
                    CTM,
                    CTM_COMPACT,
                    HORIZONTAL,
                    VERTICAL,
                    HORIZONTAL_VERTICAL,
                    VERTICAL_HORIZONTAL,
                    OVERLAY,
                    OVERLAY_CTM -> true;
            case TOP, FIXED, NONE -> false;
            case AUTO -> throw new IllegalArgumentException(
                    "auto must be resolved before sprite planning");
        };
    }

    public static boolean requiresBorderGeneration(ConnectionMethod method) {
        return switch (Objects.requireNonNull(method, "method")) {
            case CTM,
                    CTM_COMPACT,
                    HORIZONTAL,
                    VERTICAL,
                    HORIZONTAL_VERTICAL,
                    VERTICAL_HORIZONTAL,
                    OVERLAY_CTM -> true;
            case RUNTIME_BLEND, TOP, OVERLAY, FIXED, NONE -> false;
            case AUTO -> throw new IllegalArgumentException(
                    "auto must be resolved before border generation");
        };
    }

    /**
     * 中文：判断首轮资源准备是否应为一个原始源纹理建立 Continuity 生成计划。
     * English: Determines whether initial resource preparation should create a Continuity
     * generated-sprite plan for an original source texture.
     */
    public static boolean requiresInitialPlan(
            ResourceLocation source,
            ConnectionMethod method,
            int frameWidth,
            int frameHeight) {
        Objects.requireNonNull(source, "source");
        ConnectionMethod resolved = Objects.requireNonNull(method, "method");
        return requiresGeneratedSprites(resolved)
                && !GeneratedSpriteIdentity.isGenerated(source)
                && (!requiresBorderGeneration(resolved)
                        || Math.min(frameWidth, frameHeight) >= 3);
    }

    public static String key(
            ResourceLocation source,
            ConnectionMethod method,
            OverlayCutoutProfile overlayProfile) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(overlayProfile, "overlayProfile");
        if (method == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException("auto must be resolved before sprite planning");
        }
        return method.serializedName()
                + '|'
                + source
                + GeneratedOverlayProfileIdentity.keySuffix(method, overlayProfile);
    }

    private static ResourceLocation generatedId(
            ResourceLocation source,
            ConnectionMethod method,
            OverlayCutoutProfile overlayProfile,
            int slot) {
        if (slot < 0) {
            throw new IllegalArgumentException("slot must be non-negative");
        }
        return ResourceLocation.fromNamespaceAndPath(
                Constants.MOD_ID,
                "generated/continuity/"
                        + method.serializedName()
                        + '/'
                        + source.getNamespace()
                        + '/'
                        + source.getPath()
                        + GeneratedOverlayProfileIdentity.pathSuffix(method, overlayProfile)
                        + '/'
                        + slot);
    }


    /**
     * 中文：一个 loader-neutral 的生成精灵集合计划。
     * English: Loader-neutral generated-sprite set plan.
     */
    public record SetPlan(
            String key,
            ResourceLocation source,
            ConnectionMethod method,
            OverlayCutoutProfile overlayProfile,
            List<TilePlan> tiles) {
        public SetPlan {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("key must not be blank");
            }
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(overlayProfile, "overlayProfile");
            if (method == ConnectionMethod.AUTO) {
                throw new IllegalArgumentException("auto must be resolved before sprite planning");
            }
            tiles = List.copyOf(Objects.requireNonNull(tiles, "tiles"));
            if (tiles.stream().map(TilePlan::slot).distinct().count() != tiles.size()) {
                throw new IllegalArgumentException("sprite plan slots must be unique");
            }
        }
    }

    /**
     * 中文：一个槽位的稳定 ID 与项目自有像素配方。
     * English: Stable ID and project-owned pixel recipe for one generated slot.
     */
    public record TilePlan(int slot, ResourceLocation spriteId, GeneratedTileRecipe recipe) {
        public TilePlan {
            if (slot < 0) {
                throw new IllegalArgumentException("slot must be non-negative");
            }
            Objects.requireNonNull(spriteId, "spriteId");
            Objects.requireNonNull(recipe, "recipe");
        }
    }
}
