package com.kltyton.autoseamblend.compat.athena.runtime.texture;

import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.compat.athena.generation.AthenaGeneratedSpritePlan;
import com.kltyton.autoseamblend.compat.athena.plan.AthenaMethodPolicy;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.reload.surface.InitialSurfacePreparation;
import com.kltyton.autoseamblend.reload.surface.InitialSurfacePreparation.Surface;
import com.kltyton.autoseamblend.texture.profile.InitialTextureProfileFactory;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.texture.generation.GeneratedSpritePlanning;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteAtlasResolution;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import com.kltyton.autoseamblend.runtime.surface.SurfaceSourceSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/** 中文：规划 Athena 原生 CTM 提供器选择的五个角色状态精灵。 / English: Plans the five-role state sprites selected by Athena's native CTM provider. */
public final class AthenaGeneratedStateSprites {
    public static final String OWNER = AthenaGeneratedSpritePlan.OWNER;

    private AthenaGeneratedStateSprites() {}

    public static void register() {
        GeneratedSpritePlanning.register(
                OWNER,
                AthenaGeneratedStateSprites::planInitial);
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
                AthenaGeneratedSpritePlan.physicalKey(
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
                                            planningView);
            if (selection.isEmpty()
                    || selection.orElseThrow().family()
                            != EngineFamily.ATHENA) {
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
            if (!AthenaMethodPolicy.requiresGeneratedSprites(method)
                    || com.kltyton.autoseamblend.texture.generation.GeneratedSpriteIdentity
                            .isGenerated(sourceId)
                    || AthenaMethodPolicy.requiresBorderGeneration(method)
                            && Math.min(source.frameWidth(), source.frameHeight()) < 3) {
                continue;
            }
            String key = AthenaGeneratedSpritePlan.physicalKey(
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
                .map(AthenaGeneratedStateSprites::captureInitial)
                .toList();
    }

    private static GeneratedSpriteSet captureInitial(
            InitialPlanEntry entry) {
        return AthenaGeneratedSpritePlan.capture(
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
