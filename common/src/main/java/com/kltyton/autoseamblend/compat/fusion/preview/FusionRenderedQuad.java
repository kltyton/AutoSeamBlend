package com.kltyton.autoseamblend.compat.fusion.preview;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：真实 Fusion 处理后冻结的项目 Quad。
 * <p>
 * English: Project-owned quad frozen after real Fusion processing. The value object is independent
 * of Fusion, Minecraft, and either Loader.
 */
public record FusionRenderedQuad(
        int order,
        boolean overlay,
        String spriteId,
        Material material,
        String nominalFace,
        String lightFace,
        Optional<String> cullFace,
        boolean frontFacing,
        int tintIndex,
        boolean diffuseShade,
        boolean animated,
        List<Vertex> vertices) {
    public FusionRenderedQuad {
        if (order < 0 || spriteId == null || spriteId.isBlank()) {
            throw new IllegalArgumentException("invalid Fusion preview quad identity");
        }
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(nominalFace, "nominalFace");
        Objects.requireNonNull(lightFace, "lightFace");
        cullFace = Objects.requireNonNull(cullFace, "cullFace");
        vertices = List.copyOf(Objects.requireNonNull(vertices, "vertices"));
        if (vertices.size() != 4) {
            throw new IllegalArgumentException("Fusion preview quad requires four vertices");
        }
    }

    public FusionRenderedQuad withOrder(int nextOrder, boolean nextOverlay) {
        return new FusionRenderedQuad(
                nextOrder,
                nextOverlay,
                spriteId,
                material,
                nominalFace,
                lightFace,
                cullFace,
                frontFacing,
                tintIndex,
                diffuseShade,
                animated,
                vertices);
    }

    public record Vertex(
            float x,
            float y,
            float z,
            float localU,
            float localV,
            int argb,
            int lightmap,
            Optional<Normal> normal) {
        public Vertex {
            if (!Float.isFinite(x)
                    || !Float.isFinite(y)
                    || !Float.isFinite(z)
                    || !Float.isFinite(localU)
                    || !Float.isFinite(localV)) {
                throw new IllegalArgumentException("Fusion preview vertex must be finite");
            }
            normal = Objects.requireNonNull(normal, "normal");
        }
    }

    public record Normal(float x, float y, float z) {
        public Normal {
            if (!Float.isFinite(x)
                    || !Float.isFinite(y)
                    || !Float.isFinite(z)) {
                throw new IllegalArgumentException("Fusion preview normal must be finite");
            }
        }
    }

    public record Material(
            String atlas,
            Optional<String> chunkLayer,
            Optional<String> itemRenderType,
            boolean emissive,
            String ambientOcclusion,
            String foilType,
            String shadeMode,
            int tag) {
        public Material {
            Objects.requireNonNull(atlas, "atlas");
            chunkLayer = Objects.requireNonNull(chunkLayer, "chunkLayer");
            itemRenderType = Objects.requireNonNull(itemRenderType, "itemRenderType");
            Objects.requireNonNull(ambientOcclusion, "ambientOcclusion");
            Objects.requireNonNull(foilType, "foilType");
            Objects.requireNonNull(shadeMode, "shadeMode");
        }
    }
}
