package com.kltyton.autoseamblend.compat.athena.materialize;

import com.google.gson.JsonObject;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.GeneratedStateRecipe;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import java.util.Objects;

/**
 * 中文：冻结 Athena four-slice 角色的公共声明判定和像素配方。
 *
 * English: Freezes common declaration detection and pixel recipes for Athena four-slice roles.
 */
public final class AthenaFourSliceTilePlan {
    private AthenaFourSliceTilePlan() {}

    /**
     * 中文：判断对象是否声明了 Athena 的连接 four-slice 角色。
     *
     * English: Tests whether an object declares Athena's connecting four-slice roles.
     */
    public static boolean isDeclared(JsonObject textures) {
        Objects.requireNonNull(textures, "textures");
        return textures.has("particle")
                || textures.has("center")
                || textures.has("vertical")
                || textures.has("horizontal")
                || textures.has("empty");
    }

    /**
     * 中文：按 Athena 原生角色位生成 project-owned 配方；未知或源角色保持原图。
     *
     * English: Creates a project-owned recipe for Athena's native role bits; unknown or source
     * roles preserve the original image.
     */
    public static GeneratedTileRecipe recipe(
            String role,
            ConnectionMethod method) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(method, "method");
        int bits = switch (role) {
            case "empty" -> 0xFF;
            case "center" -> 0x41;
            case "vertical" -> 0x40;
            case "horizontal" -> 0x01;
            default -> -1;
        };
        return bits < 0
                ? GeneratedTileRecipe.Source.INSTANCE
                : GeneratedStateRecipe.forConnections(
                        method,
                        NeighborConnections.fromBits(bits));
    }
}
