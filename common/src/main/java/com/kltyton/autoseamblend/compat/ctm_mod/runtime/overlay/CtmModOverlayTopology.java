package com.kltyton.autoseamblend.compat.ctm_mod.runtime.overlay;

import com.kltyton.autoseamblend.texture.geometry.TextureBasis;
import com.kltyton.autoseamblend.texture.geometry.WorldDirection;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.Direction;

/**
 * 中文：冻结 CTM Mod 使用的 Continuity overlay-17 平面方向与角点门控；世界采样仍由 Loader 桥接负责。
 *
 * <p>English: Freezes the Continuity overlay-17 planar order and corner gate used by CTM Mod;
 * world sampling remains in the Loader bridge.</p>
 */
public final class CtmModOverlayTopology {
    private static final Map<Direction, List<Direction>> PLANAR_DIRECTIONS = buildPlanarDirections();

    private CtmModOverlayTopology() {}

    /** 中文：方向固定为面的左、下、右、上。 / English: Directions are fixed to face left, down, right, up. */
    public static List<Direction> planarDirections(Direction face) {
        return PLANAR_DIRECTIONS.get(Objects.requireNonNull(face, "face"));
    }

    /**
     * 中文：应用直边和同层 overlay 谓词后计算 17 状态；角点只在相邻直边均未应用且至少一侧同层时考虑。
     * English: Computes the 17-state bits after cardinal applications and same-overlay predicates;
     * a corner is considered only when neither adjacent cardinal applies and one side shares the overlay.
     */
    public static NeighborConnections state(
            boolean[] applications,
            boolean[] sameOverlay,
            boolean[] cornerApplications) {
        requireFour(applications, "applications");
        requireFour(sameOverlay, "sameOverlay");
        requireFour(cornerApplications, "cornerApplications");
        int bits = 0;
        for (int index = 0; index < 4; index++) {
            if (applications[index]) {
                bits |= 1 << (index * 2);
            }
        }
        for (int index = 0; index < 4; index++) {
            int next = (index + 1) & 3;
            if (!applications[index]
                    && !applications[next]
                    && (sameOverlay[index] || sameOverlay[next])
                    && cornerApplications[index]) {
                bits |= 1 << (index * 2 + 1);
            }
        }
        return NeighborConnections.fromBits(bits);
    }

    private static Map<Direction, List<Direction>> buildPlanarDirections() {
        EnumMap<Direction, List<Direction>> result = new EnumMap<>(Direction.class);
        for (Direction face : Direction.values()) {
            TextureBasis basis = TextureBasis.canonical(WorldDirection.valueOf(face.name()));
            result.put(face, List.of(
                    Direction.valueOf(basis.left().name()),
                    Direction.valueOf(basis.down().name()),
                    Direction.valueOf(basis.right().name()),
                    Direction.valueOf(basis.up().name())));
        }
        return Collections.unmodifiableMap(result);
    }

    private static void requireFour(boolean[] values, String label) {
        Objects.requireNonNull(values, label);
        if (values.length != 4) {
            throw new IllegalArgumentException(label + " must contain four entries");
        }
    }
}
