package com.kltyton.autoseamblend.compat.fusion.texture.generation;

import com.kltyton.autoseamblend.runtime.surface.SurfaceSourceSnapshot;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSet;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSet.Tile;
import com.kltyton.autoseamblend.texture.generation.GeneratedSpriteTransform;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe;
import com.kltyton.autoseamblend.texture.generation.fusion.FusionGeneratedTextureIdentity;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import java.util.ArrayList;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：把一个冻结源精灵确定性展开为 Fusion 物理槽位定义；不触碰 Atlas、Loader 或原生模型。
 *
 * English: Deterministically expands one frozen source sprite into Fusion physical-slot
 * definitions without touching an atlas, Loader, or native model.
 */
public final class FusionGeneratedSpriteSetPlanning {
    private FusionGeneratedSpriteSetPlanning() {
    }

    public static GeneratedSpriteSet capture(
            String owner,
            String key,
            SurfaceSourceSnapshot source,
            ConnectionMethod method,
            OverlayCutoutProfile overlayProfile) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(overlayProfile, "overlayProfile");

        ResourceLocation sourceId = ResourceLocation.parse(source.spriteId());
        FusionNativeSheetPlan sheet = FusionNativeSheetPlan.create(method);
        ArrayList<Tile> tiles = new ArrayList<>(
                sheet.tileRecipes().size());
        for (int slot = 0; slot < sheet.tileRecipes().size(); slot++) {
            GeneratedTileRecipe recipe = sheet.tileRecipes()
                    .get(slot)
                    .orElse(GeneratedTileRecipe.Source.INSTANCE);
            tiles.add(new Tile(
                    slot,
                    FusionGeneratedTextureIdentity.generatedId(
                            sourceId,
                            method,
                            overlayProfile,
                            slot),
                    new GeneratedSpriteTransform.TileRecipe(
                            recipe,
                            overlayProfile)));
        }
        return GeneratedSpriteSet.capture(
                owner,
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
