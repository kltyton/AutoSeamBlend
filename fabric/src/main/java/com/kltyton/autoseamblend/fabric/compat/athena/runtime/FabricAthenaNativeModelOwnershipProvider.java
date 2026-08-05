package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.query.NativeQueryObservation;
import com.kltyton.autoseamblend.engine.routing.NativeModelOwnershipProvider;
import com.kltyton.autoseamblend.engine.routing.NativeQueryOwnershipProvider;
import earth.terrarium.athena.api.client.fabric.AthenaBakedModel;
import earth.terrarium.athena.api.client.models.AthenaBlockModel;
import earth.terrarium.athena.impl.client.models.ConnectedBlockModel;
import com.kltyton.autoseamblend.fabric.compat.athena.mixin.AthenaBakedModelAccessor;
import com.kltyton.autoseamblend.fabric.compat.athena.mixin.ConnectedBlockModelAccessor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiPredicate;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：捕获 Athena 已接受模型并查询当前面。
 * English: Captures Athena's accepted model and queries the current face.
 */
public enum FabricAthenaNativeModelOwnershipProvider
        implements NativeModelOwnershipProvider,
                NativeQueryOwnershipProvider {
    INSTANCE;

    private final ConcurrentMap<Long, Map<BlockState, QueryProbe>>
            generations = new ConcurrentHashMap<>();
    private Map<BlockState, QueryProbe> capturing =
            new LinkedHashMap<>();
    private long capturingGeneration = -1;

    @Override
    public String engineId() {
        return "athena";
    }

    @Override
    public EngineFamily family() {
        return EngineFamily.ATHENA;
    }

    @Override
    public boolean owns(BlockStateModel model) {
        return model instanceof AthenaBakedModel;
    }

    @Override
    public synchronized void beginCapture(
            long generation) {
        if (generation <= 0) {
            throw new IllegalArgumentException(
                    "generation must be positive");
        }
        generations.keySet().removeIf(value ->
                value < generation - 1);
        capturing = new LinkedHashMap<>();
        capturingGeneration = generation;
    }

    @Override
    public synchronized void capture(
            BlockState state,
            BlockStateModel model) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(model, "model");
        if (!(model instanceof AthenaBakedModel athena)) {
            return;
        }
        AthenaBlockModel nativeModel =
                ((AthenaBakedModelAccessor) (Object) athena)
                        .autoseamblend$getModel();
        BiPredicate<BlockState, BlockState> predicate =
                null;
        if (nativeModel
                instanceof ConnectedBlockModel connected) {
            predicate =
                    ((ConnectedBlockModelAccessor) (Object) connected)
                            .autoseamblend$getConnectTo();
        }
        capturing.put(
                state,
                new QueryProbe(
                        predicate,
                        athena));
    }

    @Override
    public synchronized void endCapture() {
        if (capturingGeneration < 0) {
            throw new IllegalStateException(
                    "Athena ownership capture has not begun");
        }
        generations.put(
                capturingGeneration,
                Map.copyOf(capturing));
        capturing = new LinkedHashMap<>();
        capturingGeneration = -1;
    }

    @Override
    public synchronized void abortCapture(
            long generation) {
        generations.remove(generation);
        if (capturingGeneration == generation) {
            capturing = new LinkedHashMap<>();
            capturingGeneration = -1;
        }
    }

    @Override
    public NativeQueryObservation observe(
            long generation,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            BakedQuad quad,
            TextureAtlasSprite sprite) {
        Map<BlockState, QueryProbe> probes =
                generations.get(generation);
        if (probes == null) {
            return NativeQueryObservation.unknown(
                    "ATHENA_MODEL_CAPTURE_GENERATION_UNAVAILABLE");
        }
        QueryProbe probe = probes.get(state);
        if (probe == null) {
            return NativeQueryObservation.noMatch();
        }
        boolean nativeOwns = probe.predicate() == null
                || probe.predicate().test(
                        state,
                        state);
        return nativeOwns
                ? NativeQueryObservation.exact(
                        java.util.List.of())
                : NativeQueryObservation.noMatch();
    }

    private record QueryProbe(
            BiPredicate<BlockState, BlockState> predicate,
            AthenaBakedModel model) {}
}
