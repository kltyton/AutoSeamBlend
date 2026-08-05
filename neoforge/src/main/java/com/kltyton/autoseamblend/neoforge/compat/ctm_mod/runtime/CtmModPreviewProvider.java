package com.kltyton.autoseamblend.neoforge.compat.ctm_mod.runtime;

import com.kltyton.autoseamblend.compat.ctm_mod.runtime.CtmModOverlayStateSampler;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.compat.ctm_mod.runtime.pane.CtmModPanePolicy;
import com.kltyton.autoseamblend.authoring.preview.PreviewConnectionDonors;
import com.kltyton.autoseamblend.authoring.preview.PreviewFaceResult;
import com.kltyton.autoseamblend.authoring.preview.PreviewFaceResult.Layer;
import com.kltyton.autoseamblend.authoring.preview.PreviewProvider;
import com.kltyton.autoseamblend.authoring.preview.PreviewQuery;
import com.kltyton.autoseamblend.authoring.preview.PreviewSample;
import com.kltyton.autoseamblend.authoring.preview.BlockPreviewTint;
import com.kltyton.autoseamblend.compat.ctm_mod.runtime.texture.CtmModGeneratedStateSprites;
import com.kltyton.autoseamblend.compat.ctm_mod.runtime.texture.CtmModMethodStateDomain;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolution;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolver.Donor;
import com.kltyton.autoseamblend.runtime.render.BakedQuadTextureBasis;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** 中文：为当前预览查询采样 CTM Lib 原生标准键。 / English: Samples CTM Lib's native standard key for the current preview query. */
public enum CtmModPreviewProvider
        implements PreviewProvider {
    INSTANCE;

    @Override
    public String engineId() {
        return "ctm";
    }

    @Override
    public EngineFamily family() {
        return EngineFamily.CTM_MOD;
    }

    @Override
    public List<PreviewSample> sample(
            PreviewQuery query) {
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
            for (Donor donor : donors) {
                NeighborConnections connections =
                        sample(query, donor);
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
        return List.of(new PreviewSample(
                sample(
                        query,
                        query.state(),
                        query.surface().sprite(),
                        query.resolvedMethod(),
                        false),
                query.state(),
                query.surface(),
                query.resolvedMethod()));
    }

    @Override
    public Optional<PreviewFaceResult> exactFace(
            PreviewQuery query,
            List<PreviewSample> samples) {
        if (samples.isEmpty()
                || query.resolvedMethod() == ConnectionMethod.TOP) {
            return Optional.empty();
        }
        if (CtmModPanePolicy.preservesTerminator(
                query.state(),
                query.resolvedMethod(),
                query.face(),
                null)) {
            return Optional.of(PreviewFaceResult.full(
                    query.surface().sprite(),
                    BlockPreviewTint.color(
                            query.level(),
                            query.pos(),
                            query.state(),
                            query.surface())));
        }
        if (!requiresGeneratedResult(query.resolvedMethod())) {
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
            return selectedSprites(sample).stream()
                    .findFirst()
                    .map(sprite ->
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
            List<TextureAtlasSprite> selected =
                    selectedSprites(sample);
            if (selected.isEmpty()) {
                return Optional.empty();
            }
            int tint = BlockPreviewTint.color(
                    query.level(),
                    query.pos(),
                    sample.sourceState(),
                    sample.sourceSurface());
            for (TextureAtlasSprite sprite : selected) {
                layers.add(Layer.full(sprite, tint));
            }
        }
        return Optional.of(new PreviewFaceResult(layers));
    }

    private static List<TextureAtlasSprite> selectedSprites(
            PreviewSample sample) {
        return CtmModGeneratedStateSprites
                .sprites(
                        sample.sourceSurface().sprite(),
                        sample.renderMethod(),
                        sample.sourceSurface()
                                .overlayProfile())
                .map(sprites -> {
                    ArrayList<TextureAtlasSprite> selected =
                            new ArrayList<>();
                    for (int slot : CtmModMethodStateDomain.selectedSlots(
                            sample.renderMethod(),
                            sample.connections())) {
                        if (slot >= 0
                                && slot < sprites.length
                                && sprites[slot] != null) {
                            selected.add(sprites[slot]);
                        }
                    }
                    return List.copyOf(selected);
                })
                .orElseGet(List::of);
    }

    private static boolean requiresGeneratedResult(
            ConnectionMethod method) {
        return CtmModMethodStateDomain.requiresGeneratedResult(method);
    }

    private static NeighborConnections sample(
            PreviewQuery query,
            BlockState state,
            net.minecraft.client.renderer.texture.TextureAtlasSprite
                    sprite,
            ConnectionMethod method,
            boolean overlay) {
        CtmModNativeConnectionSampler sampler =
                new CtmModNativeConnectionSampler(
                        sprite,
                        state.getBlock(),
                        query.rules().rules(),
                        query.authoringConnectionBlocks(),
                        overlay,
                        (level,
                                appearancePos,
                                appearanceFace,
                                appearanceState,
                                otherState,
                                otherPos) ->
                                appearanceState.getAppearance(
                                        level,
                                        appearancePos,
                                        appearanceFace,
                                        otherState,
                                        otherPos));
        if (CtmModMethodStateDomain.preservesIndependentCorners(
                method)) {
            return sampler.sampleIndependent(
                    query.level(),
                    query.pos(),
                    state,
                    query.face(),
                    BakedQuadTextureBasis.resolve(
                            query.surface()
                                    .representativeQuad()));
        }
        return sampler.sample(
                        query.level(),
                        query.pos(),
                        state,
                        query.face(),
                        BakedQuadTextureBasis.resolve(
                                query.surface()
                                        .representativeQuad()),
                        RandomSource.create(0L));
    }

    private static NeighborConnections sample(
            PreviewQuery query,
            Donor donor) {
        if (donor.method() == ConnectionMethod.RUNTIME_BLEND
                || donor.method() == ConnectionMethod.OVERLAY) {
            return CtmModOverlayStateSampler.sample(
                    query,
                    donor);
        }
        return sample(
                query,
                donor.state(),
                donor.surface().sprite(),
                donor.method(),
                true);
    }

    private static List<PreviewSample>
            passthrough(PreviewQuery query) {
        return List.of(new PreviewSample(
                NeighborConnections.none(),
                query.state(),
                query.surface(),
                query.resolvedMethod()));
    }

    private static List<Direction> planarDirections(
            PreviewQuery query) {
        return CtmModOverlayStateSampler.planarDirections(
                query.face());
    }

    private static List<PreviewSample> documentSamples(
            PreviewQuery query,
            List<Donor> donors) {
        ArrayList<PreviewSample> samples =
                new ArrayList<>(donors.size());
        for (Donor donor : donors) {
            NeighborConnections connections =
                    sample(query, donor);
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
