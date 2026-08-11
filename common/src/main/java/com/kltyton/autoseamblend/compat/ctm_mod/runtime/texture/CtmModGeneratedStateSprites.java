package com.kltyton.autoseamblend.compat.ctm_mod.runtime.texture;

import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.compat.ctm_mod.runtime.texture.CtmModGeneratedSpritePlan;
import com.kltyton.autoseamblend.reload.surface.InitialSurfacePreparation;
import com.kltyton.autoseamblend.reload.surface.InitialSurfacePreparation.Surface;
import com.kltyton.autoseamblend.texture.profile.InitialTextureProfileFactory;
import com.kltyton.autoseamblend.texture.profile.InitialTextureProfiles;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.texture.generation.GeneratedSpritePlanning;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteAtlasResolution;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSet;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSet.Tile;
import com.kltyton.autoseamblend.texture.generation.GeneratedSpriteTransform;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import com.kltyton.autoseamblend.runtime.surface.SurfaceSourceSnapshot;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/** 中文：从 CTM Lib 原生标准连接键选择的内存完整状态精灵。 / English: In-memory full-state sprites selected from CTM Lib's native standard connection key. */
public final class CtmModGeneratedStateSprites {
    public static final String OWNER = CtmModGeneratedSpritePlan.OWNER;

    private CtmModGeneratedStateSprites() {}

    public static void register() {
        GeneratedSpritePlanning.register(
                OWNER,
                CtmModGeneratedStateSprites::planInitial);
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
                CtmModGeneratedSpritePlan.key(
                        source.contents().name(),
                        method,
                        overlayProfile));
    }

    public static List<GeneratedSpriteSet> planInitial(
            InitialSurfacePreparation.Result prepared,
            RuleRuntime.Snapshot rules,
            ReloadPublication.Generation planningView) {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(planningView, "planningView");
        LinkedHashMap<String, InitialPlanEntry> entries =
                new LinkedHashMap<>();
        // 中文：一次 planner 调用内按 source 实例缓存 alpha/overlay 档，避免重复扫描；不跨 reload。
        // English: Cache frame/overlay profiles per source instance for one planner call only.
        IdentityHashMap<SurfaceSourceSnapshot, InitialTextureProfiles>
                profilesBySource =
                        new IdentityHashMap<>();
        for (Surface surface : prepared.surfaces()) {
            Optional<EngineQuerySelection>
                    selection =
                            EngineQueryRouter
                                    .select(
                                            surface.state(),
                                            planningView)
                                    .filter(value ->
                                            value.family()
                                                    == EngineFamily.CTM_MOD);
            if (selection.isEmpty()) {
                continue;
            }
            ConnectionMethod method =
                    selection.orElseThrow()
                            .resolveMethod(
                                    surface.state(),
                                    surface.direction(),
                                    new ResourceLocation(surface.source().spriteId()));
            SurfaceSourceSnapshot source = surface.source();
            ResourceLocation sourceId = new ResourceLocation(source.spriteId());
            if (!CtmModGeneratedSpritePlan.requiresGeneratedSprites(method)
                    || com.kltyton.autoseamblend.texture.generation.GeneratedSpriteIdentity
                            .isGenerated(sourceId)
                    || CtmModGeneratedSpritePlan.requiresBorderGeneration(method)
                            && Math.min(source.frameWidth(), source.frameHeight()) < 3) {
                continue;
            }
            OverlayCutoutProfile overlayProfile =
                    profilesBySource
                            .computeIfAbsent(
                                    source,
                                    InitialTextureProfileFactory::from)
                            .overlay(surface.inferenceFacts()
                                    .tintPresent()
                                    .isTrue());
            String key = CtmModGeneratedSpritePlan.key(
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
                .map(CtmModGeneratedStateSprites::captureInitial)
                .toList();
    }

    private static GeneratedSpriteSet captureInitial(
            InitialPlanEntry entry) {
        SurfaceSourceSnapshot source = entry.source();
        ResourceLocation sourceId = new ResourceLocation(source.spriteId());
        CtmModGeneratedSpritePlan.SetPlan generatedPlan =
                CtmModGeneratedSpritePlan.plan(
                        sourceId,
                        entry.method(),
                        entry.overlayProfile());
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
