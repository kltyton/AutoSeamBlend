package com.kltyton.autoseamblend.texture.geometry;

import java.util.Objects;

/**
 * 中文：把精灵相对方向映射到世界偏移；U 向右增长、V 向下增长。允许镜像 UV，不允许退化或倾斜 UV。
 *
 * English:
 * Maps sprite-relative directions to world offsets. Increasing U is right and
 * increasing V is down. Mirrored UVs are valid; degenerate or skewed UVs are not.
 */
public record TextureBasis(WorldDirection face, WorldDirection right, WorldDirection down) {
    public TextureBasis {
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(down, "down");
        if (face.offset().dot(right.offset()) != 0 || face.offset().dot(down.offset()) != 0) {
            throw new IllegalArgumentException("Texture axes must lie in the quad plane");
        }
        if (right.offset().dot(down.offset()) != 0) {
            throw new IllegalArgumentException("Texture axes must be perpendicular");
        }
    }

    public static TextureBasis fromUvGradients(
            WorldDirection face, UvGradient uGradient, UvGradient vGradient) {
        Objects.requireNonNull(uGradient, "uGradient");
        Objects.requireNonNull(vGradient, "vGradient");
        return new TextureBasis(face, uGradient.increasingDirection(), vGradient.increasingDirection());
    }

    /** 中文：模型提供未旋转轴对齐表面时使用的规范方向。 / English: Canonical orientation used when a model supplies an unrotated axis-aligned face. */
    public static TextureBasis canonical(WorldDirection face) {
        Objects.requireNonNull(face, "face");
        WorldDirection up = switch (face) {
            case UP -> WorldDirection.NORTH;
            case DOWN -> WorldDirection.SOUTH;
            default -> WorldDirection.UP;
        };
        WorldOffset leftOffset = face.offset().cross(up.offset());
        WorldDirection left = WorldDirection.fromUnitOffset(leftOffset);
        return new TextureBasis(face, left.opposite(), up.opposite());
    }

    public WorldDirection left() {
        return right.opposite();
    }

    public WorldDirection up() {
        return down.opposite();
    }

    public WorldOffset offset(TextureEdge edge) {
        return switch (edge) {
            case LEFT -> left().offset();
            case RIGHT -> right.offset();
            case UP -> up().offset();
            case DOWN -> down.offset();
        };
    }

    public WorldOffset offset(TextureCorner corner) {
        return offset(corner.firstEdge()).add(offset(corner.secondEdge()));
    }
}
