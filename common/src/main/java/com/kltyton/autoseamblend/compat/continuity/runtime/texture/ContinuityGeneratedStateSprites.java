package com.kltyton.autoseamblend.compat.continuity.runtime.texture;

import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.compat.continuity.authoring.materialize.ContinuitySlotRecipeDomain;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.reload.surface.InitialSurfacePreparation;
import com.kltyton.autoseamblend.reload.surface.InitialSurfacePreparation.Surface;
import com.kltyton.autoseamblend.texture.profile.InitialTextureProfileFactory;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.texture.generation.GeneratedSpritePlanning;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteAtlasResolution;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSet;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSet.Tile;
import com.kltyton.autoseamblend.texture.generation.GeneratedSpriteTransform;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.ContinuityGeneratedSpritePlan;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import com.kltyton.autoseamblend.runtime.surface.SurfaceSourceSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：规划、解析并公开 NeoContinuity 原生处理器使用的状态精灵。
 *
 * English:
 * Plans, resolves, and exposes state sprites consumed by NeoContinuity's native processors.
 */
public final class ContinuityGeneratedStateSprites {
    public static final String OWNER = ContinuityGeneratedSpritePlan.OWNER;
    private ContinuityGeneratedStateSprites() {}

    public static void register() {
        GeneratedSpritePlanning.register(
                OWNER,
                ContinuityGeneratedStateSprites::planInitial);
    }

    public static Optional<TextureAtlasSprite[]> sprites(
            TextureAtlasSprite source,
            ConnectionMethod method) {
        return sprites(
                ReloadPublication.current(),
                source,
                method,
                OverlayCutoutProfile.thinUniform());
    }

    public static Optional<TextureAtlasSprite[]> sprites(
            TextureAtlasSprite source,
            ConnectionMethod method,
            OverlayCutoutProfile overlayProfile) {
        return sprites(
                ReloadPublication.current(),
                source,
                method,
                overlayProfile);
    }

    public static Optional<TextureAtlasSprite[]> sprites(
            ReloadPublication.Generation generation,
            TextureAtlasSprite source,
            ConnectionMethod method) {
        return sprites(
                generation,
                source,
                method,
                OverlayCutoutProfile.thinUniform());
    }

    public static Optional<TextureAtlasSprite[]> sprites(
            ReloadPublication.Generation generation,
            TextureAtlasSprite source,
            ConnectionMethod method,
            OverlayCutoutProfile overlayProfile) {
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(
                overlayProfile,
                "overlayProfile");
        return GeneratedSpriteAtlasResolution.sprites(
                generation,
                OWNER,
                ContinuityGeneratedSpritePlan.key(
                        source.contents().name(),
                        method,
                        overlayProfile));
    }

    public static List<GeneratedSpriteSet> planInitial(
            InitialSurfacePreparation.Result prepared,
            RuleRuntime.Snapshot ruleSnapshot,
            ReloadPublication.Generation planningView) {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(ruleSnapshot, "ruleSnapshot");
        Objects.requireNonNull(planningView, "planningView");
        LinkedHashMap<String, InitialPlanEntry> entries = new LinkedHashMap<>();
        for (Surface surface : prepared.surfaces()) {
            Optional<EngineQuerySelection>
                    selection =
                            EngineQueryRouter
                                    .select(
                                            surface.state(),
                                            planningView)
                                    .filter(value ->
                                            value.family()
                                                    == EngineFamily.MCPATCHER);
            if (selection.isEmpty()) {
                continue;
            }
            ConnectionMethod method =
                    selection.orElseThrow()
                            .resolveMethod(
                                    surface.state(),
                                    surface.direction(),
                                    ResourceLocation.parse(surface.source().spriteId()));
            SurfaceSourceSnapshot source = surface.source();
            ResourceLocation sourceId = ResourceLocation.parse(source.spriteId());
            OverlayCutoutProfile overlayProfile =
                    InitialTextureProfileFactory.from(source)
                            .overlay(surface.inferenceFacts()
                                    .tintPresent()
                                    .isTrue());
            if (!ContinuityGeneratedSpritePlan.requiresInitialPlan(
                    sourceId,
                    method,
                    source.frameWidth(),
                    source.frameHeight())) {
                continue;
            }
            String key = ContinuityGeneratedSpritePlan.key(
                    sourceId,
                    method,
                    overlayProfile);
            entries.putIfAbsent(
                    key,
                    new InitialPlanEntry(
                            key,
                            method,
                            source,
                            overlayProfile));
        }
        return entries.values().stream()
                .map(ContinuityGeneratedStateSprites::captureInitial)
                .toList();
    }

    private static GeneratedSpriteSet captureInitial(
            InitialPlanEntry entry) {
        SurfaceSourceSnapshot source = entry.source();
        ResourceLocation sourceId = ResourceLocation.parse(source.spriteId());
        ContinuityGeneratedSpritePlan.SetPlan generatedPlan =
                ContinuityGeneratedSpritePlan.plan(
                        sourceId,
                        entry.method(),
                        entry.overlayProfile(),
                        ContinuitySlotRecipeDomain::slots,
                        ContinuitySlotRecipeDomain::recipe);
        List<Tile> tiles = generatedPlan.tiles().stream()
                .map(tile -> new Tile(
                        tile.slot(),
                        tile.spriteId(),
                        new GeneratedSpriteTransform.TileRecipe(
                                tile.recipe(),
                                entry.overlayProfile())))
                .toList();
        return GeneratedSpriteSet.capture(
                OWNER,
                generatedPlan.key(),
                sourceId,
                source.sheetWidth(),
                source.sheetHeight(),
                source.frameWidth(),
                source.frameHeight(),
                source.straightArgb(),
                tiles);
    }

    private record InitialPlanEntry(
            String key,
            ConnectionMethod method,
            SurfaceSourceSnapshot source,
            OverlayCutoutProfile overlayProfile) {
        private InitialPlanEntry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(
                    overlayProfile,
                    "overlayProfile");
        }
    }

}
