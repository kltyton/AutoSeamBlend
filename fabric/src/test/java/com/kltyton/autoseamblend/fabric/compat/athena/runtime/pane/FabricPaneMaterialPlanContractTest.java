package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.kltyton.autoseamblend.compat.athena.authoring.AthenaPaneTilePlan.Role;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 契约——pane 四个生成角色的连接位掩码必须等于 26.1.2 common
 * AthenaPaneTilePlan.generatedConnectionBits 的已验收值（EMPTY=0xFF、CENTER=0x55、
 * VERTICAL=0x44、HORIZONTAL=0x11），非生成角色必须明确拒绝。当前 26.1.2 Fabric 没有
 * 等价包装，测试先红。
 *
 * <p>English: RED contract -- the connection bit masks of the four generated pane roles must
 * equal the accepted 26.1.2 common AthenaPaneTilePlan.generatedConnectionBits values
 * (EMPTY=0xFF, CENTER=0x55, VERTICAL=0x44, HORIZONTAL=0x11) and non-generated roles must be
 * rejected loudly. The 26.1.2 Fabric side has no equivalent wrapper yet, so the test fails
 * first.
 */
class FabricPaneMaterialPlanContractTest {

    @Test
    void generatedConnectionBitsPerRole() {
        assertEquals(
                0xFF,
                FabricPaneMaterialPlan.connectionBits(
                        Role.EMPTY));
        assertEquals(
                0x55,
                FabricPaneMaterialPlan.connectionBits(
                        Role.CENTER));
        assertEquals(
                0x44,
                FabricPaneMaterialPlan.connectionBits(
                        Role.VERTICAL));
        assertEquals(
                0x11,
                FabricPaneMaterialPlan.connectionBits(
                        Role.HORIZONTAL));
    }

    @Test
    void nonGeneratedRolesRejectConnectionBits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FabricPaneMaterialPlan.connectionBits(
                        Role.PARTICLE));
        assertThrows(
                IllegalArgumentException.class,
                () -> FabricPaneMaterialPlan.connectionBits(
                        Role.EDGE));
        assertThrows(
                IllegalArgumentException.class,
                () -> FabricPaneMaterialPlan.connectionBits(
                        Role.SIDE_EDGE));
    }
}
