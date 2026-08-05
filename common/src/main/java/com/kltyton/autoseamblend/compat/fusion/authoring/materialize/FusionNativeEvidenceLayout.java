package com.kltyton.autoseamblend.compat.fusion.authoring.materialize;

import com.kltyton.autoseamblend.compat.fusion.texture.generation.FusionNativeSheetPlan;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.fusion.FusionPhysicalLayoutPlan;
import com.supermartijn642.fusion.api.texture.types.connecting.ConnectingTextureData;
import com.supermartijn642.fusion.texture.types.connecting.layouts.ConnectingTextureLayoutHandler;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：从锁定的 Fusion 布局处理器恢复物理表尺寸、默认单元及逻辑槽到物理单元的映射。
 *
 * English: Recovers sheet dimensions, the default cell, and logical-to-physical mapping from the locked Fusion layout handler.
 */
public record FusionNativeEvidenceLayout(
        int columns,
        int rows,
        int defaultCell,
        List<List<Integer>> logicalCells) {
    public FusionNativeEvidenceLayout {
        if (columns <= 0
                || rows <= 0
                || defaultCell < 0
                || defaultCell
                        >= Math.multiplyExact(
                                columns,
                                rows)) {
            throw new IllegalArgumentException(
                    "invalid Fusion evidence layout");
        }
        logicalCells = List.copyOf(
                Objects.requireNonNull(
                        logicalCells,
                        "logicalCells")
                        .stream()
                        .map(List::copyOf)
                        .toList());
        int cellCount = Math.multiplyExact(
                columns,
                rows);
        if (logicalCells.stream()
                .anyMatch(cells -> cells.isEmpty()
                        || cells.stream().anyMatch(cell ->
                                cell == null
                                        || cell < 0
                                        || cell >= cellCount))) {
            throw new IllegalArgumentException(
                    "invalid Fusion logical-cell mapping");
        }
    }

    public static Optional<FusionNativeEvidenceLayout> resolve(
            ConnectionMethod method,
            Optional<String> declaredLayout) {
        Objects.requireNonNull(method, "method");
        declaredLayout = Objects.requireNonNull(
                declaredLayout,
                "declaredLayout");
        try {
            ConnectingTextureData.Layout expected =
                    FusionNativeSheetPlan.nativeLayout(method);
            ConnectingTextureData.Layout declared =
                    declaredLayout
                            .map(value -> ConnectingTextureData.Layout
                                    .valueOf(value.trim()
                                            .toUpperCase(Locale.ROOT)))
                            .orElse(ConnectingTextureData.Layout.FULL);
            if (declared != expected) {
                return Optional.empty();
            }
            ConnectingTextureLayoutHandler handler =
                    ConnectingTextureLayoutHandler.get(expected);
            int columns = handler.getWidth();
            int rows = handler.getHeight();
            FusionNativeSheetPlan plan =
                    FusionNativeSheetPlan.create(
                            method);
            if (plan.tileColumns() != columns
                    || plan.tileRows() != rows) {
                return Optional.empty();
            }
            Optional<FusionPhysicalLayoutPlan> physical =
                    FusionPhysicalLayoutPlan.resolve(method, plan.tileRecipes());
            if (physical.isEmpty()) {
                return Optional.empty();
            }
            List<List<Integer>> logicalCells = physical.orElseThrow().logicalCells();
            int defaultCell = Math.addExact(
                    Math.multiplyExact(
                            handler.defaultTileY(),
                            columns),
                    handler.defaultTileX());
            if (logicalCells.size() != FusionNativeSheetPlan.logicalSlots(method).size()
                    || logicalCells.stream()
                            .noneMatch(cells ->
                                    cells.contains(defaultCell))) {
                return Optional.empty();
            }
            return Optional.of(new FusionNativeEvidenceLayout(
                    columns,
                    rows,
                    defaultCell,
                    logicalCells));
        } catch (IllegalArgumentException
                | IllegalStateException
                | ArithmeticException exception) {
            return Optional.empty();
        }
    }
}
