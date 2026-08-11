package com.kltyton.autoseamblend.compat.ctm_mod.runtime.texture;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.GeneratedStateRecipe;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe;
import com.kltyton.autoseamblend.texture.geometry.TextureCorner;
import com.kltyton.autoseamblend.texture.geometry.TextureEdge;
import com.kltyton.autoseamblend.texture.mapping.AxisTileMapper;
import com.kltyton.autoseamblend.texture.mapping.Ctm47Mapper;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import com.kltyton.autoseamblend.texture.mapping.Overlay17Layout;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * 中文：把 CTM Lib 已定向的邻接状态映射到每个公开方法自己的运行时状态域。
 *
 * <p>English: Maps CTM Lib-oriented neighbor states into the distinct runtime state domain of
 * each public method.
 */
public final class CtmModMethodStateDomain {
    private CtmModMethodStateDomain() {}

    public static List<GeneratedTileRecipe> recipes(
            ConnectionMethod method) {
        return switch (Objects.requireNonNull(method, "method")) {
            case RUNTIME_BLEND, OVERLAY -> overlayRecipes();
            case CTM, CTM_COMPACT, OVERLAY_CTM ->
                    ctmRecipes(method);
            case HORIZONTAL, VERTICAL,
                    HORIZONTAL_VERTICAL, VERTICAL_HORIZONTAL ->
                    axisRecipes(method);
            case AUTO -> throw new IllegalArgumentException(
                    "auto must be resolved before CTM state planning");
            case TOP, FIXED, NONE -> List.of();
        };
    }

    public static int stateIndex(
            ConnectionMethod method,
            NeighborConnections connections) {
        Objects.requireNonNull(connections, "connections");
        return switch (Objects.requireNonNull(method, "method")) {
            case CTM, CTM_COMPACT, OVERLAY_CTM ->
                    Ctm47Mapper.tileIndex(connections);
            case HORIZONTAL -> AxisTileMapper.horizontal(connections);
            case VERTICAL -> AxisTileMapper.vertical(connections);
            case HORIZONTAL_VERTICAL ->
                    AxisTileMapper.horizontalVertical(connections);
            case VERTICAL_HORIZONTAL ->
                    AxisTileMapper.verticalHorizontal(connections);
            default -> throw new IllegalArgumentException(
                    method + " has no single CTM runtime state");
        };
    }

    /**
     * 中文：把一个已采样邻接状态映射为当前方法需要的生成槽位；仅 overlay 与 CTM 单槽路径在此分流。
     * English: Maps one sampled neighbor state to the generated slots required by the method;
     * only overlay and single-slot CTM paths branch here.
     */
    public static List<Integer> selectedSlots(
            ConnectionMethod method,
            NeighborConnections connections) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(connections, "connections");
        if (method == ConnectionMethod.RUNTIME_BLEND
                || method == ConnectionMethod.OVERLAY) {
            return Overlay17Layout.selectedSlots(connections);
        }
        if (!requiresGeneratedResult(method)) {
            return List.of();
        }
        int slot = stateIndex(method, connections);
        return slot < 0
                ? List.of()
                : List.of(slot);
    }

    public static boolean preservesIndependentCorners(
            ConnectionMethod method) {
        return method == ConnectionMethod.HORIZONTAL_VERTICAL
                || method == ConnectionMethod.VERTICAL_HORIZONTAL;
    }

    /** 中文：判断方法是否替换基础 Quad。 / English: Returns whether a method replaces base Quads. */
    public static boolean replacementMethod(ConnectionMethod method) {
        return switch (Objects.requireNonNull(method, "method")) {
            case CTM, CTM_COMPACT, HORIZONTAL, VERTICAL,
                    HORIZONTAL_VERTICAL, VERTICAL_HORIZONTAL -> true;
            default -> false;
        };
    }

    /** 中文：判断方法是否需要生成完整连接状态精灵。 / English: Returns whether a method needs generated full-state sprites. */
    public static boolean requiresGeneratedResult(ConnectionMethod method) {
        return switch (Objects.requireNonNull(method, "method")) {
            case RUNTIME_BLEND, CTM, CTM_COMPACT, HORIZONTAL, VERTICAL,
                    HORIZONTAL_VERTICAL, VERTICAL_HORIZONTAL, OVERLAY, OVERLAY_CTM -> true;
            default -> false;
        };
    }

    private static List<GeneratedTileRecipe> overlayRecipes() {
        ArrayList<GeneratedTileRecipe> recipes =
                new ArrayList<>(Overlay17Layout.TILE_COUNT);
        for (int slot = 0;
                slot < Overlay17Layout.TILE_COUNT;
                slot++) {
            recipes.add(new GeneratedTileRecipe.OverlayMask17(slot));
        }
        return List.copyOf(recipes);
    }

    private static List<GeneratedTileRecipe> ctmRecipes(
            ConnectionMethod method) {
        ArrayList<GeneratedTileRecipe> recipes =
                new ArrayList<>(Ctm47Mapper.TILE_COUNT);
        for (int slot = 0;
                slot < Ctm47Mapper.TILE_COUNT;
                slot++) {
            recipes.add(GeneratedStateRecipe.forConnections(
                    method,
                    Ctm47Mapper.connectionsForTile(slot)));
        }
        return List.copyOf(recipes);
    }

    private static List<GeneratedTileRecipe> axisRecipes(
            ConnectionMethod method) {
        List<NeighborConnections> states = switch (method) {
            case HORIZONTAL -> List.of(
                    edges(TextureEdge.RIGHT),
                    edges(TextureEdge.LEFT, TextureEdge.RIGHT),
                    edges(TextureEdge.LEFT),
                    NeighborConnections.none());
            case VERTICAL -> List.of(
                    edges(TextureEdge.UP),
                    edges(TextureEdge.DOWN, TextureEdge.UP),
                    edges(TextureEdge.DOWN),
                    NeighborConnections.none());
            case HORIZONTAL_VERTICAL -> List.of(
                    edges(TextureEdge.RIGHT),
                    edges(TextureEdge.LEFT, TextureEdge.RIGHT),
                    edges(TextureEdge.LEFT),
                    NeighborConnections.none(),
                    edges(TextureEdge.UP),
                    edges(TextureEdge.DOWN, TextureEdge.UP),
                    edges(TextureEdge.DOWN));
            case VERTICAL_HORIZONTAL -> List.of(
                    edges(TextureEdge.UP),
                    edges(TextureEdge.DOWN, TextureEdge.UP),
                    edges(TextureEdge.DOWN),
                    NeighborConnections.none(),
                    edges(TextureEdge.RIGHT),
                    edges(TextureEdge.LEFT, TextureEdge.RIGHT),
                    edges(TextureEdge.LEFT));
            default -> throw new IllegalArgumentException(
                    method + " is not an axis method");
        };
        ArrayList<GeneratedTileRecipe> recipes =
                new ArrayList<>(states.size());
        for (NeighborConnections state : states) {
            recipes.add(GeneratedStateRecipe.forConnections(
                    method,
                    state));
        }
        return List.copyOf(recipes);
    }

    private static NeighborConnections edges(
            TextureEdge... edges) {
        EnumSet<TextureEdge> connected =
                EnumSet.noneOf(TextureEdge.class);
        connected.addAll(List.of(edges));
        return NeighborConnections.of(
                connected,
                EnumSet.noneOf(TextureCorner.class));
    }
}
