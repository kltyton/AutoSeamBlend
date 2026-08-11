package com.kltyton.autoseamblend.runtime.render;

import com.kltyton.autoseamblend.texture.mapping.Overlay17Layout;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import java.util.Objects;

/**
 * 中文：按原生供体优先顺序为一个渲染面声明精确硬裁切像素。低优先级供体保留所有不重叠的边和角覆盖，高优先级供体已提供的像素会被移除，从而避免共面供体 Quad 冲突且不把整个面压缩成单一材质。
 *
 * English:
 * Claims exact hard-cutout pixels in native donor priority order for one rendered face.
 *
 * <p>Lower-priority donors retain all non-overlapping edge and corner coverage while pixels
 * already supplied by a higher-priority donor are removed. This prevents coplanar donor quads
 * from fighting without collapsing the face to a single material.
 */
public final class OverlayCoverageAllocator {
    private final int[] claimedRows =
            new int[16];

    public ProceduralConnectionPlan claim(
            OverlayCutoutProfile profile,
            Iterable<Integer> nativeSlots) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(
                nativeSlots,
                "nativeSlots");
        int tileMask = 0;
        for (Integer slotValue : nativeSlots) {
            if (slotValue == null) {
                continue;
            }
            int slot = slotValue;
            if (slot < 0
                    || slot >= Overlay17Layout.TILE_COUNT) {
                throw new IllegalArgumentException(
                        "Overlay slot must be in [0,16]: "
                                + slot);
            }
            tileMask |= 1 << slot;
        }
        int[] allocatedRows = new int[16];
        for (int y = 0;
                y < allocatedRows.length;
                y++) {
            int requested = profile.rowBits(
                    tileMask,
                    y);
            int allocated = requested
                    & ~claimedRows[y]
                    & 0xFFFF;
            allocatedRows[y] = allocated;
            claimedRows[y] |= allocated;
        }
        return ProceduralConnectionPlan.overlayRows(
                allocatedRows);
    }
}
