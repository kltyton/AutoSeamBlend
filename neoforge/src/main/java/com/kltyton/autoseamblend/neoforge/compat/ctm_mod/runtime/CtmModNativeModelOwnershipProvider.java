package com.kltyton.autoseamblend.neoforge.compat.ctm_mod.runtime;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.query.NativeQueryObservation;
import com.kltyton.autoseamblend.engine.routing.NativeModelOwnershipProvider;
import com.kltyton.autoseamblend.engine.routing.NativeQueryOwnershipProvider;
import com.kltyton.autoseamblend.neoforge.compat.ctm_mod.mixin.AbstractConnectedTextureBlockStateModelAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import team.chisel.ctm.client.model.AbstractCTMBakedModel;

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
    public boolean owns(BakedModel model) {
        return model instanceof AbstractCTMBakedModel;
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
            BakedModel model) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(model, "model");
        if (model instanceof AbstractCTMBakedModel) {
            AbstractConnectedTextureBlockStateModelAccessor accessor =
                    (AbstractConnectedTextureBlockStateModelAccessor) model;
            Set<BakedQuad> baseQuads =
                    Collections.newSetFromMap(
                            new IdentityHashMap<>());
            baseQuads.addAll(
                    accessor.autoseamblend$genQuads());
            baseQuads.addAll(
                    accessor.autoseamblend$faceQuads()
                            .values());
            capturing.put(
                    state,
                    new NativePartProbe(
                            model,
                            baseQuads));
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
                        quad.getDirection(),
                        sprite)
                : NativeQueryObservation.noMatch();
    }

    private interface QueryProbe {
        NativeQueryObservation observe(
                BlockAndTintGetter level,
                BlockPos pos,
                BlockState state,
                Direction face,
                TextureAtlasSprite sprite);
    }

    private record NativePartProbe(
            BakedModel model,
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
            RandomSource random = RandomSource.create(0L);
            for (Direction cullFace : Direction.values()) {
                if (containsOwnedQuad(
                        model.getQuads(
                                state,
                                cullFace,
                                random),
                        face,
                        sprite)) {
                    return NativeQueryObservation.unknown(
                            "CTM_LIB_NATIVE_DOCUMENT_IDENTITY_UNAVAILABLE");
                }
            }
            if (containsOwnedQuad(
                    model.getQuads(
                            state,
                            null,
                            random),
                    face,
                    sprite)) {
                return NativeQueryObservation.unknown(
                        "CTM_LIB_NATIVE_DOCUMENT_IDENTITY_UNAVAILABLE");
            }
            return NativeQueryObservation.noMatch();
        }

        private boolean containsOwnedQuad(
                List<BakedQuad> quads,
                Direction face,
                TextureAtlasSprite queriedSprite) {
            for (BakedQuad quad : quads) {
                TextureAtlasSprite nativeSprite =
                        quad.getSprite();
                if (quad.getDirection() == face
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
                                                .getParticleIcon()
                                                .contents()
                                                .name()))) {
                    return true;
                }
            }
            return false;
        }
    }
}
