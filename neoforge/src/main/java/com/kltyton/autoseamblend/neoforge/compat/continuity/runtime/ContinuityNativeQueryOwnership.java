package com.kltyton.autoseamblend.neoforge.compat.continuity.runtime;

import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityGenerationHistory;
import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityProcessingDataProbe;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.query.AcceptedNativeDocument;
import com.kltyton.autoseamblend.engine.query.NativeQueryObservation;
import com.kltyton.autoseamblend.engine.routing.NativeQueryOwnershipProvider;
import com.kltyton.autoseamblend.runtime.publication.NativeGenerationParticipant;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import me.pepperbell.continuity.api.client.CachingPredicates;
import me.pepperbell.continuity.api.client.ProcessingDataProvider;
import me.pepperbell.continuity.client.processor.ProcessingPredicate;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.quad.MutableQuad;

/**
 * 中文：另一引擎适配器变换 Quad 前复用精确的已接受 NeoContinuity 文档身份；探针就是已接受 holder 谓词，每个 holder 代次都会重建，绝不在渲染线程解析 properties。
 *
 * English:
 * Exact accepted NeoContinuity document identity reused before another engine adapter transforms
 * a quad.
 *
 * <p>The probes are the accepted NeoContinuity holder predicates themselves. They are rebuilt for
 * every holder generation and never parse properties on the render thread.
 */
public enum ContinuityNativeQueryOwnership
        implements NativeQueryOwnershipProvider,
                NativeGenerationParticipant {
    INSTANCE;

    private static final ContinuityGenerationHistory<List<Probe>> PROBES =
            new ContinuityGenerationHistory<>();
    private static List<Probe> capturing =
            new ArrayList<>();
    private static long capturingGeneration = -1;
    private static int documentOrder;

    public static synchronized void beginGeneration(
            long generation) {
        if (generation <= 0) {
            throw new IllegalArgumentException(
                    "generation must be positive");
        }
        PROBES.begin(generation);
        capturing = new ArrayList<>();
        capturingGeneration = generation;
        documentOrder = 0;
    }

    static synchronized void register(
            CachingPredicates caching,
            ProcessingPredicate processing,
            ContinuityProcessorMetadata metadata) {
        Objects.requireNonNull(caching, "caching");
        Objects.requireNonNull(metadata, "metadata");
        if (capturingGeneration < 0) {
            throw new IllegalStateException(
                    "Continuity ownership capture has not begun");
        }
        capturing.add(new Probe(
                caching,
                processing,
                metadata.acceptedDocument(
                        documentOrder++)));
    }

    public static void stageGeneration() {
        long generation;
        synchronized (ContinuityNativeQueryOwnership.class) {
            if (capturingGeneration < 0) {
                throw new IllegalStateException(
                        "Continuity ownership capture has not begun");
            }
            generation = capturingGeneration;
            long activeGeneration = generation > 1
                    ? generation - 1
                    : generation;
            if (!PROBES.stage(
                    generation,
                    activeGeneration,
                    generation,
                    List.copyOf(capturing))) {
                throw new IllegalStateException(
                        "Continuity ownership generation is stale");
            }
            capturing = new ArrayList<>();
            capturingGeneration = -1;
        }
        ReloadPublication.nativeParticipantPrepared(
                generation);
    }

    @Override
    public String engineId() {
        return "continuity";
    }

    @Override
    public EngineFamily family() {
        return EngineFamily.MCPATCHER;
    }

    @Override
    public NativeQueryObservation observe(
            long generation,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            BakedQuad quad,
            TextureAtlasSprite sprite) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(quad, "quad");
        Objects.requireNonNull(sprite, "sprite");
        MutableQuad mutable =
                new MutableQuad().setFrom(quad);
        ContinuityProcessingDataProbe data = new ContinuityProcessingDataProbe();
        ArrayList<AcceptedNativeDocument> accepted =
                new ArrayList<>();
        List<Probe> probes = PROBES.value(generation);
        if (probes == null) {
            return NativeQueryObservation.unknown(
                    "CONTINUITY_HOLDER_GENERATION_UNAVAILABLE");
        }
        boolean unknown = false;
        try {
            for (Probe probe : probes) {
                if (probe.owns(
                        mutable,
                        sprite,
                        level,
                        pos,
                        state,
                        data)) {
                    if (probe.document().isPresent()) {
                        accepted.add(
                                probe.document()
                                        .orElseThrow());
                    } else {
                        unknown = true;
                    }
                }
                data.reset();
            }
            return new NativeQueryObservation(
                    accepted,
                    unknown
                            ? java.util.Optional.of(
                                    "CONTINUITY_ACCEPTED_HOLDER_IDENTITY_UNAVAILABLE")
                            : java.util.Optional.empty());
        } finally {
            data.reset();
        }
    }

    @Override
    public boolean prepared(
            long generation) {
        synchronized (ContinuityNativeQueryOwnership.class) {
            return PROBES.value(generation) != null;
        }
    }

    @Override
    public void abort(
            long generation) {
        synchronized (ContinuityNativeQueryOwnership.class) {
            PROBES.removeGeneration(generation);
            if (capturingGeneration == generation) {
                capturing = new ArrayList<>();
                capturingGeneration = -1;
            }
        }
    }

    private record Probe(
            CachingPredicates caching,
            ProcessingPredicate processing,
            java.util.Optional<AcceptedNativeDocument>
                    document) {
        private Probe {
            Objects.requireNonNull(caching, "caching");
            document = Objects.requireNonNull(
                    document,
                    "document");
        }

        private boolean owns(
                MutableQuad quad,
                TextureAtlasSprite sprite,
                BlockAndTintGetter level,
                BlockPos pos,
                BlockState state,
                ProcessingDataProvider data) {
            if (caching.affectsBlockStates()
                    && !caching.affectsBlockState(state)) {
                return false;
            }
            if (caching.affectsSprites()
                    && !caching.affectsSprite(sprite)) {
                return false;
            }
            return processing == null
                    || processing.shouldProcessQuad(
                            quad,
                            sprite,
                            level,
                            pos,
                            state,
                            state,
                            data);
        }
    }

}
