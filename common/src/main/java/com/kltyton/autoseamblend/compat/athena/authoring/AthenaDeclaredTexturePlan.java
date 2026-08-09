package com.kltyton.autoseamblend.compat.athena.authoring;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kltyton.autoseamblend.compat.athena.materialize.AthenaFourSliceTilePlan;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：把 Athena 4.0.6 原生方向与角色键声明展开为中立材质化槽位计划。
 * English: Expands Athena 4.0.6 native direction and role-key declarations into a neutral
 * materialization slot plan.
 */
public final class AthenaDeclaredTexturePlan {
    private static final List<String> DIRECTION_KEYS =
            List.of("default", "down", "up", "north", "south", "west", "east");

    private AthenaDeclaredTexturePlan() {}

    public static List<Slot> resolve(
            JsonObject root,
            ConnectionMethod method) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(method, "method");
        JsonElement encoded = root.get("ctm_textures");
        ArrayList<Slot> slots = new ArrayList<>();
        if (encoded instanceof JsonObject textures
                && AthenaFourSliceTilePlan.isDeclared(textures)) {
            if ("athena:pane_ctm".equals(string(root.get("athena:loader")))) {
                for (AthenaPaneTilePlan.PaneTile tile
                        : AthenaPaneTilePlan.forMethod(method)) {
                    String roleKey = tile.role().jsonKey();
                    String reference = string(textures.get(roleKey));
                    if (("edge".equals(roleKey) || "side_edge".equals(roleKey))
                            && reference == null) {
                        reference = string(textures.get("particle"));
                    }
                    slots.add(new Slot(
                            Optional.ofNullable(reference),
                            roleKey,
                            tile.recipe()));
                }
            } else {
                AthenaPhysicalTilePlan plan =
                        AthenaPhysicalTilePlan.forMethod(method);
                for (AthenaPhysicalTilePlan.Role role
                        : AthenaPhysicalTilePlan.Role.values()) {
                    slots.add(new Slot(
                            Optional.ofNullable(
                                    string(textures.get(role.jsonKey()))),
                            role.jsonKey(),
                            plan.recipes().get(role.nativeIndex())));
                }
            }
            return List.copyOf(slots);
        }
        if (encoded != null && encoded.isJsonPrimitive()
                && encoded.getAsJsonPrimitive().isString()) {
            String reference = encoded.getAsString();
            rejectIndexed(reference);
            slots.add(new Slot(
                    Optional.of(reference),
                    "plain",
                    GeneratedTileRecipe.Source.INSTANCE));
            return List.copyOf(slots);
        }
        if (encoded instanceof JsonObject) {
            collect(encoded, "declared", method, slots);
        } else {
            slots.add(new Slot(
                    Optional.empty(),
                    "unknown",
                    GeneratedTileRecipe.Source.INSTANCE));
        }
        return List.copyOf(slots);
    }

    private static void collect(
            JsonElement encoded,
            String fallbackName,
            ConnectionMethod method,
            List<Slot> output) {
        if (encoded != null && encoded.isJsonPrimitive()
                && encoded.getAsJsonPrimitive().isString()) {
            String reference = encoded.getAsString();
            rejectIndexed(reference);
            output.add(new Slot(
                    Optional.of(reference),
                    fallbackName,
                    GeneratedTileRecipe.Source.INSTANCE));
            return;
        }
        if (encoded instanceof JsonObject nested) {
            if (AthenaFourSliceTilePlan.isDeclared(nested)) {
                AthenaPhysicalTilePlan plan =
                        AthenaPhysicalTilePlan.forMethod(method);
                for (AthenaPhysicalTilePlan.Role role
                        : AthenaPhysicalTilePlan.Role.values()) {
                    output.add(new Slot(
                            Optional.ofNullable(
                                    string(nested.get(role.jsonKey()))),
                            fallbackName + '-' + role.jsonKey(),
                            plan.recipes().get(role.nativeIndex())));
                }
                return;
            }
            int initialSize = output.size();
            for (String key : DIRECTION_KEYS) {
                if (nested.has(key)) {
                    collect(nested.get(key), fallbackName + '-' + key, method, output);
                }
            }
            if (output.size() != initialSize) {
                return;
            }
        }
        output.add(new Slot(
                Optional.empty(),
                fallbackName,
                GeneratedTileRecipe.Source.INSTANCE));
    }

    private static void rejectIndexed(String reference) {
        if (reference.contains("[$index]")) {
            throw new IllegalArgumentException(
                    "Athena 4.0.6 does not support [$index] carriers; "
                            + "ctm_textures must use the native role-key object");
        }
    }

    private static String string(JsonElement value) {
        return value != null
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }

    public record Slot(
            Optional<String> reference,
            String fallbackName,
            GeneratedTileRecipe recipe) {
        public Slot {
            reference = Objects.requireNonNull(reference, "reference");
            if (fallbackName == null || fallbackName.isBlank()) {
                throw new IllegalArgumentException("fallbackName must not be blank");
            }
            Objects.requireNonNull(recipe, "recipe");
        }
    }
}
