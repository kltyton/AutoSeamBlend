package com.kltyton.autoseamblend.frontend.model.preview;

import com.kltyton.autoseamblend.authoring.preview.PreviewNeighborPosition;
import com.kltyton.autoseamblend.frontend.model.PreviewViewModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Direction;

/**
 * 中文：跨 Loader 共享的方块立方体投影、可见面拾取和遮挡深度；真实 renderer 只消费投影结果。
 * English: Shared cross-Loader cube projection, visible-face picking, and occlusion depth;
 * real renderers only consume the projection results.
 */
public final class PreviewSceneProjection {
    private static final double DEPTH_EPSILON = 1.0E-7D;
    private static final int[][] CUBE_FACES = {
        {0, 1, 3, 2},
        {4, 6, 7, 5},
        {0, 4, 5, 1},
        {2, 3, 7, 6},
        {0, 2, 6, 4},
        {1, 5, 7, 3}
    };
    private static final Direction[] CUBE_FACE_DIRECTIONS = {
        Direction.DOWN,
        Direction.UP,
        Direction.NORTH,
        Direction.SOUTH,
        Direction.WEST,
        Direction.EAST
    };

    private PreviewSceneProjection() {}

    /**
     * 中文：投影中心和固定十邻位，顺序与 DESIGN.md 及渲染适配器一致。
     * English: Projects the center and ten fixed neighbors in the order shared by DESIGN.md and
     * the rendering adapters.
     */
    public static List<ProjectedNode> projectNodes(
            PreviewViewModel.Viewport viewport,
            PreviewViewModel.Camera camera,
            double fillScale,
            boolean includeNeighbors) {
        Objects.requireNonNull(viewport, "viewport");
        Objects.requireNonNull(camera, "camera");
        if (!Double.isFinite(fillScale) || fillScale <= 0.0D) {
            throw new IllegalArgumentException("preview fill scale must be finite and positive");
        }
        Projection projection = new Projection(
                PreviewSceneMath.cameraRotation(camera.yaw(), camera.pitch()),
                viewport.x() + viewport.width() / 2.0D + camera.panX(),
                viewport.y() + viewport.height() / 2.0D + camera.panY(),
                fillScale * camera.zoom());
        ArrayList<ProjectedNode> nodes = new ArrayList<>(includeNeighbors ? 11 : 1);
        nodes.add(projectCube(projection, Optional.empty(), 0, 0, 0, 0));
        if (includeNeighbors) {
            int order = 1;
            for (PreviewNeighborPosition position : PreviewNeighborPosition.values()) {
                nodes.add(projectCube(
                        projection,
                        Optional.of(position),
                        position.x(),
                        position.y(),
                        position.z(),
                        order++));
            }
        }
        nodes.sort(Comparator
                .comparingDouble(ProjectedNode::depth)
                .thenComparingInt(ProjectedNode::order));
        return List.copyOf(nodes);
    }

    public static Optional<Pick> pick(
            List<ProjectedNode> nodes,
            double mouseX,
            double mouseY,
            PreviewViewModel.Viewport viewport) {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(viewport, "viewport");
        if (!Double.isFinite(mouseX)
                || !Double.isFinite(mouseY)
                || mouseX < viewport.x()
                || mouseX >= viewport.x() + viewport.width()
                || mouseY < viewport.y()
                || mouseY >= viewport.y() + viewport.height()) {
            return Optional.empty();
        }
        Pick best = null;
        for (ProjectedNode node : nodes) {
            if (!node.bounds().contains(mouseX, mouseY)) {
                continue;
            }
            for (int faceIndex = 0; faceIndex < CUBE_FACES.length; faceIndex++) {
                double depth = PreviewSceneMath.faceDepthAt(
                        node.corners(),
                        CUBE_FACES[faceIndex],
                        mouseX,
                        mouseY);
                if (!Double.isFinite(depth)) {
                    continue;
                }
                Pick candidate = new Pick(
                        node,
                        CUBE_FACE_DIRECTIONS[faceIndex],
                        depth);
                if (isBefore(candidate, best)) {
                    best = candidate;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public static double frontDepthAt(
            ProjectedNode node,
            double x,
            double y) {
        Objects.requireNonNull(node, "node");
        if (!node.bounds().contains(x, y)) {
            return Double.POSITIVE_INFINITY;
        }
        double front = Double.POSITIVE_INFINITY;
        for (int[] face : CUBE_FACES) {
            front = Math.min(
                    front,
                    PreviewSceneMath.faceDepthAt(node.corners(), face, x, y));
        }
        return front;
    }

    public static boolean visible(
            ProjectedNode node,
            PreviewViewModel.Viewport viewport) {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(viewport, "viewport");
        return node.bounds().intersects(
                viewport.x(),
                viewport.y(),
                viewport.x() + viewport.width() - 1,
                viewport.y() + viewport.height() - 1);
    }

    private static ProjectedNode projectCube(
            Projection projection,
            Optional<PreviewNeighborPosition> neighbor,
            int x,
            int y,
            int z,
            int order) {
        ArrayList<ProjectedPoint> corners = new ArrayList<>(8);
        for (int yCorner = 0; yCorner <= 1; yCorner++) {
            for (int zCorner = 0; zCorner <= 1; zCorner++) {
                for (int xCorner = 0; xCorner <= 1; xCorner++) {
                    corners.add(projection.project(
                            x + xCorner - 0.5D,
                            y + yCorner - 0.5D,
                            z + zCorner - 0.5D));
                }
            }
        }
        return new ProjectedNode(
                neighbor,
                projection.project(x, y, z).depth(),
                Bounds.of(corners),
                List.copyOf(corners),
                order);
    }

    private static boolean isBefore(Pick candidate, Pick current) {
        return current == null
                || candidate.depth() < current.depth() - DEPTH_EPSILON
                || Math.abs(candidate.depth() - current.depth()) <= DEPTH_EPSILON
                        && (candidate.node().depth() < current.node().depth()
                                || candidate.node().depth() == current.node().depth()
                                        && candidate.node().order() < current.node().order());
    }

    private record Projection(
            org.joml.Quaternionf rotation,
            double centerX,
            double centerY,
            double scale) {
        private ProjectedPoint project(double x, double y, double z) {
            org.joml.Vector3f transformed = rotation.transform(
                    (float) x,
                    (float) y,
                    (float) z,
                    new org.joml.Vector3f());
            return new ProjectedPoint(
                    centerX + transformed.x * scale,
                    centerY + transformed.y * scale,
                    transformed.z);
        }
    }

    public record ProjectedNode(
            Optional<PreviewNeighborPosition> neighbor,
            double depth,
            Bounds bounds,
            List<ProjectedPoint> corners,
            int order) {
        public ProjectedNode {
            neighbor = Objects.requireNonNull(neighbor, "neighbor");
            bounds = Objects.requireNonNull(bounds, "bounds");
            corners = List.copyOf(Objects.requireNonNull(corners, "corners"));
            if (corners.size() != 8 || order < 0) {
                throw new IllegalArgumentException("projected node must have eight corners and a nonnegative order");
            }
        }
    }

    public record ProjectedPoint(
            double x,
            double y,
            double depth)
            implements PreviewSceneMath.ProjectedPoint {}

    public record Pick(
            ProjectedNode node,
            Direction face,
            double depth) {
        public Pick {
            Objects.requireNonNull(node, "node");
            Objects.requireNonNull(face, "face");
        }
    }

    public record Bounds(
            double minX,
            double minY,
            double maxX,
            double maxY) {
        private static Bounds of(List<? extends PreviewSceneMath.ProjectedPoint> points) {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            for (PreviewSceneMath.ProjectedPoint point : points) {
                minX = Math.min(minX, point.x());
                minY = Math.min(minY, point.y());
                maxX = Math.max(maxX, point.x());
                maxY = Math.max(maxY, point.y());
            }
            return new Bounds(minX, minY, maxX, maxY);
        }

        public boolean contains(double x, double y) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }

        public boolean intersects(int left, int top, int right, int bottom) {
            return maxX >= left
                    && minX <= right
                    && maxY >= top
                    && minY <= bottom;
        }
    }
}
