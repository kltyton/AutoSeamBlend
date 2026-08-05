package com.kltyton.autoseamblend.compat.fusion.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;

/**
 * 中文：对所有 cull bucket 和无 cull bucket 统一执行按需复制的 Quad 变换，并保留未变换
 * part 的对象身份。
 *
 * English: Applies a copy-on-write quad transform to every cull bucket and the uncull bucket while
 * preserving the identity of an untouched model part.
 */
public final class FusionBlockStateModelPartTransformer {
    private static final List<Direction> CULL_FACES = List.of(Direction.values());

    private FusionBlockStateModelPartTransformer() {}

    /**
     * 中文：只要所有 bucket 都原样返回，就直接复用原 part；首次变化后复制其余 bucket。
     * English: Reuses the original part while every bucket is unchanged; after the first change,
     * copies the remaining buckets exactly once.
     */
    public static BlockStateModelPart transform(
            BlockStateModelPart part,
            QuadTransformer transformer) {
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(transformer, "transformer");
        LinkedHashMap<Direction, List<BakedQuad>> quads = null;
        for (Direction cullFace : CULL_FACES) {
            List<BakedQuad> source = part.getQuads(cullFace);
            List<BakedQuad> transformed = transformer.transform(source, cullFace);
            if (transformed != source && quads == null) {
                quads = new LinkedHashMap<>();
                for (Direction previous : CULL_FACES) {
                    if (previous == cullFace) {
                        break;
                    }
                    quads.put(previous, part.getQuads(previous));
                }
            }
            if (quads != null) {
                quads.put(cullFace, transformed);
            }
        }

        List<BakedQuad> source = part.getQuads(null);
        List<BakedQuad> transformed = transformer.transform(source, null);
        if (transformed != source && quads == null) {
            quads = new LinkedHashMap<>();
            for (Direction cullFace : CULL_FACES) {
                quads.put(cullFace, part.getQuads(cullFace));
            }
        }
        if (quads == null) {
            return part;
        }
        quads.put(null, transformed);
        return new TransformedPart(part, quads);
    }

    @FunctionalInterface
    public interface QuadTransformer {
        List<BakedQuad> transform(List<BakedQuad> source, Direction cullBucket);
    }

    private record TransformedPart(
            BlockStateModelPart delegate,
            Map<Direction, List<BakedQuad>> quads)
            implements BlockStateModelPart {
        private TransformedPart {
            Objects.requireNonNull(delegate, "delegate");
            LinkedHashMap<Direction, List<BakedQuad>> copy = new LinkedHashMap<>();
            Objects.requireNonNull(quads, "quads").forEach((direction, values) ->
                    copy.put(direction, Objects.requireNonNull(values, "values")));
            quads = Collections.unmodifiableMap(copy);
        }

        @Override
        public List<BakedQuad> getQuads(Direction direction) {
            return quads.getOrDefault(direction, List.of());
        }

        @Override
        public boolean useAmbientOcclusion() {
            return delegate.useAmbientOcclusion();
        }

        @Override
        public Material.Baked particleMaterial() {
            return delegate.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return delegate.materialFlags();
        }
    }
}
