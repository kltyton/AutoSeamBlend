package com.kltyton.autoseamblend.compat.continuity.preview;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：真实 Continuity 处理后冻结的项目自有渲染 Quad；不持有 Minecraft 或引擎对象。
 * English: Project-owned rendered quad frozen after real Continuity processing; it retains no Minecraft or engine objects.
 */
public record ContinuityRenderedQuad(
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
    public ContinuityRenderedQuad {
        if (order < 0) {
            throw new IllegalArgumentException("render order must be non-negative");
        }
        spriteId = text(spriteId, "spriteId");
        Objects.requireNonNull(material, "material");
        nominalFace = text(nominalFace, "nominalFace");
        lightFace = text(lightFace, "lightFace");
        cullFace = Objects.requireNonNull(cullFace, "cullFace")
                .map(value -> text(value, "cullFace"));
        vertices = List.copyOf(Objects.requireNonNull(vertices, "vertices"));
        if (vertices.size() != 4) {
            throw new IllegalArgumentException("rendered quad requires exactly four vertices");
        }
    }

    public ContinuityRenderedQuad withOrder(int nextOrder, boolean nextOverlay) {
        return new ContinuityRenderedQuad(
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
                throw new IllegalArgumentException("rendered vertex values must be finite");
            }
            normal = Objects.requireNonNull(normal, "normal");
        }
    }

    public record Normal(float x, float y, float z) {
        public Normal {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                throw new IllegalArgumentException("normal values must be finite");
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
            atlas = text(atlas, "atlas");
            chunkLayer = Objects.requireNonNull(chunkLayer, "chunkLayer")
                    .map(value -> text(value, "chunkLayer"));
            itemRenderType = Objects.requireNonNull(itemRenderType, "itemRenderType")
                    .map(value -> text(value, "itemRenderType"));
            ambientOcclusion = text(ambientOcclusion, "ambientOcclusion");
            foilType = text(foilType, "foilType");
            shadeMode = text(shadeMode, "shadeMode");
        }
    }

    private static String text(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
