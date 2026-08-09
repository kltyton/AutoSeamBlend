package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import com.kltyton.autoseamblend.authoring.preview.BlockPreviewTint;
import com.kltyton.autoseamblend.authoring.preview.PreviewFaceResult;
import com.kltyton.autoseamblend.authoring.preview.PreviewProvider;
import com.kltyton.autoseamblend.authoring.preview.PreviewQuery;
import com.kltyton.autoseamblend.authoring.preview.PreviewSample;
import com.kltyton.autoseamblend.compat.athena.authoring.AthenaPhysicalTilePlan;
import com.kltyton.autoseamblend.compat.athena.runtime.texture.AthenaGeneratedStateSprites;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * 中文：Athena 预览提供器：优先使用生成状态精灵，缺失时透传原表面。
 * English: Athena preview provider preferring generated state sprites and
 * passing through the original surface when unavailable.
 */
public enum FabricAthenaPreviewProvider
        implements PreviewProvider {
    INSTANCE;

    @Override
    public String engineId() {
        return "athena";
    }

    @Override
    public EngineFamily family() {
        return EngineFamily.ATHENA;
    }

    @Override
    public List<PreviewSample> sample(
            PreviewQuery query) {
        return List.of(new PreviewSample(
                NeighborConnections.none(),
                query.state(),
                query.surface(),
                query.resolvedMethod()));
    }

    @Override
    public Optional<PreviewFaceResult> exactFace(
            PreviewQuery query,
            List<PreviewSample> samples) {
        Optional<TextureAtlasSprite[]> generated =
                AthenaGeneratedStateSprites.sprites(
                        query.surface().sprite(),
                        query.resolvedMethod(),
                        query.surface().overlayProfile());
        TextureAtlasSprite sprite = generated
                .filter(sprites -> sprites.length > 0)
                .map(sprites -> selectedSprite(
                        sprites,
                        samples))
                .orElse(query.surface().sprite());
        return Optional.of(
                PreviewFaceResult.full(
                        sprite,
                        BlockPreviewTint.color(
                                query.level(),
                                query.pos(),
                                query.state(),
                                query.surface())));
    }

    private static TextureAtlasSprite selectedSprite(
            TextureAtlasSprite[] sprites,
            List<PreviewSample> samples) {
        if (samples.isEmpty()) {
            return sprites[0];
        }
        int slot = AthenaPhysicalTilePlan
                .roleFor(samples.getFirst().connections())
                .nativeIndex();
        return slot >= 0
                        && slot < sprites.length
                        && sprites[slot] != null
                ? sprites[slot]
                : sprites[0];
    }
}
