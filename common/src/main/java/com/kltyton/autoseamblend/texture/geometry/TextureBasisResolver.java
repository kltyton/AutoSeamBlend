package com.kltyton.autoseamblend.texture.geometry;

import java.util.Objects;

/**
 * 中文：从四边形的实际位置和 UV 推导纹理空间方向。
 *
 * <p>English: Derives texture-space directions from the actual positions and UVs of a quad.
 * The algorithm is loader-neutral; each Loader only adapts its native quad to
 * {@link TextureBasisQuadView}.</p>
 */
public final class TextureBasisResolver {
    private static final OrientationAxes UP_AXES = new OrientationAxes(0, 1, 2, -1);
    private static final OrientationAxes DOWN_AXES = new OrientationAxes(0, 1, 2, 1);
    private static final OrientationAxes EAST_AXES = new OrientationAxes(2, -1, 1, 1);
    private static final OrientationAxes WEST_AXES = new OrientationAxes(2, 1, 1, 1);
    private static final OrientationAxes SOUTH_AXES = new OrientationAxes(0, 1, 1, 1);
    private static final OrientationAxes NORTH_AXES = new OrientationAxes(0, -1, 1, 1);

    private TextureBasisResolver() {}

    /**
     * 中文：使用 NeoForge 已验收的旋转/镜像判定规则计算纹理基向量。
     *
     * <p>English: Computes the texture basis using the rotation and mirror classification
     * accepted by the NeoForge implementation.</p>
     */
    public static TextureBasis resolve(TextureBasisQuadView quad) {
        Objects.requireNonNull(quad, "quad");
        WorldDirection face = Objects.requireNonNull(quad.face(), "quad.face");
        int orientation = textureOrientation(quad, face);
        if (orientation < 0) {
            return TextureBasis.canonical(face);
        }
        TextureBasis canonical = TextureBasis.canonical(face);
        int rotation = orientation & 3;
        int rightIndex = ((orientation & 4) != 0 ? rotation : 2 + rotation) & 3;
        int downIndex = (1 + rotation) & 3;
        return new TextureBasis(
                face,
                direction(canonical, rightIndex),
                direction(canonical, downIndex));
    }

    /**
     * 中文：用四个顶点的仿射导数识别旋转和镜像；负值表示 UV 退化。
     *
     * <p>English: Classifies rotation and mirroring from four affine vertex derivatives. A
     * negative result denotes degenerate UVs.</p>
     */
    private static int textureOrientation(TextureBasisQuadView quad, WorldDirection face) {
        float u31 = quad.u(3) - quad.u(1);
        float v31 = quad.v(3) - quad.v(1);
        float u20 = quad.u(2) - quad.u(0);
        float v20 = quad.v(2) - quad.v(0);
        float determinant = u31 * v20 - u20 * v31;
        if (determinant == 0.0F) {
            return -1;
        }
        float inverse = 1.0F / determinant;
        float firstCoefficient = -u20 * inverse;
        float secondCoefficient = u31 * inverse;

        OrientationAxes axes = switch (face) {
            case UP -> UP_AXES;
            case DOWN -> DOWN_AXES;
            case EAST -> EAST_AXES;
            case WEST -> WEST_AXES;
            case SOUTH -> SOUTH_AXES;
            case NORTH -> NORTH_AXES;
        };

        float first = -(positionDifference(
                                quad,
                                3,
                                1,
                                axes.firstComponent())
                        * firstCoefficient
                + positionDifference(
                                quad,
                                2,
                                0,
                                axes.firstComponent())
                        * secondCoefficient)
                * axes.firstSign();
        float second = -(positionDifference(
                                quad,
                                3,
                                1,
                                axes.secondComponent())
                        * firstCoefficient
                + positionDifference(
                                quad,
                                2,
                                0,
                                axes.secondComponent())
                        * secondCoefficient)
                * axes.secondSign();
        int rotation = Math.abs(second) >= Math.abs(first)
                ? second > 0.0F ? 0 : 2
                : first > 0.0F ? 3 : 1;
        return rotation + (determinant < 0.0F ? 4 : 0);
    }

    private static float positionDifference(
            TextureBasisQuadView quad,
            int leftVertex,
            int rightVertex,
            int component) {
        return quad.position(leftVertex, component) - quad.position(rightVertex, component);
    }

    private static WorldDirection direction(TextureBasis canonical, int index) {
        return switch (index) {
            case 0 -> canonical.left();
            case 1 -> canonical.down();
            case 2 -> canonical.right();
            case 3 -> canonical.up();
            default -> throw new IllegalArgumentException(
                    "texture direction index must be in [0,3]");
        };
    }

    private record OrientationAxes(
            int firstComponent,
            int firstSign,
            int secondComponent,
            int secondSign) {}
}
