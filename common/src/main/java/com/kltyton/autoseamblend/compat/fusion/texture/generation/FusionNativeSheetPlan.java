package com.kltyton.autoseamblend.compat.fusion.texture.generation;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.GeneratedStateRecipe;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe;
import com.kltyton.autoseamblend.texture.generation.fusion.FusionSheetMethodPlan;
import com.kltyton.autoseamblend.texture.geometry.TextureEdge;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import com.kltyton.autoseamblend.texture.mapping.Overlay17Layout;
import com.supermartijn642.fusion.api.model.custom.quad.EmittableQuad;
import com.supermartijn642.fusion.api.texture.types.connecting.ConnectingTextureData;
import com.supermartijn642.fusion.texture.types.connecting.TextureConnections;
import com.supermartijn642.fusion.texture.types.connecting.layouts.ConnectingTextureLayoutHandler;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：调用锁定 Fusion 1.3.12 布局处理器派生物理纹理表，不复制 ARR 槽位表；Fabric 与 NeoForge
 * 只提供各自 Loader 的 Fusion 运行时依赖。
 *
 * English: Derives the physical sheet by invoking the locked Fusion 1.3.12 layout handler without
 * copying its ARR slot table; Fabric and NeoForge only provide their Loader-local Fusion runtime.
 */
public record FusionNativeSheetPlan(
        String layout,
        int tileColumns,
        int tileRows,
        List<Optional<GeneratedTileRecipe>> tileRecipes,
        List<Boolean> topSourceTiles) {
    public FusionNativeSheetPlan {
        if (layout == null || layout.isBlank() || tileColumns <= 0 || tileRows <= 0) {
            throw new IllegalArgumentException("invalid Fusion sheet dimensions");
        }
        tileRecipes = List.copyOf(Objects.requireNonNull(tileRecipes, "tileRecipes"));
        topSourceTiles = List.copyOf(Objects.requireNonNull(topSourceTiles, "topSourceTiles"));
        int size = Math.multiplyExact(tileColumns, tileRows);
        if (tileRecipes.size() != size || topSourceTiles.size() != size) {
            throw new IllegalArgumentException("Fusion tile plan count differs from layout");
        }
    }

    /** 中文：NeoForge 基线使用 UP 作为 top-local edge。 / English: NeoForge baseline uses UP as the top-local edge. */
    public static FusionNativeSheetPlan create(ConnectionMethod method) {
        return create(method, TextureEdge.UP);
    }

    public static FusionNativeSheetPlan create(
            ConnectionMethod method,
            TextureEdge topLocalEdge) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(topLocalEdge, "topLocalEdge");
        if (method == ConnectionMethod.AUTO
                || method == ConnectionMethod.FIXED
                || method == ConnectionMethod.NONE) {
            throw new IllegalArgumentException(method + " does not use a Fusion connecting sheet");
        }
        ConnectingTextureData.Layout layout = nativeLayout(method);
        ConnectingTextureLayoutHandler handler = ConnectingTextureLayoutHandler.get(layout);
        List<Optional<GeneratedTileRecipe>> recipes;
        List<Boolean> topSources;
        if (layout == ConnectingTextureData.Layout.OVERLAY) {
            recipes = overlayRecipes(handler);
            topSources = java.util.Collections.nCopies(recipes.size(), false);
        } else {
            ReplacementTiles replacement = replacementPlans(handler, method, topLocalEdge);
            recipes = replacement.recipes();
            topSources = replacement.topSourceTiles();
        }
        return new FusionNativeSheetPlan(
                layout.name().toLowerCase(Locale.ROOT),
                handler.getWidth(),
                handler.getHeight(),
                recipes,
                topSources);
    }

    public static ConnectingTextureData.Layout nativeLayout(ConnectionMethod method) {
        return ConnectingTextureData.Layout.valueOf(
                FusionSheetMethodPlan.layout(method).name());
    }

    public static List<Integer> selectedTiles(
            ConnectionMethod method,
            NeighborConnections connections) {
        Objects.requireNonNull(connections, "connections");
        return java.util.Arrays.stream(selected(
                        ConnectingTextureLayoutHandler.get(nativeLayout(method)),
                        fusion(connections)))
                .boxed()
                .toList();
    }

    public static List<Integer> logicalSlots(ConnectionMethod method) {
        return FusionSheetMethodPlan.logicalSlots(method);
    }

    private static ReplacementTiles replacementPlans(
            ConnectingTextureLayoutHandler handler,
            ConnectionMethod method,
            TextureEdge topLocalEdge) {
        int tileCount = Math.multiplyExact(handler.getWidth(), handler.getHeight());
        NeighborConnections[] representatives = new NeighborConnections[tileCount];
        for (TextureConnections state : TextureConnections.iterateAll()) {
            NeighborConnections common = common(state);
            if (common.normalizedCtmBits() != common.bits()) {
                continue;
            }
            for (int tile : selected(handler, state)) {
                if (tile < 0 || tile >= tileCount) {
                    throw new IllegalStateException("Fusion emitted a tile outside its sheet");
                }
                if (representatives[tile] == null) {
                    representatives[tile] = common;
                }
            }
        }
        ArrayList<Optional<GeneratedTileRecipe>> recipes = new ArrayList<>(tileCount);
        ArrayList<Boolean> topSources = new ArrayList<>(tileCount);
        for (NeighborConnections representative : representatives) {
            recipes.add(representative == null
                    ? Optional.empty()
                    : Optional.of(GeneratedStateRecipe.forConnections(method, representative)));
            topSources.add(representative != null
                    && method == ConnectionMethod.TOP
                    && representative.connected(topLocalEdge));
        }
        return new ReplacementTiles(recipes, topSources);
    }

    private static List<Optional<GeneratedTileRecipe>> overlayRecipes(
            ConnectingTextureLayoutHandler handler) {
        int tileCount = Math.multiplyExact(handler.getWidth(), handler.getHeight());
        BitSet[] fusionSignatures = signatures(tileCount);
        BitSet[] commonSignatures = signatures(Overlay17Layout.TILE_COUNT);
        for (TextureConnections state : TextureConnections.iterateAll()) {
            NeighborConnections common = common(state);
            int bits = common.bits();
            for (int tile : selected(handler, state)) {
                fusionSignatures[tile].set(bits);
            }
            for (int slot : Overlay17Layout.selectedSlots(common)) {
                commonSignatures[slot].set(bits);
            }
        }
        ArrayList<Optional<GeneratedTileRecipe>> recipes = new ArrayList<>(tileCount);
        for (BitSet signature : fusionSignatures) {
            recipes.add(signature.isEmpty()
                    ? Optional.empty()
                    : Optional.of(new GeneratedTileRecipe.OverlayMask17(
                            matchingOverlaySlot(signature, commonSignatures))));
        }
        return List.copyOf(recipes);
    }

    private static BitSet[] signatures(int size) {
        BitSet[] result = new BitSet[size];
        for (int index = 0; index < size; index++) {
            result[index] = new BitSet(256);
        }
        return result;
    }

    private static int matchingOverlaySlot(BitSet fusion, BitSet[] common) {
        for (int slot = 0; slot < common.length; slot++) {
            if (fusion.equals(common[slot])) {
                return slot;
            }
        }
        throw new IllegalStateException(
                "Fusion overlay layout differs from the AutoBlend overlay contract");
    }

    private static int[] selected(
            ConnectingTextureLayoutHandler handler,
            TextureConnections state) {
        return FusionSheetMethodPlan.collectSelected(emitter -> {
            EmittableQuad quad = EmittableQuad.create(ignored -> {});
            handler.processQuad(quad, (tile, ignored) -> emitter.accept(tile), state);
        });
    }

    private static NeighborConnections common(TextureConnections value) {
        return FusionSheetMethodPlan.fromNativeFlags(
                value.left,
                value.bottomLeft,
                value.bottom,
                value.bottomRight,
                value.right,
                value.topRight,
                value.top,
                value.topLeft);
    }

    private static TextureConnections fusion(NeighborConnections value) {
        // 中文：Fusion 独立提供八方向，进入原生处理器前不能套用普通 CTM 对角门控。
        // English: Fusion supplies eight independent directions; do not normalize diagonals first.
        FusionSheetMethodPlan.NativeConnectionFlags state =
                FusionSheetMethodPlan.toNativeFlags(value);
        return new TextureConnections(
                state.top(),
                state.topRight(),
                state.right(),
                state.bottomRight(),
                state.bottom(),
                state.bottomLeft(),
                state.left(),
                state.topLeft());
    }

    private record ReplacementTiles(
            List<Optional<GeneratedTileRecipe>> recipes,
            List<Boolean> topSourceTiles) {
        private ReplacementTiles {
            recipes = List.copyOf(recipes);
            topSourceTiles = List.copyOf(topSourceTiles);
        }
    }
}
