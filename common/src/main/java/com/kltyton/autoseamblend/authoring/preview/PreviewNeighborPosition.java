package com.kltyton.autoseamblend.authoring.preview;

import java.util.Locale;

/**
 * 中文：预览中心周围允许编辑的十个固定邻位；Minecraft 坐标对象在渲染边界再构造。
 *
 * English:
 * Ten fixed editable positions around the preview center; Minecraft coordinate
 * objects are created only at the rendering boundary.
 */
public enum PreviewNeighborPosition {
    FRONT(0, 0, 1),
    BACK(0, 0, -1),
    UP(0, 1, 0),
    DOWN(0, -1, 0),
    LEFT(-1, 0, 0),
    RIGHT(1, 0, 0),
    LEFT_FRONT(-1, 0, 1),
    RIGHT_FRONT(1, 0, 1),
    LEFT_BACK(-1, 0, -1),
    RIGHT_BACK(1, 0, -1);

    private final int x;
    private final int y;
    private final int z;

    PreviewNeighborPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public String translationKey() {
        return "gui.autoseamblend.preview.position."
                + name().toLowerCase(Locale.ROOT);
    }
}
