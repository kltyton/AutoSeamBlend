package com.kltyton.autoseamblend.compat.ctm_mod.runtime.texture;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.contract.CtmModCarrierLayout.CarrierSpec;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.GeneratedStateRecipe;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe;
import com.kltyton.autoseamblend.texture.geometry.TextureCorner;
import com.kltyton.autoseamblend.texture.geometry.TextureEdge;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：规划 CTM Mod 载体单元的项目自有像素配方；不读取 Atlas、资源管理器或 CTM 类型。
 *
 * <p>English: Plans the project-owned pixel recipes for CTM Mod carrier cells without reading an
 * Atlas, a resource manager, or CTM types.</p>
 */
public final class CtmModCarrierRecipePlan {
    private CtmModCarrierRecipePlan() {}

    /**
     * 中文：按锁定的载体单元顺序生成配方；空 overlay 状态保留为空槽位。
     * English: Creates recipes in the locked carrier-cell order; empty overlay states remain
     * empty slots.
     */
    public static List<Optional<GeneratedTileRecipe>> recipes(
            ConnectionMethod method,
            CarrierSpec spec) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(spec, "spec");
        ArrayList<Optional<GeneratedTileRecipe>> recipes =
                new ArrayList<>(spec.cells().size());
        for (String cell : spec.cells()) {
            NeighborConnections connections = connections(cell);
            if (method.overlayCapable() && connections.bits() == 0) {
                recipes.add(Optional.empty());
                continue;
            }
            GeneratedTileRecipe recipe =
                    method == ConnectionMethod.CTM_COMPACT
                            ? new GeneratedTileRecipe.BorderConnections(connections)
                            : GeneratedStateRecipe.forConnections(method, connections);
            recipes.add(Optional.of(recipe));
        }
        return List.copyOf(recipes);
    }

    /**
     * 中文：保留 CTM Mod 原生动画元数据，只更新合成载体的帧尺寸。
     * English: Retains CTM Mod native animation metadata and updates only the composed carrier
     * frame size.
     */
    public static byte[] animationMetadata(
            byte[] sourceMetadata,
            int frameWidth,
            int frameHeight,
            boolean animated) {
        Objects.requireNonNull(sourceMetadata, "sourceMetadata");
        if (frameWidth <= 0 || frameHeight <= 0) {
            throw new IllegalArgumentException("carrier frame dimensions must be positive");
        }
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(
                    new String(sourceMetadata, StandardCharsets.UTF_8));
            root = parsed instanceof JsonObject object ? object.deepCopy() : new JsonObject();
        } catch (RuntimeException exception) {
            root = new JsonObject();
        }
        root.remove("ctm");
        if (animated && root.get("animation") instanceof JsonObject animation) {
            animation.addProperty("width", frameWidth);
            animation.addProperty("height", frameHeight);
        }
        return root.size() == 0
                ? new byte[0]
                : (root + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private static NeighborConnections connections(String cell) {
        return switch (Objects.requireNonNull(cell, "cell")) {
            case "horizontal" -> of(
                    EnumSet.of(TextureEdge.LEFT, TextureEdge.RIGHT),
                    EnumSet.noneOf(TextureCorner.class));
            case "left" -> of(
                    EnumSet.of(TextureEdge.LEFT),
                    EnumSet.noneOf(TextureCorner.class));
            case "right" -> of(
                    EnumSet.of(TextureEdge.RIGHT),
                    EnumSet.noneOf(TextureCorner.class));
            case "vertical" -> of(
                    EnumSet.of(TextureEdge.UP, TextureEdge.DOWN),
                    EnumSet.noneOf(TextureCorner.class));
            case "bottom" -> of(
                    EnumSet.of(TextureEdge.DOWN),
                    EnumSet.noneOf(TextureCorner.class));
            case "top" -> of(
                    EnumSet.of(TextureEdge.UP),
                    EnumSet.noneOf(TextureCorner.class));
            case "both" -> of(
                    EnumSet.of(TextureEdge.LEFT, TextureEdge.RIGHT, TextureEdge.UP, TextureEdge.DOWN),
                    EnumSet.noneOf(TextureCorner.class));
            case "corner" -> of(
                    EnumSet.allOf(TextureEdge.class),
                    EnumSet.allOf(TextureCorner.class));
            case "cornerless" -> of(
                    EnumSet.allOf(TextureEdge.class),
                    EnumSet.noneOf(TextureCorner.class));
            default -> NeighborConnections.none();
        };
    }

    private static NeighborConnections of(
            EnumSet<TextureEdge> edges,
            EnumSet<TextureCorner> corners) {
        return NeighborConnections.of(edges, corners);
    }
}
