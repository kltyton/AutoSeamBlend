package com.kltyton.autoseamblend.neoforge.compat.fusion.runtime;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.query.NativeQueryObservation;
import com.kltyton.autoseamblend.engine.routing.NativeQueryOwnershipProvider;
import java.util.ArrayList;
import java.util.Objects;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：NeoForge 适配器只负责把 BakedQuad/BlockStateModel 转成 common 表面探针。
 *
 * English: The NeoForge adapter only converts BakedQuad/BlockStateModel data into the common
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
                quad.direction(),
                sprite);
    }

    synchronized void beginModelCapture(long generation) {
        core.beginModelCapture(
                generation,
                FusionAcceptedModifierDocumentCatalog.staged()
                        .orElseThrow(() -> new IllegalStateException(
                                "Fusion accepted-document candidate is unavailable")));
    }

    synchronized void captureModel(BlockState state, BlockStateModel model) {
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
            BlockStateModel model,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction face,
            TextureAtlasSprite queriedSprite) {
        ArrayList<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(level, pos, state, RandomSource.create(0L), parts);
        for (BlockStateModelPart part : parts) {
            for (Direction cullFace : Direction.values()) {
                if (containsSurface(part.getQuads(cullFace), face, queriedSprite)) {
                    return true;
                }
            }
            if (containsSurface(part.getQuads(null), face, queriedSprite)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSurface(
            Iterable<BakedQuad> quads,
            Direction face,
            TextureAtlasSprite queriedSprite) {
        for (BakedQuad candidate : quads) {
            if (candidate.direction() == face
                    && candidate.materialInfo().sprite().contents().name()
                            .equals(queriedSprite.contents().name())) {
                return true;
            }
        }
        return false;
    }
}
