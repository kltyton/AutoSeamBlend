package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 契约——pane 运行时方法解析必须等于 1.21.1 ce33d6c
 * AthenaPaneTilePlan.resolveRuntimeMethod 语义：configured=AUTO 且推断为 NONE 时降级为
 * CTM（通用几何可能把全臂 pane 状态误判为 NONE），显式方法绝不被改写。当前 26.1.2 Fabric
 * 没有等价组件，测试先红。
 *
 * <p>English: RED contract -- pane runtime method resolution must equal the 1.21.1 ce33d6c
 * AthenaPaneTilePlan.resolveRuntimeMethod semantics: configured=AUTO with an inferred NONE
 * degrades to CTM (generic geometry may misjudge full-arm pane states as NONE) while explicit
 * methods are never rewritten. The 26.1.2 Fabric side has no equivalent yet, so the test
 * fails first.
 */
class FabricPaneRuntimeMethodContractTest {

    @Test
    void autoWithInferredNoneDegradesToCtm() {
        assertEquals(
                ConnectionMethod.CTM,
                FabricPaneMaterialPlan.resolveRuntimeMethod(
                        ConnectionMethod.AUTO,
                        ConnectionMethod.NONE),
                "AUTO + inferred NONE must degrade to CTM for pane geometry");
    }

    @Test
    void autoKeepsOtherInferredMethods() {
        assertEquals(
                ConnectionMethod.CTM,
                FabricPaneMaterialPlan.resolveRuntimeMethod(
                        ConnectionMethod.AUTO,
                        ConnectionMethod.CTM));
        assertEquals(
                ConnectionMethod.OVERLAY,
                FabricPaneMaterialPlan.resolveRuntimeMethod(
                        ConnectionMethod.AUTO,
                        ConnectionMethod.OVERLAY));
    }

    @Test
    void explicitMethodNeverRewritten() {
        assertEquals(
                ConnectionMethod.CTM,
                FabricPaneMaterialPlan.resolveRuntimeMethod(
                        ConnectionMethod.CTM,
                        ConnectionMethod.NONE));
        assertEquals(
                ConnectionMethod.NONE,
                FabricPaneMaterialPlan.resolveRuntimeMethod(
                        ConnectionMethod.NONE,
                        ConnectionMethod.CTM));
    }
}
