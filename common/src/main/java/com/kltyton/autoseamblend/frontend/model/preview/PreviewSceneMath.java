package com.kltyton.autoseamblend.frontend.model.preview;

import java.util.List;
import java.util.Objects;
import org.joml.Quaternionf;

/**
 * 中文：预览场景跨 Loader 共用的投影与拾取数学；渲染器只负责提供坐标和使用结果。
 *
 * English:
 * Loader-neutral projection and picking math for preview scenes; renderers
 * provide coordinates and consume the results.
 */
public final class PreviewSceneMath {
    private static final double DEPTH_EPSILON = 1.0E-7D;

    private PreviewSceneMath() {}

    /**
     * 中文：Fabric 和 NeoForge PIP 使用同一组轴顺序，保证投影与拾取的方向一致。
     *
     * English:
     * Fabric and NeoForge PIP paths use one axis order so projection and
     * picking retain the same orientation.
     */
    public static Quaternionf cameraRotation(
            float yaw,
            float pitch) {
        return new Quaternionf()
                .rotateZ((float) Math.PI)
                .rotateX((float) Math.toRadians(pitch))
                .rotateY((float) Math.toRadians(yaw));
    }

    /**
     * 中文：用两个三角形的重心坐标插值四边形面深度。
     *
     * English:
     * Interpolates a quadrilateral face depth with the barycentric coordinates
     * of its two triangles.
     */
    public static double faceDepthAt(
            List<? extends ProjectedPoint> corners,
            int[] face,
            double x,
            double y) {
        Objects.requireNonNull(corners, "corners");
        Objects.requireNonNull(face, "face");
        if (face.length != 4) {
            throw new IllegalArgumentException("preview face must have four corners");
        }
        return Math.min(
                triangleDepthAt(
                        corners.get(face[0]),
                        corners.get(face[1]),
                        corners.get(face[2]),
                        x,
                        y),
                triangleDepthAt(
                        corners.get(face[0]),
                        corners.get(face[2]),
                        corners.get(face[3]),
                        x,
                        y));
    }

    private static double triangleDepthAt(
            ProjectedPoint first,
            ProjectedPoint second,
            ProjectedPoint third,
            double x,
            double y) {
        double area = cross(
                first,
                second,
                third.x(),
                third.y());
        if (Math.abs(area) < DEPTH_EPSILON) {
            return Double.POSITIVE_INFINITY;
        }
        double firstWeight = cross(
                second,
                third,
                x,
                y)
                / area;
        double secondWeight = cross(
                third,
                first,
                x,
                y)
                / area;
        double thirdWeight = cross(
                first,
                second,
                x,
                y)
                / area;
        if (firstWeight < -DEPTH_EPSILON
                || secondWeight < -DEPTH_EPSILON
                || thirdWeight < -DEPTH_EPSILON) {
            return Double.POSITIVE_INFINITY;
        }
        return firstWeight * first.depth()
                + secondWeight * second.depth()
                + thirdWeight * third.depth();
    }

    private static double cross(
            ProjectedPoint start,
            ProjectedPoint end,
            double x,
            double y) {
        return (end.x() - start.x())
                        * (y - start.y())
                - (end.y() - start.y())
                        * (x - start.x());
    }

    /**
     * 中文：Loader 投影点只需暴露屏幕坐标和深度，不携带引擎对象。
     *
     * English:
     * Loader projection points expose only screen coordinates and depth, with
     * no engine-specific objects.
     */
    public interface ProjectedPoint {
        double x();

        double y();

        double depth();
    }
}
