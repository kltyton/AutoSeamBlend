package com.kltyton.autoseamblend.compat.athena.generation;

import com.kltyton.autoseamblend.compat.athena.authoring.AthenaPhysicalTilePlan;
import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSet;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSet.Tile;
import com.kltyton.autoseamblend.texture.generation.GeneratedOverlayProfileIdentity;
import com.kltyton.autoseamblend.texture.generation.GeneratedSpriteTransform;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import com.kltyton.autoseamblend.runtime.surface.SurfaceSourceSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：统一 Athena 生成状态精灵的身份、去重键和首轮像素计划。
 *
 * English: Centralizes Athena generated-state sprite identities, deduplication keys, and the
 * first-pass pixel plan.
 */
public final class AthenaGeneratedSpritePlan {
    public static final String OWNER = "athena";

    private AthenaGeneratedSpritePlan() {}

    /**
     * 中文：生成稳定的 Atlas 精灵 ID；源精灵命名空间和路径保持原样编码在路径中。
     *
     * English: Builds a stable Atlas sprite ID while preserving the source namespace and path in
     * the generated path.
     */
    public static ResourceLocation generatedId(
            ResourceLocation source,
            ConnectionMethod method,
            int slot) {
        return generatedId(
                source,
                method,
                OverlayCutoutProfile.thinUniform(),
                slot);
    }

    public static ResourceLocation generatedId(
            ResourceLocation source,
            ConnectionMethod method,
            OverlayCutoutProfile overlayProfile,
            int slot) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(overlayProfile, "overlayProfile");
        if (slot < 0
                || slot >= AthenaPhysicalTilePlan.ROLE_COUNT) {
            throw new IllegalArgumentException(
                    "Athena state sprite role must be in [0,4]");
        }
        return new ResourceLocation(
                Constants.MOD_ID,
                "generated/athena/"
                        + method.serializedName()
                        + '/'
                        + source.getNamespace()
                        + '/'
                        + source.getPath()
                        + GeneratedOverlayProfileIdentity.pathSuffix(
                                method,
                                overlayProfile)
                        + '/'
                        + slot);
    }

    /**
     * 中文：同一源精灵、方法和 overlay 拓扑共享一套物理状态槽。
     *
     * English: Keys one physical generated-state set by source sprite, method, and overlay
     * topology.
     */
    public static String physicalKey(
            ResourceLocation source,
            ConnectionMethod method,
            OverlayCutoutProfile overlayProfile) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(overlayProfile, "overlayProfile");
        return method.serializedName()
                + '|'
                + source
                + GeneratedOverlayProfileIdentity.keySuffix(
                        method,
                        overlayProfile);
    }

    /**
     * 中文：从冻结源像素和 Athena 五角色计划生成不可变首轮精灵集合。
     *
     * English: Builds an immutable first-pass sprite set from frozen source pixels and Athena's
     * five-role plan.
     */
    public static GeneratedSpriteSet capture(
            String key,
            SurfaceSourceSnapshot source,
            ConnectionMethod method,
            OverlayCutoutProfile overlayProfile) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(overlayProfile, "overlayProfile");
        ResourceLocation sourceId = new ResourceLocation(source.spriteId());
        List<GeneratedTileRecipe> recipes = AthenaPhysicalTilePlan
                .forNativeCarrier(method)
                .recipes();
        ArrayList<Tile> tiles = new ArrayList<>(recipes.size());
        for (int slot = 0; slot < recipes.size(); slot++) {
            tiles.add(new Tile(
                    slot,
                    generatedId(
                            sourceId,
                            method,
                            overlayProfile,
                            slot),
                    new GeneratedSpriteTransform.TileRecipe(
                            recipes.get(slot),
                            overlayProfile)));
        }
        return GeneratedSpriteSet.capture(
                OWNER,
                key,
                sourceId,
                source.sheetWidth(),
                source.sheetHeight(),
                source.frameWidth(),
                source.frameHeight(),
                source.straightArgb(),
                tiles);
    }
}
