package com.kltyton.autoseamblend.compat.ctm_mod.authoring.contract;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 中文：对齐锁定的 CTM Mod 1.20.1（1.20.1-1.1.10）API 的纹理槽与精灵内单元布局。
 *
 * English: Texture-slot and in-sprite cell layouts aligned to the locked
 * CTM Mod 1.20.1 (1.20.1-1.1.10) API.
 */
public final class CtmModCarrierLayout {
    private CtmModCarrierLayout() {}

    public static NativeLayout forMethod(ConnectionMethod method) {
        return switch (Objects.requireNonNull(method, "method")) {
            case AUTO -> throw new IllegalArgumentException(
                    "auto must be resolved before CTM carrier planning");
            case HORIZONTAL -> forKind("bookshelf");
            case VERTICAL -> new NativeLayout(
                    "ctmv",
                    List.of(vertical(
                            "vertical",
                            "overlay_vertical",
                            "overlay_side",
                            "overlay_top",
                            "overlay_bottom",
                            "overlay_texture",
                            "layer1")));
            case TOP -> new NativeLayout(
                    "standard",
                    List.of(disconnected(
                            "top",
                            "base_texture",
                            "layer0")));
            case FIXED -> new NativeLayout(
                    "standard",
                    List.of(disconnected(
                            "fixed",
                            "base_texture",
                            "layer0")));
            case NONE -> new NativeLayout(
                    "standard",
                    List.of());
            case RUNTIME_BLEND, CTM, CTM_COMPACT,
                    HORIZONTAL_VERTICAL,
                    VERTICAL_HORIZONTAL, OVERLAY,
                    OVERLAY_CTM -> forKind("standard");
        };
    }

    public static NativeLayout forKind(String kind) {
        return switch (Objects.requireNonNull(kind, "kind")) {
            case "standard" -> new NativeLayout(
                    kind,
                    List.of(
                            disconnected(
                                    "disconnected",
                                    "overlay_texture",
                                    "layer1"),
                            connected(
                                    "connected",
                                    "overlay_connected",
                                    "layer1")));
            case "tbs" -> new NativeLayout(
                    kind,
                    List.of(
                            disconnected(
                                    "top_disconnected",
                                    "overlay_top",
                                    "overlay_texture",
                                    "layer1"),
                            connected(
                                    "top_connected",
                                    "overlay_top_connected",
                                    "overlay_connected"),
                            disconnected(
                                    "bottom_disconnected",
                                    "overlay_bottom",
                                    "overlay_texture",
                                    "layer1"),
                            connected(
                                    "bottom_connected",
                                    "overlay_bottom_connected",
                                    "overlay_connected"),
                            disconnected(
                                    "side_disconnected",
                                    "overlay_side",
                                    "overlay_texture",
                                    "layer1"),
                            connected(
                                    "side_connected",
                                    "overlay_side_connected",
                                    "overlay_connected")));
            case "bookshelf", "ctmh" -> new NativeLayout(
                    kind,
                    List.of(grid(
                            "horizontal",
                            List.of(
                                    "overlay_horizontal",
                                    "overlay_texture",
                                    "layer1"),
                            2,
                            2,
                            List.of(
                                    "none",
                                    "both",
                                    "right",
                                    "left"))));
            case "ctmv" -> new NativeLayout(
                    kind,
                    List.of(
                            vertical(
                                    "top",
                                    "overlay_top",
                                    "overlay_texture",
                                    "layer1"),
                            vertical(
                                    "bottom",
                                    "overlay_bottom",
                                    "overlay_texture",
                                    "layer1"),
                            vertical(
                                    "side",
                                    "overlay_vertical",
                                    "overlay_side",
                                    "overlay_texture",
                                    "layer1")));
            case "ar" -> new NativeLayout(
                    kind,
                    List.of(grid(
                            "ar",
                            List.of(
                                    "overlay_2x2",
                                    "overlay_texture",
                                    "layer1"),
                            2,
                            2,
                            indexedCells(4))));
            case "multiblock_2x2", "v4", "r4" ->
                    multiblock(kind, 2);
            case "multiblock_3x3", "v9", "r9" ->
                    multiblock(kind, 3);
            case "multiblock_4x4", "v16", "r16" ->
                    multiblock(kind, 4);
            default -> new NativeLayout(kind, List.of());
        };
    }

    private static NativeLayout multiblock(
            String kind,
            int size) {
        return new NativeLayout(
                kind,
                List.of(grid(
                        "multiblock_" + size + 'x' + size,
                        List.of("overlay_" + size + 'x' + size),
                        size,
                        size,
                        indexedCells(Math.multiplyExact(
                                size,
                                size)))));
    }

    private static CarrierSpec disconnected(
            String role,
            String... textureKeys) {
        return grid(
                role,
                List.of(textureKeys),
                1,
                1,
                List.of("disconnected"));
    }

    private static CarrierSpec connected(
            String role,
            String... textureKeys) {
        return grid(
                role,
                List.of(textureKeys),
                2,
                2,
                List.of(
                        "cornerless",
                        "horizontal",
                        "vertical",
                        "corner"));
    }

    private static CarrierSpec vertical(
            String role,
            String... textureKeys) {
        return grid(
                role,
                List.of(textureKeys),
                2,
                2,
                List.of(
                        "none",
                        "top",
                        "both",
                        "bottom"));
    }

    private static CarrierSpec grid(
            String role,
            List<String> textureKeys,
            int columns,
            int rows,
            List<String> cells) {
        return new CarrierSpec(
                role,
                textureKeys,
                columns,
                rows,
                cells);
    }

    private static List<String> indexedCells(int count) {
        ArrayList<String> cells = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            cells.add(Integer.toString(index));
        }
        return List.copyOf(cells);
    }

    public record NativeLayout(
            String kind,
            List<CarrierSpec> carriers) {
        public NativeLayout {
            if (kind == null || kind.isBlank()) {
                throw new IllegalArgumentException(
                        "kind must not be blank");
            }
            carriers = List.copyOf(
                    Objects.requireNonNull(
                            carriers,
                            "carriers"));
        }
    }

    public record CarrierSpec(
            String role,
            List<String> textureKeys,
            int columns,
            int rows,
            List<String> cells) {
        public CarrierSpec {
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException(
                        "role must not be blank");
            }
            textureKeys = List.copyOf(
                    Objects.requireNonNull(
                            textureKeys,
                            "textureKeys"));
            cells = List.copyOf(
                    Objects.requireNonNull(
                            cells,
                            "cells"));
            if (textureKeys.isEmpty()
                    || columns <= 0
                    || rows <= 0
                    || cells.size()
                            != Math.multiplyExact(
                                    columns,
                                    rows)) {
                throw new IllegalArgumentException(
                        "invalid CTM carrier grid");
            }
        }
    }
}
