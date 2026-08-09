package com.kltyton.autoseamblend.neoforge.compat.fusion.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：锁定 Fusion 1.21.1 overlay 只能在独立 CUTOUT pass 发射。
 *
 * <p>English: Locks Fusion 1.21.1 overlays to a dedicated CUTOUT pass.
 */
class FusionRenderPassPolicyTest {

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        LoadingModList.of(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of());
        Bootstrap.bootStrap();
    }

    @Test
    void advertisedTypesUnionsCutoutOnlyWhenNeeded() {
        List<RenderType> original = List.of(RenderType.solid());

        Set<RenderType> advertised = Set.copyOf(
                FusionRenderPassPolicy.advertisedTypes(original, true));
        assertTrue(advertised.contains(RenderType.solid()));
        assertTrue(advertised.contains(RenderType.cutout()));

        Set<RenderType> unchanged = Set.copyOf(
                FusionRenderPassPolicy.advertisedTypes(original, false));
        assertTrue(unchanged.contains(RenderType.solid()));
        assertFalse(unchanged.contains(RenderType.cutout()));
    }

    @Test
    void solidCallIsBaseOnly() {
        FusionRenderPassPolicy.PassDecision decision =
                FusionRenderPassPolicy.decision(
                        List.of(RenderType.solid()),
                        RenderType.solid(),
                        true);
        assertTrue(decision.basePass());
        assertFalse(decision.overlayPass());
    }

    @Test
    void extraCutoutCallIsOverlayOnly() {
        FusionRenderPassPolicy.PassDecision decision =
                FusionRenderPassPolicy.decision(
                        List.of(RenderType.cutoutMipped()),
                        RenderType.cutout(),
                        true);
        assertFalse(decision.basePass());
        assertTrue(decision.overlayPass());
    }

    @Test
    void nativeCutoutCallCanCarryBaseAndOverlay() {
        FusionRenderPassPolicy.PassDecision decision =
                FusionRenderPassPolicy.decision(
                        List.of(RenderType.cutout()),
                        RenderType.cutout(),
                        true);
        assertTrue(decision.basePass());
        assertTrue(decision.overlayPass());
    }

    @Test
    void noOverlayNeverUsesOverlayPass() {
        FusionRenderPassPolicy.PassDecision decision =
                FusionRenderPassPolicy.decision(
                        List.of(RenderType.cutout()),
                        RenderType.cutout(),
                        false);
        assertTrue(decision.basePass());
        assertFalse(decision.overlayPass());
    }

    @Test
    void nullRenderTypeIsBaseOnly() {
        FusionRenderPassPolicy.PassDecision decision =
                FusionRenderPassPolicy.decision(
                        List.of(RenderType.solid()),
                        null,
                        true);
        assertTrue(decision.basePass());
        assertFalse(decision.overlayPass());
    }
}
