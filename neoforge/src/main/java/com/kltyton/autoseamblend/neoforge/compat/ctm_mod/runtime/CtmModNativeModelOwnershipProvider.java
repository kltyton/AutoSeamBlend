package com.kltyton.autoseamblend.neoforge.compat.ctm_mod.runtime;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.query.NativeQueryObservation;
import com.kltyton.autoseamblend.neoforge.compat.ctm_mod.mixin.AbstractConnectedTextureBlockStateModelAccessor;
import com.kltyton.autoseamblend.engine.routing.NativeQueryOwnershipProvider;
import com.kltyton.autoseamblend.engine.routing.NativeModelOwnershipProvider;
import io.github.chiselteam.ctm.client.AbstractConnectedTextureBlockStateModel;
import io.github.chiselteam.ctm.client.baked.EldritchBlockStateModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：捕获 CTM Lib 已接受原生模型，并向该模型查询一个世界请求；输出 Quad 的材质精灵
 * 不能证明产生模型的原生文档身份。
 *
 * English:
 * Captures CTM Lib's accepted native model and asks that model about one world query.
 *
 * <p>Query ownership requires the current native model part to contain an actual non-base CTM
 * quad on the rendered face. Its output material sprite cannot prove the native document identity
 * that produced the model.
 */
public enum CtmModNativeModelOwnershipProvider
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
        return "ctm";
    }

    @Override
    public EngineFamily family() {
        return EngineFamily.CTM_MOD;
    }

    @Override
    public boolean owns(BlockStateModel model) {
        return model
                        instanceof AbstractConnectedTextureBlockStateModel<?>
                || model instanceof EldritchBlockStateModel;
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
        if (model instanceof AbstractConnectedTextureBlockStateModel<?>) {
            AbstractConnectedTextureBlockStateModelAccessor accessor =
                    (AbstractConnectedTextureBlockStateModelAccessor) model;
            Set<BakedQuad> baseQuads =
                    Collections.newSetFromMap(
                            new IdentityHashMap<>());
            accessor.autoseamblend$baseQuads()
                    .values()
                    .forEach(quads ->
                            Collections.addAll(
                                    baseQuads,
                                    quads));
            capturing.put(
                    state,
                    new NativePartProbe(
                            model,
                            baseQuads));
        } else {
            capturing.put(
                    state,
                    QueryProbe.ELDRITCH);
        }
    }

    @Override
    public synchronized void endCapture() {
        if (capturingGeneration < 0) {
            throw new IllegalStateException(
                    "CTM Lib ownership capture has not begun");
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
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(quad, "quad");
        Objects.requireNonNull(sprite, "sprite");
        Map<BlockState, QueryProbe> probes =
                generations.get(generation);
        if (probes == null) {
            return NativeQueryObservation.unknown(
                    "CTM_LIB_MODEL_CAPTURE_GENERATION_UNAVAILABLE");
        }
        QueryProbe probe = probes.get(state);
        return probe != null
                ? probe.observe(
                        level,
                        pos,
                        state,
                        quad.direction(),
                        sprite)
                : NativeQueryObservation.noMatch();
    }

    private interface QueryProbe {
        QueryProbe ELDRITCH =
                (level, pos, state, face, sprite) ->
                        NativeQueryObservation.unknown(
                                "CTM_LIB_NATIVE_DOCUMENT_IDENTITY_UNAVAILABLE");

        NativeQueryObservation observe(
                BlockAndTintGetter level,
                BlockPos pos,
                BlockState state,
                Direction face,
                TextureAtlasSprite sprite);
    }

    private record NativePartProbe(
            BlockStateModel model,
            Set<BakedQuad> baseQuads)
            implements QueryProbe {
        private NativePartProbe {
            Objects.requireNonNull(model, "model");
            Set<BakedQuad> identityCopy =
                    Collections.newSetFromMap(
                            new IdentityHashMap<>());
            identityCopy.addAll(
                    Objects.requireNonNull(
                            baseQuads,
                            "baseQuads"));
            baseQuads = Collections.unmodifiableSet(
                    identityCopy);
        }

        @Override
        public NativeQueryObservation observe(
                BlockAndTintGetter level,
                BlockPos pos,
                BlockState state,
                Direction face,
                TextureAtlasSprite sprite) {
            ArrayList<BlockStateModelPart> parts =
                    new ArrayList<>();
            model.collectParts(
                    level,
                    pos,
                    state,
                    RandomSource.create(0L),
                    parts);
            for (BlockStateModelPart part : parts) {
                for (Direction cullFace : Direction.values()) {
                    if (containsOwnedQuad(
                            part.getQuads(cullFace),
                            face,
                            sprite)) {
                        return NativeQueryObservation.unknown(
                                "CTM_LIB_NATIVE_DOCUMENT_IDENTITY_UNAVAILABLE");
                    }
                }
                if (containsOwnedQuad(
                        part.getQuads(null),
                        face,
                        sprite)) {
                    return NativeQueryObservation.unknown(
                            "CTM_LIB_NATIVE_DOCUMENT_IDENTITY_UNAVAILABLE");
                }
            }
            return NativeQueryObservation.noMatch();
        }

        private boolean containsOwnedQuad(
                Iterable<BakedQuad> quads,
                Direction face,
                TextureAtlasSprite queriedSprite) {
            for (BakedQuad quad : quads) {
                TextureAtlasSprite nativeSprite =
                        quad.materialInfo().sprite();
                if (quad.direction() == face
                        && !baseQuads.contains(quad)
                        && !nativeSprite
                                .contents()
                                .name()
                                .equals(MissingTextureAtlasSprite
                                        .getLocation())
                        && (nativeSprite.contents()
                                        .name()
                                        .equals(queriedSprite
                                                .contents()
                                                .name())
                                || queriedSprite.contents()
                                        .name()
                                        .equals(model
                                                .particleMaterial()
                                                .sprite()
                                                .contents()
                                                .name()))) {
                    return true;
                }
            }
            return false;
        }
    }
}
