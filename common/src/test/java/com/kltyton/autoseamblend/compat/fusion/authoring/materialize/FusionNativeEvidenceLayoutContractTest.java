package com.kltyton.autoseamblend.compat.fusion.authoring.materialize;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.compat.fusion.texture.generation.FusionNativeSheetPlan;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.fusion.FusionPhysicalLayoutPlan;
import com.supermartijn642.fusion.api.texture.types.connecting.ConnectingTextureData;
import com.supermartijn642.fusion.texture.types.connecting.layouts.ConnectingTextureLayoutHandler;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 中文：永久合同——Fusion 1.3.12 生成的 sheet 布局必须能被证据解析，纹理编辑器才能打开。
 * FusionNativeSheetPlan.create 支持的每个连接方法都用自身 plan.layout() 解析必须成功；
 * 其中 OVERLAY（runtime_blend）的 default tile (1,1)（cell 7）是合法空物理格，可不属于
 * 任何逻辑槽而布局仍然有效。
 *
 * <p>English: Permanent contract -- every Fusion 1.3.12 generated sheet layout must resolve
 * through its own plan.layout() so the texture editor can open. For OVERLAY (runtime_blend)
 * the default tile (1,1) (cell 7) is a legal empty physical cell: it may belong to no
 * logical slot while the layout stays valid.
 */
class FusionNativeEvidenceLayoutContractTest {

    @ParameterizedTest(
            name = "Fusion 1.3.12 generated layouts open texture editor for {0}")
    @EnumSource(
            value = ConnectionMethod.class,
            names = {
                "RUNTIME_BLEND",
                "CTM",
                "CTM_COMPACT",
                "HORIZONTAL",
                "VERTICAL",
                "HORIZONTAL_VERTICAL",
                "VERTICAL_HORIZONTAL",
                "TOP",
                "OVERLAY",
                "OVERLAY_CTM"
            })
    void generatedLayoutsOpenTextureEditor(ConnectionMethod method) {
        FusionNativeSheetPlan plan =
                FusionNativeSheetPlan.create(method);
        assertTrue(
                FusionNativeEvidenceLayout.resolve(
                                method,
                                Optional.of(plan.layout()))
                        .isPresent(),
                method.serializedName()
                        + ": Fusion 1.3.12 generated "
                        + plan.layout()
                        + " layout must resolve so the texture editor can open "
                        + "(FUSION_NATIVE_LAYOUT_INVALID regression)");
    }

    @Test
    void runtimeBlendOverlayDefaultCellMaySitOutsideLogicalSlotsAndLayoutStillResolves() {
        ConnectionMethod method =
                ConnectionMethod.RUNTIME_BLEND;
        FusionNativeSheetPlan plan =
                FusionNativeSheetPlan.create(method);
        ConnectingTextureData.Layout nativeLayout =
                FusionNativeSheetPlan.nativeLayout(method);
        ConnectingTextureLayoutHandler handler =
                ConnectingTextureLayoutHandler.get(nativeLayout);
        int columns = handler.getWidth();
        int rows = handler.getHeight();
        int defaultCell =
                Math.addExact(
                        Math.multiplyExact(
                                handler.defaultTileY(),
                                columns),
                        handler.defaultTileX());
        Optional<FusionPhysicalLayoutPlan> physical =
                FusionPhysicalLayoutPlan.resolve(
                        method,
                        plan.tileRecipes());
        assertTrue(
                physical.isPresent(),
                "FusionPhysicalLayoutPlan.resolve must be present for "
                        + method.serializedName());
        List<List<Integer>> logicalCells =
                physical.orElseThrow().logicalCells();
        assertTrue(
                logicalCells.stream()
                        .noneMatch(cells ->
                                cells.contains(defaultCell)),
                "Fusion 1.3.12 OVERLAY defaultCell=" + defaultCell
                        + " is a legal empty physical cell outside all "
                        + "logical slots, but found in logicalCells="
                        + logicalCells);
        assertTrue(
                FusionNativeEvidenceLayout.resolve(
                                method,
                                Optional.of(plan.layout()))
                        .isPresent(),
                "runtime_blend generated layout must still resolve when "
                        + "defaultCell=" + defaultCell
                        + " belongs to no logical slot");
    }
}
