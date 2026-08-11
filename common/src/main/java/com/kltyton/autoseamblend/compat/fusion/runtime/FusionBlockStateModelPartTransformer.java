package com.kltyton.autoseamblend.compat.fusion.runtime;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：对 1.20.1 BakedModel 的所有 cull bucket 统一执行按需复制的 Quad 变换。
 *
 * English: Applies a copy-on-write quad transform to every cull bucket of a
 * 1.20.1 BakedModel.
 */
public final class FusionBlockStateModelPartTransformer {
    private FusionBlockStateModelPartTransformer() {}

    /**
     * 中文：包装原模型，使每个查询按需变换目标 bucket。
     *
     * English: Wraps the original model so every query transforms the requested bucket on demand.
     */
    public static BakedModel transform(
            BakedModel model,
            QuadTransformer transformer) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(transformer, "transformer");
        return new TransformedModel(model, transformer);
    }

    @FunctionalInterface
    public interface QuadTransformer {
        List<BakedQuad> transform(List<BakedQuad> source, Direction cullBucket);
    }

    private record TransformedModel(
            BakedModel delegate,
            QuadTransformer transformer)
            implements BakedModel {
        private TransformedModel {
            Objects.requireNonNull(delegate, "delegate");
            Objects.requireNonNull(transformer, "transformer");
        }

        @Override
        public List<BakedQuad> getQuads(
                BlockState state,
                Direction direction,
                RandomSource random) {
            return transformer.transform(
                    delegate.getQuads(state, direction, random),
                    direction);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return delegate.useAmbientOcclusion();
        }

        @Override
        public boolean isGui3d() {
            return delegate.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return delegate.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return delegate.isCustomRenderer();
        }

        @Override
        public net.minecraft.client.renderer.texture.TextureAtlasSprite getParticleIcon() {
            return delegate.getParticleIcon();
        }

        @Override
        public ItemTransforms getTransforms() {
            return delegate.getTransforms();
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }
}
