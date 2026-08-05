package com.kltyton.autoseamblend.compat.ctm_mod.runtime.texture;

import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe;
import com.kltyton.autoseamblend.texture.generation.GeneratedOverlayProfileIdentity;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.Identifier;

/**
 * 中文：规划 CTM Mod 生成精灵的稳定键、ID 与像素配方；不触碰 Atlas 或 NeoForge 生命周期。
 *
 * <p>English: Plans stable keys, IDs, and pixel recipes for CTM Mod generated sprites without
 * touching Atlas or NeoForge lifecycle APIs.</p>
 */
public final class CtmModGeneratedSpritePlan {
    public static final String OWNER = "ctm";

    private CtmModGeneratedSpritePlan() {}

    public static SetPlan plan(
            Identifier source,
            ConnectionMethod method,
            OverlayCutoutProfile overlayProfile) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(overlayProfile, "overlayProfile");
        if (method == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException("auto must be resolved before sprite planning");
        }
        if (com.kltyton.autoseamblend.texture.generation.GeneratedSpriteIdentity.isGenerated(source)) {
            throw new IllegalArgumentException("generated sprites cannot recursively seed planning");
        }
        List<GeneratedTileRecipe> recipes = CtmModMethodStateDomain.recipes(method);
        ArrayList<TilePlan> tiles = new ArrayList<>(recipes.size());
        for (int slot = 0; slot < recipes.size(); slot++) {
            tiles.add(new TilePlan(
                    slot,
                    generatedId(source, method, overlayProfile, slot),
                    recipes.get(slot)));
        }
        return new SetPlan(key(source, method, overlayProfile), source, method, overlayProfile, tiles);
    }

    public static boolean requiresGeneratedSprites(ConnectionMethod method) {
        return CtmModMethodStateDomain.requiresGeneratedResult(method);
    }

    public static boolean requiresBorderGeneration(ConnectionMethod method) {
        return switch (Objects.requireNonNull(method, "method")) {
            case CTM, CTM_COMPACT, HORIZONTAL, VERTICAL,
                    HORIZONTAL_VERTICAL, VERTICAL_HORIZONTAL, OVERLAY_CTM -> true;
            case RUNTIME_BLEND, TOP, OVERLAY, FIXED, NONE -> false;
            case AUTO -> throw new IllegalArgumentException("auto must be resolved before border generation");
        };
    }

    public static String key(
            Identifier source,
            ConnectionMethod method,
            OverlayCutoutProfile overlayProfile) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(overlayProfile, "overlayProfile");
        if (method == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException("auto must be resolved before sprite planning");
        }
        return method.serializedName()
                + '|' + source
                + GeneratedOverlayProfileIdentity.keySuffix(method, overlayProfile);
    }

    private static Identifier generatedId(
            Identifier source,
            ConnectionMethod method,
            OverlayCutoutProfile overlayProfile,
            int slot) {
        if (slot < 0) {
            throw new IllegalArgumentException("slot must be non-negative");
        }
        return Identifier.fromNamespaceAndPath(
                Constants.MOD_ID,
                "generated/ctm/"
                        + method.serializedName() + '/'
                        + source.getNamespace() + '/'
                        + source.getPath()
                        + GeneratedOverlayProfileIdentity.pathSuffix(method, overlayProfile)
                        + '/' + slot);
    }

    public record SetPlan(
            String key,
            Identifier source,
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

    public record TilePlan(int slot, Identifier spriteId, GeneratedTileRecipe recipe) {
        public TilePlan {
            if (slot < 0) {
                throw new IllegalArgumentException("slot must be non-negative");
            }
            Objects.requireNonNull(spriteId, "spriteId");
            Objects.requireNonNull(recipe, "recipe");
        }
    }
}
