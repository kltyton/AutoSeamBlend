package com.kltyton.autoseamblend.compat.fusion.runtime;

import com.kltyton.autoseamblend.compat.fusion.runtime.FusionNativeQuadProcessor;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.authoring.preview.PreviewConnectionDonors;
import com.kltyton.autoseamblend.authoring.preview.PreviewFaceResult;
import com.kltyton.autoseamblend.authoring.preview.PreviewFaceResult.Layer;
import com.kltyton.autoseamblend.authoring.preview.PreviewProvider;
import com.kltyton.autoseamblend.authoring.preview.PreviewQuery;
import com.kltyton.autoseamblend.authoring.preview.PreviewSample;
import com.kltyton.autoseamblend.authoring.preview.BlockPreviewTint;
import com.kltyton.autoseamblend.compat.fusion.texture.generation.FusionNativeSheetPlan;
import com.kltyton.autoseamblend.compat.fusion.runtime.texture.FusionGeneratedStateSprites;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolution;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolver.Donor;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：使用当前烘焙面和世界查询采样 Fusion 原生处理器。 / English: Samples Fusion's native processor with the current baked face and world query.
 */
public enum FusionPreviewProvider
        implements PreviewProvider {
    INSTANCE;

    @Override
    public String engineId() {
        return "fusion";
    }

    @Override
    public EngineFamily family() {
        return EngineFamily.FUSION;
    }

    @Override
    public List<PreviewSample> sample(
            PreviewQuery query) {
        if (!requiresNativeConnections(
                query.resolvedMethod())) {
            return passthrough(query);
        }
        if (query.resolvedMethod().overlayCapable()) {
            List<Direction> directions = planarDirections(query);
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
                return passthrough(query);
            }
            ArrayList<PreviewSample> samples =
                    new ArrayList<>(donors.size());
            for (Donor selected : donors) {
                NeighborConnections connections =
                        processor(
                                query,
                                selected.surface(),
                                selected.state(),
                                selected.method())
                                .flatMap(value -> value.connections(
                                        query.level(),
                                        query.pos(),
                                        selected.state(),
                                        0L))
                                .orElseGet(
                                        NeighborConnections::none);
                if (connections.bits() != 0) {
                    samples.add(new PreviewSample(
                            connections,
                            selected.state(),
                            selected.surface(),
                            selected.method()));
                }
            }
            return samples.isEmpty()
                    ? passthrough(query)
                    : List.copyOf(samples);
        }
        return processor(
                query,
                query.surface(),
                query.state(),
                query.resolvedMethod())
                .flatMap(value -> value.connections(
                        query.level(),
                        query.pos(),
                        query.state(),
                        0L))
                .map(connections ->
                        List.of(new PreviewSample(
                                connections,
                                query.state(),
                                query.surface(),
                                query.resolvedMethod())))
                .orElseGet(List::of);
    }

    private static List<PreviewSample> passthrough(
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
        if (samples.isEmpty()
                || query.resolvedMethod()
                == ConnectionMethod.TOP) {
            return Optional.empty();
        }
        if (!requiresNativeConnections(
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
            return selectedLayers(sample).map(layers ->
                    new PreviewFaceResult(color(
                            query,
                            sample,
                            layers)));
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
            Optional<List<TextureAtlasSprite>> selected =
                    selectedLayers(sample);
            if (selected.isEmpty()) {
                return Optional.empty();
            }
            layers.addAll(color(
                    query,
                    sample,
                    selected.orElseThrow()));
        }
        return Optional.of(new PreviewFaceResult(layers));
    }

    private static Optional<List<TextureAtlasSprite>>
    selectedLayers(
            PreviewSample sample) {
        return FusionGeneratedStateSprites
                .sprites(
                        sample.sourceSurface().sprite(),
                        sample.renderMethod(),
                        sample.sourceSurface()
                                .overlayProfile())
                .flatMap(sprites -> {
                    ArrayList<TextureAtlasSprite> selected =
                            new ArrayList<>();
                    for (int tile : FusionNativeSheetPlan
                            .selectedTiles(
                                    sample.renderMethod(),
                                    sample.connections())) {
                        if (tile < 0
                                || tile >= sprites.length
                                || sprites[tile] == null) {
                            return Optional.empty();
                        }
                        selected.add(sprites[tile]);
                    }
                    return selected.isEmpty()
                            ? Optional.empty()
                            : Optional.of(List.copyOf(selected));
                });
    }

    private static List<Layer> color(
            PreviewQuery query,
            PreviewSample sample,
            List<TextureAtlasSprite> sprites) {
        int color = BlockPreviewTint.color(
                query.level(),
                query.pos(),
                sample.sourceState(),
                sample.sourceSurface());
        return sprites.stream()
                .map(sprite -> Layer.full(sprite, color))
                .toList();
    }

    private static boolean requiresNativeConnections(
            ConnectionMethod method) {
        return switch (method) {
            case RUNTIME_BLEND, CTM, CTM_COMPACT,
                 HORIZONTAL, VERTICAL,
                 HORIZONTAL_VERTICAL,
                 VERTICAL_HORIZONTAL, OVERLAY,
                 OVERLAY_CTM -> true;
            case TOP, FIXED, NONE -> false;
            case AUTO -> throw new IllegalArgumentException(
                    "preview method must already be resolved");
        };
    }

    private static Optional<FusionNativeQuadProcessor>
    processor(
            PreviewQuery query,
            FaceSurface surface,
            BlockState state,
            ConnectionMethod method) {
        return FusionGeneratedStateSprites
                .sprites(
                        surface.sprite(),
                        method,
                        surface.overlayProfile())
                .flatMap(stateSprites ->
                        FusionNativeQuadProcessor.create(
                                surface.representativeQuad(),
                                surface.sprite(),
                                stateSprites,
                                state.getBlock(),
                                query.rules().rules(),
                                method,
                                Optional.empty(),
                                query.authoringConnectionBlocks()));
    }

    private static List<Direction> planarDirections(
            PreviewQuery query) {
        return OverlayDonorResolution
                .planarDirections(query.face());
    }

    private static List<PreviewSample> documentSamples(
            PreviewQuery query,
            List<Donor> donors) {
        ArrayList<PreviewSample> samples =
                new ArrayList<>(donors.size());
        for (Donor donor : donors) {
            NeighborConnections connections = processor(
                    query,
                    donor.surface(),
                    donor.state(),
                    donor.method()).flatMap(value -> value.connections(
                    query.level(),
                    query.pos(),
                    donor.state(),
                    0L)).orElseGet(NeighborConnections::none);
            if (connections.bits() != 0) {
                samples.add(new PreviewSample(
                        connections,
                        donor.state(),
                        donor.surface(),
                        donor.method()));
            }
        }
        return samples.isEmpty()
                ? passthrough(query)
                : List.copyOf(samples);
    }
}
