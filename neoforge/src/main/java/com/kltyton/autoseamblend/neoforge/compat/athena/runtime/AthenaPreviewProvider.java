package com.kltyton.autoseamblend.neoforge.compat.athena.runtime;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.compat.athena.authoring.AthenaPhysicalTilePlan;
import com.kltyton.autoseamblend.compat.athena.plan.AthenaMethodPolicy;
import com.kltyton.autoseamblend.authoring.preview.PreviewConnectionDonors;
import com.kltyton.autoseamblend.authoring.preview.PreviewFaceResult;
import com.kltyton.autoseamblend.authoring.preview.PreviewFaceResult.Layer;
import com.kltyton.autoseamblend.authoring.preview.PreviewProvider;
import com.kltyton.autoseamblend.authoring.preview.PreviewQuery;
import com.kltyton.autoseamblend.authoring.preview.PreviewSample;
import com.kltyton.autoseamblend.authoring.preview.BlockPreviewTint;
import com.kltyton.autoseamblend.neoforge.compat.athena.runtime.overlay.AthenaNativeOverlayStateSampler;
import com.kltyton.autoseamblend.compat.athena.runtime.texture.AthenaGeneratedStateSprites;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolution;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolver.Donor;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import com.kltyton.autoseamblend.runtime.overlay.PlanarOverlayNeighborhood;
import earth.terrarium.athena.api.client.utils.CtmState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/**
 * 中文：为当前世界表面采样 Athena 原生的外观感知 CTM 状态。 / English: Samples Athena's native appearance-aware CTM state for the current world surface.
 */
public enum AthenaPreviewProvider
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
        if (query.resolvedMethod().overlayCapable()) {
            List<Direction> directions = PlanarOverlayNeighborhood
                    .planarDirections(query.face());
            List<Donor> donors = query.usesDocumentConnectionBlocks()
                    ? PreviewConnectionDonors.resolve(
                    query, directions)
                    : OverlayDonorResolution.resolveAll(
                    query.level(),
                    query.pos(),
                    query.face(),
                    query.state(),
                    query.rules().rules(),
                    query.surfaces(),
                    family(),
                    directions);
            if (query.usesDocumentConnectionBlocks()) {
                return documentSamples(query, donors);
            }
            if (donors.isEmpty()) {
                return List.of(new PreviewSample(
                        NeighborConnections.fromBits(0xFF),
                        query.state(),
                        query.surface(),
                        query.resolvedMethod()));
            }
            ArrayList<PreviewSample> samples =
                    new ArrayList<>(donors.size());
            for (Donor selected : donors) {
                CtmState nativeState =
                        AthenaNativeOverlayStateSampler.state(
                                query,
                                selected);
                if (nativeState.allTrue()) {
                    continue;
                }
                NeighborConnections connections =
                        AthenaNativeConnectionSampler.connections(
                                nativeState);
                samples.add(new PreviewSample(
                        connections,
                        selected.state(),
                        selected.surface(),
                        selected.method()));
            }
            return samples.isEmpty()
                    ? List.of(new PreviewSample(
                    NeighborConnections.fromBits(0xFF),
                    query.state(),
                    query.surface(),
                    query.resolvedMethod()))
                    : List.copyOf(samples);
        }
        return List.of(new PreviewSample(
                AthenaNativeConnectionSampler.sample(
                        query.level(),
                        query.pos(),
                        query.state(),
                        query.face(),
                        query.rules().rules(),
                        query.authoringConnectionBlocks()),
                query.state(),
                query.surface(),
                query.resolvedMethod()));
    }

    @Override
    public Optional<PreviewFaceResult> exactFace(
            PreviewQuery query,
            List<PreviewSample> samples) {
        if (samples.isEmpty()
                || query.resolvedMethod()
                == ConnectionMethod.TOP) {
            return Optional.empty();
        }
        if (!AthenaMethodPolicy.requiresGeneratedSprites(
                query.resolvedMethod())) {
            return Optional.of(PreviewFaceResult.full(
                    query.surface().sprite(),
                    BlockPreviewTint.color(
                            query.level(),
                            query.pos(),
                            query.state(),
                            query.surface())));
        }
        if (!query.resolvedMethod().overlayCapable()) {
            PreviewSample sample = samples.getFirst();
            return selectedSprite(sample).map(sprite ->
                    PreviewFaceResult.full(
                            sprite,
                            BlockPreviewTint.color(
                                    query.level(),
                                    query.pos(),
                                    sample.sourceState(),
                                    sample.sourceSurface())));
        }
        ArrayList<Layer> layers = new ArrayList<>();
        layers.add(Layer.full(
                query.surface().sprite(),
                BlockPreviewTint.color(
                        query.level(),
                        query.pos(),
                        query.state(),
                        query.surface())));
        for (PreviewSample sample : samples) {
            Optional<TextureAtlasSprite> selected =
                    selectedSprite(sample);
            if (selected.isEmpty()) {
                return Optional.empty();
            }
            layers.add(Layer.full(
                    selected.orElseThrow(),
                    BlockPreviewTint.color(
                            query.level(),
                            query.pos(),
                            sample.sourceState(),
                            sample.sourceSurface())));
        }
        return Optional.of(new PreviewFaceResult(layers));
    }

    private static Optional<TextureAtlasSprite> selectedSprite(
            PreviewSample sample) {
        return AthenaGeneratedStateSprites
                .sprites(
                        sample.sourceSurface().sprite(),
                        sample.renderMethod(),
                        sample.sourceSurface()
                                .overlayProfile())
                .flatMap(sprites -> {
                    int slot = AthenaPhysicalTilePlan
                            .roleFor(sample.connections())
                            .nativeIndex();
                    return slot >= 0
                            && slot < sprites.length
                            && sprites[slot] != null
                            ? Optional.of(sprites[slot])
                            : Optional.empty();
                });
    }

    private static List<PreviewSample> documentSamples(
            PreviewQuery query,
            List<Donor> donors) {
        ArrayList<PreviewSample> samples =
                new ArrayList<>(donors.size());
        for (Donor donor : donors) {
            CtmState nativeState =
                    AthenaNativeOverlayStateSampler.state(
                            query,
                            donor);
            if (nativeState.allTrue()) {
                continue;
            }
            NeighborConnections connections =
                    AthenaNativeConnectionSampler.connections(
                            nativeState);
            samples.add(new PreviewSample(
                    connections,
                    donor.state(),
                    donor.surface(),
                    donor.method()));
        }
        return samples.isEmpty()
                ? List.of(new PreviewSample(
                NeighborConnections.fromBits(0xFF),
                query.state(),
                query.surface(),
                query.resolvedMethod()))
                : List.copyOf(samples);
    }
}
