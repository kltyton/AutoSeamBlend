package com.kltyton.autoseamblend.authoring.preview;

import java.util.List;
import java.util.Objects;

/**
 * 中文：不携带 Loader、世界或渲染上下文的冻结预览 Quad。
 *
 * English:
 * Frozen preview quad with no Loader, world, or render-context dependency.
 */
public record PreviewQuad(
        List<Vertex> vertices,
        Material material,
        PreviewRenderLayer layer,
        String face,
        Winding winding,
        int tintIndex,
        boolean emissive,
        boolean ambientOcclusion,
        boolean foil,
        boolean shade) {
    public PreviewQuad {
        vertices = List.copyOf(Objects.requireNonNull(vertices, "vertices"));
        if (vertices.size() != 4) {
            throw new IllegalArgumentException("preview quad needs exactly four vertices");
        }
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(layer, "layer");
        if (face == null || face.isBlank()) {
            throw new IllegalArgumentException("preview quad face must not be blank");
        }
        Objects.requireNonNull(winding, "winding");
    }

    /**
     * 中文：保持原始顶点色、光照、overlay、法线和 sprite-local UV。
     *
     * English:
     * Preserves raw vertex color, light, overlay, normal, and sprite-local UV.
     */
    public record Vertex(
            float x,
            float y,
            float z,
            float u,
            float v,
            int argb,
            int lightmap,
            int overlay,
            float normalX,
            float normalY,
            float normalZ) {
        public Vertex {
            if (!Float.isFinite(x)
                    || !Float.isFinite(y)
                    || !Float.isFinite(z)
                    || !Float.isFinite(u)
                    || !Float.isFinite(v)
                    || !Float.isFinite(normalX)
                    || !Float.isFinite(normalY)
                    || !Float.isFinite(normalZ)) {
                throw new IllegalArgumentException("preview vertex values must be finite");
            }
        }
    }

    /**
     * 中文：材质只保存稳定资源标识字符串，真正 atlas sprite 在提交时按当前资源代次解析。
     *
     * English:
     * The material stores stable resource-id strings only; the real atlas
     * sprite is resolved at submission time from the current resource
     * generation.
     */
    public record Material(String atlasId, String spriteId) {
        public Material {
            if (atlasId == null || atlasId.isBlank()) {
                throw new IllegalArgumentException("preview atlas id must not be blank");
            }
            if (spriteId == null || spriteId.isBlank()) {
                throw new IllegalArgumentException("preview sprite id must not be blank");
            }
        }
    }

    /** 中文：保持捕获时的顶点顺序。 / English: Preserves the captured vertex order. */
    public enum Winding {
        CLOCKWISE,
        COUNTERCLOCKWISE
    }
}
