package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;

import earth.terrarium.athena.api.client.models.AthenaQuad;
import earth.terrarium.athena.api.client.utils.CtmState;
import earth.terrarium.athena.api.client.utils.CtmUtils;
import java.util.List;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 契约——FabricRuleAwarePaneModel 的单侧替换 quad 对必须逐值等于已验收
 * 1.21.1 NeoForge RuleAwarePaneModel.singleSideCorners（4.7.3 connectCorners=true 分支：
 * CW 侧左列 [0,1-arm]、CCW 侧右列 [arm,1]，上下两个半 quad，depth=0.4375，槽位走
 * CtmUtils.getTexture 真值表）。当前 Fabric 没有该路径，测试先红。
 *
 * <p>English: RED contract -- FabricRuleAwarePaneModel's single-side replacement quad pair
 * must match the accepted 1.21.1 NeoForge RuleAwarePaneModel.singleSideCorners value for
 * value (the 4.7.3 connectCorners=true branch: CW-side left column [0,1-arm], CCW-side
 * right column [arm,1], top/bottom half quads, depth=0.4375, slots from the
 * CtmUtils.getTexture truth table). The Fabric side has no such path yet, so the test
 * fails first.
 */
class FabricRuleAwarePaneModelContractTest {

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void cwSingleSideCornersMatchAcceptedNeoForgeGeometry() {
        CtmState state = new CtmState(
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false);
        float arm = 0.5625F;

        List<AthenaQuad> quads =
                FabricRuleAwarePaneModel.singleSideCorners(
                        state,
                        arm,
                        true);

        assertEquals(2, quads.size(), "CW side must emit two half quads");
        assertQuad(
                quads.get(0),
                CtmUtils.getTexture(
                        state.up(),
                        state.left(),
                        state.upLeft()),
                0.0F,
                1.0F - arm,
                1.0F,
                0.5F);
        assertQuad(
                quads.get(1),
                CtmUtils.getTexture(
                        state.down(),
                        state.left(),
                        state.downLeft()),
                0.0F,
                1.0F - arm,
                0.5F,
                0.0F);
    }

    @Test
    void ccwSingleSideCornersMatchAcceptedNeoForgeGeometry() {
        CtmState state = new CtmState(
                false,
                true,
                false,
                true,
                false,
                false,
                false,
                false);
        float arm = 0.4375F;

        List<AthenaQuad> quads =
                FabricRuleAwarePaneModel.singleSideCorners(
                        state,
                        arm,
                        false);

        assertEquals(2, quads.size(), "CCW side must emit two half quads");
        assertQuad(
                quads.get(0),
                CtmUtils.getTexture(
                        state.up(),
                        state.right(),
                        state.upRight()),
                arm,
                1.0F,
                1.0F,
                0.5F);
        assertQuad(
                quads.get(1),
                CtmUtils.getTexture(
                        state.down(),
                        state.right(),
                        state.downRight()),
                arm,
                1.0F,
                0.5F,
                0.0F);
    }

    private static void assertQuad(
            AthenaQuad quad,
            int slot,
            float left,
            float right,
            float top,
            float bottom) {
        assertEquals(slot, quad.sprite(), "slot must follow CtmUtils.getTexture");
        assertEquals(left, quad.left(), 1.0e-6F, "left bound");
        assertEquals(right, quad.right(), 1.0e-6F, "right bound");
        assertEquals(top, quad.top(), 1.0e-6F, "top bound");
        assertEquals(bottom, quad.bottom(), 1.0e-6F, "bottom bound");
        assertEquals(
                Rotation.NONE,
                quad.rotation(),
                "single-side corners keep the native rotation");
        assertEquals(
                0.4375F,
                quad.depth(),
                1.0e-6F,
                "single-side corners keep the native pane depth");
    }
}
