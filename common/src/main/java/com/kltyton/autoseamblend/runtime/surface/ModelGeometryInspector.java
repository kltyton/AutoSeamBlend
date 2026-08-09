package com.kltyton.autoseamblend.runtime.surface;

import com.kltyton.autoseamblend.inference.SurfaceGeometryPredicates;
import com.kltyton.autoseamblend.inference.SurfaceMaterialResolution;
import com.kltyton.autoseamblend.texture.atlas.InitialBlockAtlasResources.Snapshot;
import com.kltyton.autoseamblend.texture.atlas.InitialBlockAtlasResources.SourceRead;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：在 Loader 接线前统一检查原始方块模型几何、材质、UV 与源纹理可用性。
 * English: Inspects raw block-model geometry, materials, UVs, and source availability before
 * Loader-specific wiring.
 */
public final class ModelGeometryInspector {
    private ModelGeometryInspector() {}

    public static Result inspect(
            ResourceLocation dependency,
            BlockModel model,
            Snapshot atlas) {
        Objects.requireNonNull(dependency, "dependency");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(atlas, "atlas");
        boolean fullBlock = model.getElements().stream()
                .anyMatch(element -> SurfaceGeometryPredicates.isFullBlock(
                        element.rotation == null,
                        element.from.x(),
                        element.from.y(),
                        element.from.z(),
                        element.to.x(),
                        element.to.y(),
                        element.to.z()));
        ArrayList<Face> faces = new ArrayList<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        boolean complete = true;
        for (BlockElement element : model.getElements()) {
            boolean axisAligned = element.rotation == null;
            for (Map.Entry<Direction, BlockElementFace> faceEntry : element.faces.entrySet()) {
                BlockElementFace face = faceEntry.getValue();
                Material material;
                try {
                    material = SurfaceMaterialResolution.require(
                            face.texture(),
                            model::getMaterial);
                } catch (SurfaceMaterialResolution.Rejected rejection) {
                    complete = false;
                    diagnostics.add("FACE_SKIPPED:"
                            + dependency
                            + ':'
                            + faceEntry.getKey()
                            + ':'
                            + rejection.getMessage());
                    continue;
                }
                SourceRead read = atlas.read(material.texture());
                if (read.image().isEmpty()) {
                    complete = false;
                    diagnostics.add("FACE_SOURCE_UNAVAILABLE:"
                            + dependency
                            + ':'
                            + faceEntry.getKey()
                            + ':'
                            + material.texture()
                            + ':'
                            + read.evidence()
                            + ':'
                            + read.detail());
                    continue;
                }
                boolean validUv = face.uv() == null
                        || face.uv().uvs == null
                        || SurfaceGeometryPredicates.hasFiniteUv(
                                face.uv().uvs[0],
                                face.uv().uvs[1],
                                face.uv().uvs[2],
                                face.uv().uvs[3]);
                faces.add(new Face(
                        faceEntry.getKey(),
                        read.image().orElseThrow(),
                        fullBlock,
                        axisAligned,
                        axisAligned && SurfaceGeometryPredicates.isFullFace(
                                element.from.x(),
                                element.from.y(),
                                element.from.z(),
                                element.to.x(),
                                element.to.y(),
                                element.to.z(),
                                faceEntry.getKey()),
                        validUv,
                        face.tintIndex()));
            }
        }
        return new Result(faces, complete, diagnostics);
    }

    public record Face(
            Direction direction,
            SurfaceSourceSnapshot source,
            boolean fullBlock,
            boolean axisAligned,
            boolean fullFace,
            boolean validUv,
            int tintIndex) {
        public Face {
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(source, "source");
        }
    }

    public record Result(
            List<Face> faces,
            boolean complete,
            List<String> diagnostics) {
        public Result {
            faces = List.copyOf(Objects.requireNonNull(faces, "faces"));
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }
}
