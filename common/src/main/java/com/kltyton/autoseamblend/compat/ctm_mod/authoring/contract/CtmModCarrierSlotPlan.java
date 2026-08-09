package com.kltyton.autoseamblend.compat.ctm_mod.authoring.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 中文：把 CTM Mod 载体的物理单元展开为稳定槽位地址；不含 Loader 的纹理对象。
 *
 * <p>English: Expands a CTM Mod carrier into stable physical slot addresses without Loader
 * texture objects.</p>
 */
public final class CtmModCarrierSlotPlan {
    private CtmModCarrierSlotPlan() {}

    public static List<Cell> cells(
            CtmModCarrierLayout.CarrierSpec spec,
            int frameWidth,
            int frameHeight) {
        Objects.requireNonNull(spec, "spec");
        if (frameWidth <= 0 || frameHeight <= 0
                || frameWidth % spec.columns() != 0
                || frameHeight % spec.rows() != 0) {
            throw new IllegalArgumentException("carrier frame does not fit CTM grid");
        }
        int cellWidth = frameWidth / spec.columns();
        int cellHeight = frameHeight / spec.rows();
        ArrayList<Cell> cells = new ArrayList<>(spec.cells().size());
        for (int cell = 0; cell < spec.cells().size(); cell++) {
            cells.add(new Cell(
                    cell,
                    spec.columns() == 1 && spec.rows() == 1
                            ? Kind.INDEPENDENT_PNG
                            : Kind.SHARED_SHEET,
                    cell % spec.columns() * cellWidth,
                    cell / spec.columns() * cellHeight,
                    cellWidth,
                    cellHeight));
        }
        return List.copyOf(cells);
    }

    public enum Kind {
        INDEPENDENT_PNG,
        SHARED_SHEET
    }

    public record Cell(
            int physicalIndex,
            Kind kind,
            int x,
            int y,
            int width,
            int height) {
        public Cell {
            if (physicalIndex < 0 || x < 0 || y < 0 || width <= 0 || height <= 0) {
                throw new IllegalArgumentException("invalid CTM physical cell");
            }
            Objects.requireNonNull(kind, "kind");
        }
    }
}
