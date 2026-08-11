package com.kltyton.autoseamblend.fabric.compat.continuity.preview;

import com.kltyton.autoseamblend.authoring.preview.BlockPreviewTint;
import com.kltyton.autoseamblend.authoring.preview.PreviewConnectionDonors;
import com.kltyton.autoseamblend.authoring.preview.PreviewFaceResult;
import com.kltyton.autoseamblend.authoring.preview.PreviewFaceResult.Layer;
import com.kltyton.autoseamblend.authoring.preview.PreviewProvider;
import com.kltyton.autoseamblend.authoring.preview.PreviewQuery;
import com.kltyton.autoseamblend.authoring.preview.PreviewSample;
import com.kltyton.autoseamblend.compat.continuity.authoring.materialize.ContinuityNativeSlotMaps;
import com.kltyton.autoseamblend.fabric.runtime.render.FabricQuadEmitting;
import com.kltyton.autoseamblend.compat.continuity.runtime.texture.ContinuityGeneratedStateSprites;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.fabric.compat.continuity.runtime.FabricContinuityAutoBlendProcessor;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolution;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolver.Donor;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.ContinuityGeneratedSpritePlan;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import me.pepperbell.continuity.api.client.ProcessingDataKey;
import me.pepperbell.continuity.api.client.QuadProcessor;
import me.pepperbell.continuity.client.processor.ConnectionPredicate;
import me.pepperbell.continuity.client.processor.DirectionMaps;
import me.pepperbell.continuity.client.processor.OrientationMode;
import me.pepperbell.continuity.client.processor.simple.CtmSpriteProvider;
import me.pepperbell.continuity.client.processor.simple.HorizontalSpriteProvider;
import me.pepperbell.continuity.client.processor.simple.HorizontalVerticalSpriteProvider;
import me.pepperbell.continuity.client.processor.simple.SpriteProvider;
import me.pepperbell.continuity.client.processor.simple.VerticalHorizontalSpriteProvider;
import me.pepperbell.continuity.client.processor.simple.VerticalSpriteProvider;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：采样运行时使用的同一 Continuity 方向与 CTM 邻接路径。
 * English: Samples the same Continuity orientation and CTM neighbor path used
 * by runtime.
 */
enum FabricContinuityPreviewProvider
        implements PreviewProvider {
    INSTANCE;

    @Override
    public String engineId() {
        return "continuity";
    }

    @Override
    public EngineFamily family() {
        return EngineFamily.MCPATCHER;
    }

    @Override
    public List<PreviewSample> sample(
            PreviewQuery query) {
        if (query.resolvedMethod().overlayCapable()) {
            List<Donor> donors =
                    query.usesDocumentConnectionBlocks()
                            ? PreviewConnectionDonors.resolve(
                                    query,
                                    Arrays.asList(
                                            DirectionMaps.getMap(
                                                            query.face())[0]))
                            : OverlayDonorResolution.resolveAll(
                                    query.level(),
                                    query.pos(),
                                    query.face(),
                                    query.state(),
                                    query.rules().rules(),
                                    query.surfaces(),
                                    family(),
                                    Arrays.asList(
                                            DirectionMaps.getMap(
                                                            query.face())[0]));
            if (donors.isEmpty()) {
                return List.of(new PreviewSample(
                        NeighborConnections.none(),
                        query.state(),
                        query.surface(),
                        query.resolvedMethod()));
            }
            ArrayList<PreviewSample> samples =
                    new ArrayList<>(donors.size());
            for (Donor selected : donors) {
                PreviewSample sampled = sample(
                        query,
                        selected.state(),
                        selected.surface(),
                        selected.method());
                if (sampled.connections().bits() != 0) {
                    samples.add(sampled);
                }
            }
            return samples.isEmpty()
                    ? List.of(new PreviewSample(
                            NeighborConnections.none(),
                            query.state(),
                            query.surface(),
                            query.resolvedMethod()))
                    : List.copyOf(samples);
        }
        return List.of(sample(
                query,
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
        if (!ContinuityGeneratedSpritePlan
                .requiresGeneratedSprites(
                        query.resolvedMethod())) {
            return Optional.of(
                    PreviewFaceResult.full(
                            query.surface().sprite(),
                            BlockPreviewTint.color(
                                    query.level(),
                                    query.pos(),
                                    query.state(),
                                    query.surface())));
        }
        PreviewProcessingContext context =
                new PreviewProcessingContext();
        if (query.resolvedMethod().overlayCapable()) {
            return exactOverlay(query, samples, context);
        }
        PreviewSample sample = samples.get(0);
        Optional<TextureAtlasSprite[]> generated =
                ContinuityGeneratedStateSprites
                        .sprites(
                                sample.sourceSurface()
                                        .sprite(),
                                sample.renderMethod(),
                                sample.sourceSurface()
                                        .overlayProfile());
        if (generated.isEmpty()) {
            return Optional.empty();
        }
        if (sample.renderMethod()
                == ConnectionMethod.CTM_COMPACT) {
            return Optional.of(
                    compactFace(
                            generated.orElseThrow(),
                            sample.connections().bits(),
                            BlockPreviewTint.color(
                                    query.level(),
                                    query.pos(),
                                    sample.sourceState(),
                                    sample.sourceSurface())));
        }
        TextureAtlasSprite selected =
                selectSimple(
                        query,
                        sample.sourceState(),
                        sample.sourceSurface().sprite(),
                        sample.renderMethod(),
                        generated.orElseThrow(),
                        context);
        return Optional.of(
                PreviewFaceResult.full(
                        selected,
                        BlockPreviewTint.color(
                                query.level(),
                                query.pos(),
                                sample.sourceState(),
                                sample.sourceSurface())));
    }

    private static Optional<PreviewFaceResult> exactOverlay(
            PreviewQuery query,
            List<PreviewSample> samples,
            PreviewProcessingContext context) {
        ArrayList<Layer> layers = new ArrayList<>();
        layers.add(Layer.full(
                query.surface().sprite(),
                BlockPreviewTint.color(
                        query.level(),
                        query.pos(),
                        query.state(),
                        query.surface())));
        for (PreviewSample sample : samples) {
            Optional<TextureAtlasSprite[]> generated =
                    ContinuityGeneratedStateSprites
                            .sprites(
                                    sample.sourceSurface()
                                            .sprite(),
                                    sample.renderMethod(),
                                    sample.sourceSurface()
                                            .overlayProfile());
            if (generated.isEmpty()) {
                return Optional.empty();
            }
            TextureAtlasSprite[] sprites =
                    generated.orElseThrow();
            if (sample.renderMethod()
                    == ConnectionMethod.OVERLAY_CTM) {
                layers.add(Layer.full(
                        selectSimple(
                                query,
                                sample.sourceState(),
                                sample.sourceSurface().sprite(),
                                sample.renderMethod(),
                                sprites,
                                context),
                        BlockPreviewTint.color(
                                query.level(),
                                query.pos(),
                                sample.sourceState(),
                                sample.sourceSurface())));
                continue;
            }
            for (int slot :
                    FabricContinuityAutoBlendProcessor
                            .previewOverlaySlots(
                                    query,
                                    sample,
                                    context)) {
                if (slot >= 0
                        && slot < sprites.length) {
                    layers.add(Layer.full(
                            sprites[slot],
                            BlockPreviewTint.color(
                                    query.level(),
                                    query.pos(),
                                    sample.sourceState(),
                                    sample.sourceSurface())));
                }
            }
        }
        return Optional.of(
                new PreviewFaceResult(layers));
    }

    private static TextureAtlasSprite selectSimple(
            PreviewQuery query,
            BlockState sourceState,
            TextureAtlasSprite sourceSprite,
            ConnectionMethod method,
            TextureAtlasSprite[] sprites,
            PreviewProcessingContext context) {
        ConnectionPredicate predicate =
                connectionPredicate(query);
        SpriteProvider provider = switch (method) {
            case CTM, OVERLAY_CTM ->
                    new CtmSpriteProvider(
                            sprites,
                            predicate,
                            false,
                            OrientationMode.TEXTURE);
            case HORIZONTAL ->
                    new HorizontalSpriteProvider(
                            sprites,
                            predicate,
                            false,
                            OrientationMode.TEXTURE);
            case VERTICAL ->
                    new VerticalSpriteProvider(
                            sprites,
                            predicate,
                            false,
                            OrientationMode.TEXTURE);
            case HORIZONTAL_VERTICAL ->
                    new HorizontalVerticalSpriteProvider(
                            sprites,
                            predicate,
                            false,
                            OrientationMode.TEXTURE);
            case VERTICAL_HORIZONTAL ->
                    new VerticalHorizontalSpriteProvider(
                            sprites,
                            predicate,
                            false,
                            OrientationMode.TEXTURE);
            default -> throw new IllegalArgumentException(
                    method
                            + " does not use a simple Continuity selector");
        };
        MutableQuadView quad =
                FabricQuadEmitting.fromBakedQuad(
                        RendererAccess.INSTANCE
                                .getRenderer()
                                .meshBuilder()
                                .getEmitter(),
                        query.surface()
                                .representativeQuad());
        return provider.getSprite(
                quad,
                sourceSprite,
                query.level(),
                query.state(),
                query.state(),
                query.pos(),
                () -> RandomSource.create(0L),
                context);
    }

    private static PreviewFaceResult compactFace(
            TextureAtlasSprite[] sprites,
            int connections,
            int color) {
        int[] slots =
                ContinuityNativeSlotMaps.compactSlots(
                        connections);
        float[][] regions = {
            {0.0F, 0.0F, 0.5F, 0.5F},
            {0.5F, 0.0F, 1.0F, 0.5F},
            {0.5F, 0.5F, 1.0F, 1.0F},
            {0.0F, 0.5F, 0.5F, 1.0F}
        };
        ArrayList<Layer> layers =
                new ArrayList<>(4);
        for (int quadrant = 0;
                quadrant < slots.length;
                quadrant++) {
            int slot = slots[quadrant];
            if (slot < 0
                    || slot >= sprites.length) {
                throw new IllegalStateException(
                        "Continuity compact slot is outside generated domain");
            }
            float[] region = regions[quadrant];
            layers.add(new Layer(
                    sprites[slot],
                    region[0],
                    region[1],
                    region[2],
                    region[3],
                    region[0],
                    region[1],
                    region[2],
                    region[3],
                    color));
        }
        return new PreviewFaceResult(layers);
    }

    private static ConnectionPredicate connectionPredicate(
            PreviewQuery query) {
        return (world, origin, originAppearance,
                originPos, other,
                otherAppearance, otherPos,
                face, quadSprite) ->
                query.connects(origin, other);
    }

    private static PreviewSample sample(
            PreviewQuery query,
            BlockState sourceState,
            FaceSurface sourceSurface,
            ConnectionMethod renderMethod) {
        MutableQuadView quad =
                FabricQuadEmitting.fromBakedQuad(
                        RendererAccess.INSTANCE
                                .getRenderer()
                                .meshBuilder()
                                .getEmitter(),
                        query.surface()
                                .representativeQuad());
        ConnectionPredicate predicate =
                connectionPredicate(query);
        int bits = CtmSpriteProvider.getConnections(
                DirectionMaps.getDirections(
                        OrientationMode.TEXTURE,
                        quad,
                        query.state()),
                predicate,
                false,
                new BlockPos.MutableBlockPos(),
                query.level(),
                query.state(),
                sourceState,
                query.pos(),
                query.face(),
                sourceSurface.sprite());
        return new PreviewSample(
                NeighborConnections.fromBits(bits),
                sourceState,
                sourceSurface,
                renderMethod);
    }

    private static final class PreviewProcessingContext
            implements QuadProcessor.ProcessingContext {
        private final IdentityHashMap<ProcessingDataKey<?>, Object> data =
                new IdentityHashMap<>();
        private final List<Consumer<QuadEmitter>> emitterConsumers =
                new ArrayList<>();
        private final List<Mesh> meshes =
                new ArrayList<>();
        private final QuadEmitter extraQuadEmitter =
                RendererAccess.INSTANCE
                        .getRenderer()
                        .meshBuilder()
                        .getEmitter();

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getData(
                ProcessingDataKey<T> key) {
            return (T) data.computeIfAbsent(
                    key,
                    ignored -> key.getValueSupplier().get());
        }

        @Override
        public QuadEmitter getExtraQuadEmitter() {
            return extraQuadEmitter;
        }

        @Override
        public void addEmitterConsumer(
                Consumer<QuadEmitter> consumer) {
            emitterConsumers.add(consumer);
        }

        @Override
        public void addMesh(Mesh mesh) {
            meshes.add(mesh);
        }

        @Override
        public void markHasExtraQuads() {
            for (Consumer<QuadEmitter> consumer
                    : emitterConsumers) {
                consumer.accept(extraQuadEmitter);
            }
            emitterConsumers.clear();
        }
    }
}
