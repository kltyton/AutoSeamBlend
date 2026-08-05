package com.kltyton.autoseamblend.runtime.surface;

import com.kltyton.autoseamblend.inference.SurfaceGeometryPredicates;
import com.kltyton.autoseamblend.inference.SurfaceMaterialResolution;
import com.kltyton.autoseamblend.texture.atlas.InitialBlockAtlasResources.Snapshot;
import com.kltyton.autoseamblend.texture.atlas.InitialBlockAtlasResources.SourceRead;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.CuboidModelElement;
import net.minecraft.client.resources.model.cuboid.UnbakedCuboidGeometry;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

/**
 * 中文：在 Loader 接线前统一检查原始方块模型几何、材质、UV 与源纹理可用性。
 * English: Inspects raw block-model geometry, materials, UVs, and source availability before
 * Loader-specific wiring.
 */
public final class ModelGeometryInspector {
    private ModelGeometryInspector() {}

    public static Result inspect(
            Identifier dependency,
            TextureSlots textures,
            UnbakedCuboidGeometry geometry,
            Snapshot atlas) {
        Objects.requireNonNull(dependency, "dependency");
        Objects.requireNonNull(textures, "textures");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(atlas, "atlas");
        boolean fullBlock = geometry.elements().stream()
                .anyMatch(element -> SurfaceGeometryPredicates.isFullBlock(
                        element.rotation() == null,
                        element.from().x(),
                        element.from().y(),
                        element.from().z(),
                        element.to().x(),
                        element.to().y(),
                        element.to().z()));
        ArrayList<Face> faces = new ArrayList<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        boolean complete = true;
        for (CuboidModelElement element : geometry.elements()) {
            boolean axisAligned = element.rotation() == null;
            for (Map.Entry<Direction, CuboidFace> faceEntry : element.faces().entrySet()) {
                CuboidFace face = faceEntry.getValue();
                Material material;
                try {
                    material = SurfaceMaterialResolution.require(
                            face.texture(),
                            textures::getMaterial);
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
                SourceRead read = atlas.read(material.sprite());
                if (read.image().isEmpty()) {
                    complete = false;
                    diagnostics.add("FACE_SOURCE_UNAVAILABLE:"
                            + dependency
                            + ':'
                            + faceEntry.getKey()
                            + ':'
                            + material.sprite()
                            + ':'
                            + read.evidence()
                            + ':'
                            + read.detail());
                    continue;
                }
                boolean validUv = face.uvs() == null || SurfaceGeometryPredicates.hasFiniteUv(
                        face.uvs().minU(),
                        face.uvs().minV(),
                        face.uvs().maxU(),
                        face.uvs().maxV());
                faces.add(new Face(
                        faceEntry.getKey(),
                        read.image().orElseThrow(),
                        fullBlock,
                        axisAligned,
                        axisAligned && SurfaceGeometryPredicates.isFullFace(
                                element.from().x(),
                                element.from().y(),
                                element.from().z(),
                                element.to().x(),
                                element.to().y(),
                                element.to().z(),
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
