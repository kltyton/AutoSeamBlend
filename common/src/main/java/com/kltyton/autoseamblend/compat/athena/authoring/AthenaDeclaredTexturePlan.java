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
 * 中文：把 Athena 原生方向、四切片与索引声明展开为中立材质化槽位计划。
 * English: Expands Athena native direction, four-slice, and indexed declarations into a neutral
 * materialization slot plan.
 */
public final class AthenaDeclaredTexturePlan {
    private static final List<String> FOUR_SLICE_ROLES =
            List.of("particle", "empty", "center", "vertical", "horizontal");
    private static final List<String> PANE_ROLES =
            List.of("particle", "empty", "center", "vertical", "horizontal", "edge", "side_edge");
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
            boolean pane = "athena:pane_ctm".equals(string(root.get("athena:loader")));
            List<String> roles = pane ? PANE_ROLES : FOUR_SLICE_ROLES;
            for (String role : roles) {
                String reference = string(textures.get(role));
                if (pane && ("edge".equals(role) || "side_edge".equals(role))
                        && reference == null) {
                    reference = string(textures.get("particle"));
                }
                slots.add(new Slot(
                        Optional.ofNullable(reference),
                        role,
                        AthenaFourSliceTilePlan.recipe(role, method)));
            }
            return List.copyOf(slots);
        }
        if (encoded != null && encoded.isJsonPrimitive()
                && encoded.getAsJsonPrimitive().isString()) {
            String reference = encoded.getAsString();
            if (reference.contains("[$index]")) {
                AthenaPhysicalTilePlan plan = AthenaPhysicalTilePlan.forNativeCarrier(method);
                for (int tile = 0;
                        tile < AthenaPhysicalTilePlan.Carrier.CTM_47.physicalSlots();
                        tile++) {
                    slots.add(new Slot(
                            Optional.of(reference.replace(
                                    "[$index]",
                                    Integer.toString(tile))),
                            Integer.toString(tile),
                            plan.recipes().get(tile)));
                }
            } else {
                slots.add(new Slot(
                        Optional.of(reference),
                        "plain",
                        GeneratedTileRecipe.Source.INSTANCE));
            }
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
            if (reference.contains("[$index]")) {
                AthenaPhysicalTilePlan plan = AthenaPhysicalTilePlan.forNativeCarrier(method);
                for (int tile = 0;
                        tile < AthenaPhysicalTilePlan.Carrier.CTM_47.physicalSlots();
                        tile++) {
                    output.add(new Slot(
                            Optional.of(reference.replace(
                                    "[$index]",
                                    Integer.toString(tile))),
                            fallbackName + '-' + tile,
                            plan.recipes().get(tile)));
                }
                return;
            }
            output.add(new Slot(
                    Optional.of(reference),
                    fallbackName,
                    GeneratedTileRecipe.Source.INSTANCE));
            return;
        }
        if (encoded instanceof JsonObject nested) {
            if (AthenaFourSliceTilePlan.isDeclared(nested)) {
                for (String role : FOUR_SLICE_ROLES) {
                    output.add(new Slot(
                            Optional.ofNullable(string(nested.get(role))),
                            fallbackName + '-' + role,
                            AthenaFourSliceTilePlan.recipe(role, method)));
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
