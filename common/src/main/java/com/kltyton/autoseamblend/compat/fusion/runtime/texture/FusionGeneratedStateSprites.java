package com.kltyton.autoseamblend.compat.fusion.runtime.texture;

import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.compat.fusion.texture.generation.FusionGeneratedSpriteSetPlanning;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.reload.surface.InitialSurfacePreparation;
import com.kltyton.autoseamblend.reload.surface.InitialSurfacePreparation.Surface;
import com.kltyton.autoseamblend.texture.profile.InitialTextureProfileFactory;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.texture.generation.GeneratedSpritePlanning;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteAtlasResolution;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSet;
import com.kltyton.autoseamblend.texture.generation.GeneratedSpriteIdentity;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.fusion.FusionSheetMethodPlan;
import com.kltyton.autoseamblend.texture.generation.fusion.FusionGeneratedTextureIdentity;
import com.kltyton.autoseamblend.runtime.surface.SurfaceSourceSnapshot;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/** 中文：规划 Fusion 原生连接处理器使用的 Fusion 布局精灵。 / English: Plans Fusion-layout sprites consumed by Fusion's native connecting processor. */
public final class FusionGeneratedStateSprites {
    public static final String OWNER = "fusion";

    private FusionGeneratedStateSprites() {}

    public static void register() {
        GeneratedSpritePlanning.register(
                OWNER,
                FusionGeneratedStateSprites::planInitial);
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
                FusionGeneratedTextureIdentity.physicalKey(
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
        for (Surface surface : prepared.surfaces()) {
            Optional<EngineQuerySelection>
                    selection =
                            EngineQueryRouter
                                    .select(
                                            surface.state(),
                                            planningView)
                                    .filter(value ->
                                            value.family()
                                                    == EngineFamily.FUSION);
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
            if (!FusionSheetMethodPlan.requiresGeneratedSprites(method)
                    || GeneratedSpriteIdentity.isGenerated(sourceId)
                    || FusionSheetMethodPlan.requiresBorderGeneration(method)
                            && Math.min(source.frameWidth(), source.frameHeight()) < 3) {
                continue;
            }
            String key = FusionGeneratedTextureIdentity.physicalKey(
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
                .map(FusionGeneratedStateSprites::captureInitial)
                .toList();
    }

    private static GeneratedSpriteSet captureInitial(
            InitialPlanEntry entry) {
        return FusionGeneratedSpriteSetPlanning.capture(
                OWNER,
                entry.key(),
                entry.source(),
                entry.method(),
                entry.overlayProfile());
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
