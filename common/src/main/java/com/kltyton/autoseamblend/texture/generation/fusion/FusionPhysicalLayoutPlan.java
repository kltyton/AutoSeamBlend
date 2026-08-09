package com.kltyton.autoseamblend.texture.generation.fusion;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：把 Fusion 原生物理单元映射到项目逻辑槽的不可变计划；物理槽发现由 Loader 适配器完成。
 *
 * English: Immutable mapping from Fusion physical cells to project logical slots; Loader
 * adapters remain responsible for discovering the native cells.
 */
public record FusionPhysicalLayoutPlan(
        List<List<Integer>> logicalCells,
        int physicalCellCount) {
    public FusionPhysicalLayoutPlan {
        logicalCells = List.copyOf(Objects.requireNonNull(logicalCells, "logicalCells")
                .stream()
                .map(List::copyOf)
                .toList());
        if (physicalCellCount <= 0
                || logicalCells.stream().anyMatch(cells -> cells.isEmpty()
                        || cells.stream().anyMatch(cell -> cell < 0 || cell >= physicalCellCount))) {
            throw new IllegalArgumentException("invalid Fusion native physical layout");
        }
    }

    /**
     * 中文：从适配器通过 Fusion layout handler 恢复的 recipe 列表建立公共逻辑槽映射。
     *
     * English: Builds the common logical-slot mapping from recipes recovered by a Loader adapter
     * through Fusion's layout handler.
     */
    public static Optional<FusionPhysicalLayoutPlan> resolve(
            ConnectionMethod method,
            List<Optional<GeneratedTileRecipe>> tileRecipes) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(tileRecipes, "tileRecipes");
        if (method == ConnectionMethod.AUTO || method == ConnectionMethod.NONE) {
            return Optional.empty();
        }
        if (method == ConnectionMethod.FIXED) {
            return Optional.of(new FusionPhysicalLayoutPlan(List.of(List.of(0)), 1));
        }
        if (tileRecipes.isEmpty()) {
            return Optional.empty();
        }
        LinkedHashMap<GeneratedTileRecipe, ArrayList<Integer>> cells = new LinkedHashMap<>();
        for (int cell = 0; cell < tileRecipes.size(); cell++) {
            int physicalCell = cell;
            tileRecipes.get(cell).ifPresent(recipe ->
                    cells.computeIfAbsent(recipe, ignored -> new ArrayList<>()).add(physicalCell));
        }
        List<List<Integer>> logical = cells.values().stream().map(List::copyOf).toList();
        if (logical.size() != FusionSheetMethodPlan.logicalSlots(method).size()) {
            return Optional.empty();
        }
        return Optional.of(new FusionPhysicalLayoutPlan(logical, tileRecipes.size()));
    }
}
