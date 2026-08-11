package com.kltyton.autoseamblend.runtime.render;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.geometry.TextureCorner;
import com.kltyton.autoseamblend.texture.geometry.TextureEdge;
import com.kltyton.autoseamblend.texture.generation.GeneratedStateRecipe;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import com.kltyton.autoseamblend.texture.mapping.Overlay17Layout;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import com.kltyton.autoseamblend.texture.mask.TextureFrameProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 中文：一个已解析原生连接状态的不可变、引擎无关渲染指令。坐标归一化到源面，U 向右增长、V 向下增长；邻接发现与表面旋转仍由选定的原生引擎适配器负责。
 *
 * English:
 * Immutable, engine-neutral rendering instructions for one already-resolved native connection
 * state. Coordinates are normalized to the source face: U grows right and V grows down.
 *
 * <p>The plan never discovers neighbors or rotates a face. Those decisions remain owned by the
 * selected native engine adapter; this type only turns its accepted state or slots into small
 * source-sprite fragments.
 */
public record ProceduralConnectionPlan(Mode mode, List<Patch> patches) {
    private static final OverlayCutoutProfile DEFAULT_OVERLAY_PROFILE =
            OverlayCutoutProfile.thinUniform();
    private static final TextureFrameProfile DEFAULT_FRAME_PROFILE =
            TextureFrameProfile.fromAlpha(
                    16,
                    16,
                    false,
                    (x, y) -> true);
    private static final ConcurrentMap<OverlayPlanKey, ProceduralConnectionPlan> OVERLAY_PLANS =
            new ConcurrentHashMap<>();

    public ProceduralConnectionPlan {
        Objects.requireNonNull(mode, "mode");
        patches = List.copyOf(Objects.requireNonNull(patches, "patches"));
    }

    public static ProceduralConnectionPlan forConnections(
            ConnectionMethod method,
            NeighborConnections connections) {
        return forConnections(
                method,
                connections,
                DEFAULT_FRAME_PROFILE,
                DEFAULT_OVERLAY_PROFILE);
    }

    public static ProceduralConnectionPlan forConnections(
            ConnectionMethod method,
            NeighborConnections connections,
            TextureFrameProfile frameProfile,
            OverlayCutoutProfile overlayProfile) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(connections, "connections");
        Objects.requireNonNull(frameProfile, "frameProfile");
        Objects.requireNonNull(overlayProfile, "overlayProfile");
        return switch (method) {
            case RUNTIME_BLEND, OVERLAY, OVERLAY_CTM ->
                    overlaySlots(
                            overlayProfile,
                            Overlay17Layout.selectedSlots(connections));
            case NONE, FIXED, TOP, AUTO -> new ProceduralConnectionPlan(Mode.PASSTHROUGH, List.of());
            case CTM, CTM_COMPACT, HORIZONTAL, VERTICAL,
                    HORIZONTAL_VERTICAL, VERTICAL_HORIZONTAL ->
                    replacement(
                            connectionsForMethod(method, connections),
                            frameProfile);
        };
    }

    public static ProceduralConnectionPlan overlaySlots(Iterable<Integer> nativeSlots) {
        return overlaySlots(
                DEFAULT_OVERLAY_PROFILE,
                nativeSlots);
    }

    /**
     * 中文：在保留源 UV 域的同时描述全表面原生重贴图；用于所选适配器解析精确顶部精灵后的 top 预览路径，运行时适配器仍负责自身原生 Quad 变换。
     *
     * English:
     * Describes a full-face native retexture while preserving the source UV domain.
     *
     * <p>This is used by the {@code top} preview path after the selected adapter has resolved the
     * exact top sprite. Runtime adapters still perform their own native quad mutation.
     */
    public static ProceduralConnectionPlan sourceReplacement() {
        return new ProceduralConnectionPlan(
                Mode.REPLACE,
                List.of(Patch.opaque(
                        0.0F,
                        0.0F,
                        1.0F,
                        1.0F,
                        0.0F,
                        0.0F,
                        1.0F,
                        1.0F)));
    }

    public static ProceduralConnectionPlan overlaySlots(
            OverlayCutoutProfile profile,
            Iterable<Integer> nativeSlots) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(nativeSlots, "nativeSlots");
        int tileMask = 0;
        for (Integer slotValue : nativeSlots) {
            if (slotValue == null) continue;
            int slot = slotValue;
            if (slot < 0 || slot >= Overlay17Layout.TILE_COUNT) {
                throw new IllegalArgumentException(
                        "Overlay slot must be in [0,16]: " + slot);
            }
            tileMask |= 1 << slot;
        }
        if (tileMask == 0) {
            return new ProceduralConnectionPlan(Mode.PASSTHROUGH, List.of());
        }
        int selectedMask = tileMask;
        OverlayPlanKey key =
                new OverlayPlanKey(profile, selectedMask);
        return OVERLAY_PLANS.computeIfAbsent(
                key,
                ignored -> hardOverlayPlan(
                        profile,
                        selectedMask));
    }

    /**
     * 中文：从十六行精确的 16 位覆盖数据构建硬边覆盖几何；它在逐供体冲突分配后使用，不引入分数 alpha、重采样或生成纹理。
     *
     * English:
     * Builds hard overlay geometry from sixteen exact 16-bit coverage rows.
     *
     * <p>This is used after per-donor conflict allocation. It never introduces fractional alpha,
     * resampling, or generated textures.
     */
    public static ProceduralConnectionPlan overlayRows(
            int[] rowBits) {
        Objects.requireNonNull(rowBits, "rowBits");
        if (rowBits.length != 16) {
            throw new IllegalArgumentException(
                    "Overlay coverage must contain sixteen rows");
        }
        int[] stable = rowBits.clone();
        for (int row : stable) {
            if ((row & ~0xFFFF) != 0) {
                throw new IllegalArgumentException(
                        "Overlay coverage rows must fit in sixteen bits");
            }
        }
        return hardOverlayPlan(stable);
    }

    public static ProceduralConnectionPlan forOverlayCompletion(
            NeighborConnections connections,
            Iterable<Integer> missingSlots) {
        return forOverlayCompletion(
                connections,
                missingSlots,
                DEFAULT_OVERLAY_PROFILE);
    }

    public static ProceduralConnectionPlan forOverlayCompletion(
            NeighborConnections connections,
            Iterable<Integer> missingSlots,
            OverlayCutoutProfile profile) {
        Objects.requireNonNull(connections, "connections");
        Objects.requireNonNull(missingSlots, "missingSlots");
        Objects.requireNonNull(profile, "profile");
        LinkedHashSet<Integer> missing = new LinkedHashSet<>();
        for (Integer slot : missingSlots) {
            if (slot == null || slot < 0 || slot >= Overlay17Layout.TILE_COUNT) {
                throw new IllegalArgumentException(
                        "Overlay slots must be in [0,16]: " + slot);
            }
            missing.add(slot);
        }
        List<Integer> selected = Overlay17Layout.selectedSlots(connections).stream()
                .filter(missing::contains)
                .toList();
        return selected.isEmpty()
                ? new ProceduralConnectionPlan(Mode.PASSTHROUGH, List.of())
                : overlaySlots(profile, selected);
    }

    /**
     * 中文：只构建适配器所报告精确槽位缺失的 compact CTM 象限；各适配器通过原生状态路径提供每个定向象限的已选槽位。
     *
     * English:
     * Builds only the compact-CTM quadrants whose adapter-reported exact slot is missing.
     * Each adapter supplies the slot selected for each oriented quadrant by its native state path.
     */
    public static ProceduralConnectionPlan forCompactCompletion(
            NeighborConnections connections,
            CompactSlots selectedSlots,
            Iterable<Integer> missingSlots) {
        return forCompactCompletion(
                connections,
                selectedSlots,
                missingSlots,
                DEFAULT_FRAME_PROFILE);
    }

    public static ProceduralConnectionPlan forCompactCompletion(
            NeighborConnections connections,
            CompactSlots selectedSlots,
            Iterable<Integer> missingSlots,
            TextureFrameProfile frameProfile) {
        Objects.requireNonNull(connections, "connections");
        Objects.requireNonNull(selectedSlots, "selectedSlots");
        Objects.requireNonNull(missingSlots, "missingSlots");
        Objects.requireNonNull(frameProfile, "frameProfile");
        LinkedHashSet<Integer> missing = new LinkedHashSet<>();
        for (Integer slot : missingSlots) {
            if (slot == null || slot < 0 || slot > 4) {
                throw new IllegalArgumentException(
                        "Compact CTM slots must be in [0,4]: " + slot);
            }
            missing.add(slot);
        }
        if (missing.isEmpty()) {
            return new ProceduralConnectionPlan(Mode.PASSTHROUGH, List.of());
        }

        ArrayList<Patch> patches = new ArrayList<>(4);
        for (TextureCorner corner
                : TextureCorner.values()) {
            int slot = selectedSlots.forCorner(corner);
            if (!missing.contains(slot)) {
                continue;
            }
            patches.add(quadrantPatch(
                    corner,
                    connections,
                    frameProfile));
        }
        return patches.isEmpty()
                ? new ProceduralConnectionPlan(Mode.PASSTHROUGH, List.of())
                : new ProceduralConnectionPlan(Mode.REPLACE, patches);
    }

    private static ProceduralConnectionPlan replacement(
            NeighborConnections connections,
            TextureFrameProfile frameProfile) {
        if (connections.bits() == 0) {
            return new ProceduralConnectionPlan(
                    Mode.PASSTHROUGH,
                    List.of());
        }
        ArrayList<Patch> patches = new ArrayList<>(4);
        for (TextureCorner corner
                : TextureCorner.values()) {
            patches.add(quadrantPatch(
                    corner,
                    connections,
                    frameProfile));
        }
        return new ProceduralConnectionPlan(
                Mode.REPLACE,
                patches);
    }

    private static Patch quadrantPatch(
            TextureCorner corner,
            NeighborConnections connections,
            TextureFrameProfile frameProfile) {
        TextureEdge horizontal =
                corner == TextureCorner.TOP_LEFT
                                || corner == TextureCorner.BOTTOM_LEFT
                        ? TextureEdge.LEFT
                        : TextureEdge.RIGHT;
        TextureEdge vertical =
                corner == TextureCorner.TOP_LEFT
                                || corner == TextureCorner.TOP_RIGHT
                        ? TextureEdge.UP
                        : TextureEdge.DOWN;
        float x0 =
                horizontal == TextureEdge.LEFT
                        ? 0.0F
                        : 0.5F;
        float x1 =
                horizontal == TextureEdge.LEFT
                        ? 0.5F
                        : 1.0F;
        float y0 =
                vertical == TextureEdge.UP
                        ? 0.0F
                        : 0.5F;
        float y1 =
                vertical == TextureEdge.UP
                        ? 0.5F
                        : 1.0F;
        float u0 = x0;
        float u1 = x1;
        float v0 = y0;
        float v1 = y1;
        boolean clampHorizontal =
                horizontal != null && connections.connected(horizontal);
        boolean clampVertical =
                vertical != null && connections.connected(vertical);
        if (clampHorizontal && clampVertical) {
            if (!connections.connected(corner)) {
                clampHorizontal = false;
                clampVertical = false;
            }
        }
        if (clampHorizontal) {
            if (horizontal == TextureEdge.LEFT) {
                u0 = frameProfile.left();
            } else {
                u1 = 1.0F - frameProfile.right();
            }
        }
        if (clampVertical) {
            if (vertical == TextureEdge.UP) {
                v0 = frameProfile.up();
            } else {
                v1 = 1.0F - frameProfile.down();
            }
        }
        return Patch.opaque(x0, y0, x1, y1, u0, v0, u1, v1);
    }

    public static NeighborConnections connectionsForMethod(
            ConnectionMethod method,
            NeighborConnections connections) {
        return GeneratedStateRecipe.connectionsForMethod(
                method,
                connections);
    }

    /**
     * 中文：把参考兼容的 16x16 二值覆盖蒙版转换为合并矩形几何；UV 保持原位置，因此供体材质被裁切而非镜像、拉伸或 alpha 混合。
     *
     * English:
     * Converts the reference-compatible 16x16 binary overlay mask into merged rectangular
     * geometry. UV coordinates remain in their original positions, so the donor material is
     * clipped rather than mirrored, stretched, or alpha-blended.
     */
    private static ProceduralConnectionPlan hardOverlayPlan(
            OverlayCutoutProfile profile,
            int tileMask) {
        int[] rows = new int[16];
        for (int y = 0; y < rows.length; y++) {
            rows[y] = profile.rowBits(tileMask, y);
        }
        return hardOverlayPlan(rows);
    }

    private static ProceduralConnectionPlan hardOverlayPlan(
            int[] rows) {
        ArrayList<Patch> patches = new ArrayList<>();
        LinkedHashMap<Integer, ActiveRun> active = new LinkedHashMap<>();
        for (int y = 0; y < 16; y++) {
            int row = rows[y];
            LinkedHashMap<Integer, Run> current = runs(row);
            for (Map.Entry<Integer, ActiveRun> entry : active.entrySet()) {
                if (!current.containsKey(entry.getKey())) {
                    patches.add(entry.getValue().finish(y));
                }
            }
            LinkedHashMap<Integer, ActiveRun> next = new LinkedHashMap<>();
            for (Map.Entry<Integer, Run> entry : current.entrySet()) {
                Run run = entry.getValue();
                next.put(
                        entry.getKey(),
                        active.getOrDefault(
                                entry.getKey(),
                                new ActiveRun(run.x0(), run.x1(), y)));
            }
            active = next;
        }
        for (ActiveRun run : active.values()) {
            patches.add(run.finish(16));
        }
        return patches.isEmpty()
                ? new ProceduralConnectionPlan(Mode.PASSTHROUGH, List.of())
                : new ProceduralConnectionPlan(Mode.OVERLAY, patches);
    }

    private static LinkedHashMap<Integer, Run> runs(int row) {
        LinkedHashMap<Integer, Run> runs = new LinkedHashMap<>();
        int x = 0;
        while (x < 16) {
            while (x < 16 && (row & 1 << x) == 0) x++;
            if (x == 16) break;
            int x0 = x;
            while (x < 16 && (row & 1 << x) != 0) x++;
            Run run = new Run(x0, x);
            runs.put(x0 << 5 | x, run);
        }
        return runs;
    }

    private record Run(int x0, int x1) {}

    private record ActiveRun(int x0, int x1, int y0) {
        private Patch finish(int y1) {
            float u0 = x0 / 16.0F;
            float v0 = y0 / 16.0F;
            float u1 = x1 / 16.0F;
            float v1 = y1 / 16.0F;
            return Patch.opaque(u0, v0, u1, v1, u0, v0, u1, v1);
        }
    }

    public static void clearCachedOverlays() {
        OVERLAY_PLANS.clear();
    }

    private record OverlayPlanKey(
            OverlayCutoutProfile profile,
            int tileMask) {}

    public enum Mode {
        PASSTHROUGH,
        REPLACE,
        OVERLAY
    }

    public record CompactSlots(
            int topLeft,
            int topRight,
            int bottomLeft,
            int bottomRight) {
        public CompactSlots {
            checkCompactSlot(topLeft);
            checkCompactSlot(topRight);
            checkCompactSlot(bottomLeft);
            checkCompactSlot(bottomRight);
        }

        public int forCorner(TextureCorner corner) {
            return switch (Objects.requireNonNull(corner, "corner")) {
                case TOP_LEFT -> topLeft;
                case TOP_RIGHT -> topRight;
                case BOTTOM_LEFT -> bottomLeft;
                case BOTTOM_RIGHT -> bottomRight;
            };
        }

        private static void checkCompactSlot(int slot) {
            if (slot < 0 || slot > 4) {
                throw new IllegalArgumentException(
                        "Compact CTM slot must be in [0,4]: " + slot);
            }
        }
    }

    /**
     * 中文：Alpha 顺序对应表面四角：(x0,y0)、(x0,y1)、(x1,y1)、(x1,y0)。
     *
     * English:
     * Alpha order follows the face corners (x0,y0), (x0,y1), (x1,y1), (x1,y0).
     */
    public record Patch(
            float x0,
            float y0,
            float x1,
            float y1,
            float u0,
            float v0,
            float u1,
            float v1,
            int alpha00,
            int alpha01,
            int alpha11,
            int alpha10) {
        public Patch {
            checkRange("x0", x0);
            checkRange("y0", y0);
            checkRange("x1", x1);
            checkRange("y1", y1);
            checkRange("u0", u0);
            checkRange("v0", v0);
            checkRange("u1", u1);
            checkRange("v1", v1);
            if (x1 <= x0 || y1 <= y0) {
                throw new IllegalArgumentException("Patch geometry must have positive area");
            }
            checkAlpha(alpha00);
            checkAlpha(alpha01);
            checkAlpha(alpha11);
            checkAlpha(alpha10);
        }

        private static Patch opaque(
                float x0,
                float y0,
                float x1,
                float y1,
                float u0,
                float v0,
                float u1,
                float v1) {
            return new Patch(
                    x0, y0, x1, y1, u0, v0, u1, v1, 255, 255, 255, 255);
        }

        public int alpha(boolean right, boolean bottom) {
            if (right) return bottom ? alpha11 : alpha10;
            return bottom ? alpha01 : alpha00;
        }

        private static void checkRange(String name, float value) {
            if (!Float.isFinite(value) || value < 0.0F || value > 1.0F) {
                throw new IllegalArgumentException(name + " must be finite and in [0,1]");
            }
        }

        private static void checkAlpha(int alpha) {
            if (alpha < 0 || alpha > 255) {
                throw new IllegalArgumentException("alpha must be in [0,255]");
            }
        }
    }
}
