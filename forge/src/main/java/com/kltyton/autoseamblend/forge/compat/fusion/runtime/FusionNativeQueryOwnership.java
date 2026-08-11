package com.kltyton.autoseamblend.forge.compat.fusion.runtime;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.query.NativeQueryObservation;
import com.kltyton.autoseamblend.engine.routing.NativeQueryOwnershipProvider;
import java.util.ArrayList;
import java.util.Objects;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：Forge 适配器只负责把 BakedQuad/BakedModel 转成 common 表面探针。
 *
 * English: The Forge adapter only converts BakedQuad/BakedModel data into the common
 * surface probe contract.
 */
public enum FusionNativeQueryOwnership
        implements NativeQueryOwnershipProvider {
    INSTANCE;

    private final com.kltyton.autoseamblend.compat.fusion.runtime
            .FusionNativeQueryOwnership core =
                    new com.kltyton.autoseamblend.compat.fusion.runtime
                            .FusionNativeQueryOwnership();

    @Override
    public String engineId() {
        return core.engineId();
    }

    @Override
    public EngineFamily family() {
        return core.family();
    }

    @Override
    public NativeQueryObservation observe(
            long generation,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            BakedQuad quad,
            TextureAtlasSprite sprite) {
        Objects.requireNonNull(quad, "quad");
        return core.observe(
                generation,
                level,
                pos,
                state,
                quad.getDirection(),
                sprite);
    }

    synchronized void beginModelCapture(long generation) {
        core.beginModelCapture(
                generation,
                FusionAcceptedModifierDocumentCatalog.staged()
                        .orElseThrow(() -> new IllegalStateException(
                                "Fusion accepted-document candidate is unavailable")));
    }

    synchronized void captureModel(BlockState state, BakedModel model) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(model, "model");
        core.captureModel(
                state,
                (level, pos, capturedState, face, queriedSprite) -> matches(
                        model,
                        level,
                        pos,
                        capturedState,
                        face,
                        queriedSprite));
    }

    synchronized void endModelCapture() {
        core.endModelCapture();
        FusionAcceptedModifierDocumentCatalog.abortStaged();
    }

    synchronized void abortModelCapture(long generation) {
        core.abortModelCapture(generation);
        FusionAcceptedModifierDocumentCatalog.abortStaged();
    }

    private static boolean matches(
            BakedModel model,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction face,
            TextureAtlasSprite queriedSprite) {
        RandomSource random = RandomSource.create(0L);
        for (Direction cullFace : Direction.values()) {
            if (containsSurface(
                    model.getQuads(
                            state,
                            cullFace,
                            random),
                    face,
                    queriedSprite)) {
                return true;
            }
        }
        return containsSurface(
                model.getQuads(
                        state,
                        null,
                        random),
                face,
                queriedSprite);
    }

    private static boolean containsSurface(
            Iterable<BakedQuad> quads,
            Direction face,
            TextureAtlasSprite queriedSprite) {
        for (BakedQuad candidate : quads) {
            if (candidate.getDirection() == face
                    && candidate.getSprite().contents().name()
                            .equals(queriedSprite.contents().name())) {
                return true;
            }
        }
        return false;
    }
}
