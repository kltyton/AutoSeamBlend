package com.kltyton.autoseamblend.inference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.inference.InferenceFacts.FactState;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class InferencePolicyTest {
    @Test
    void arbitraryOpaqueFullSurfaceInfersOverlay() {
        InferenceDecision decision = InferencePolicy.decide(
                ConnectionMethod.AUTO,
                facts(true, false, false, true, false));

        assertEquals(
                ConnectionMethod.OVERLAY,
                decision.resolvedMethod().orElseThrow());
        assertTrue(decision.evidence().contains(
                "uniform_full_block_native_overlay"));
        assertFalse(decision.manual());
    }

    @Test
    void arbitraryTintedSurfaceInfersOverlayWithoutAnIdentityWhitelist() {
        InferenceDecision decision = InferencePolicy.decide(
                ConnectionMethod.AUTO,
                facts(true, false, true, true, false));

        assertEquals(
                ConnectionMethod.OVERLAY,
                decision.resolvedMethod().orElseThrow());
        assertTrue(decision.evidence().contains(
                "animated_translucent_or_tinted_native_overlay"));
    }

    @Test
    void transparentFramedFullBlockSurfaceInfersCtm() {
        InferenceDecision decision = InferencePolicy.decide(
                ConnectionMethod.AUTO,
                facts(false, true, false, true, false));

        assertEquals(
                ConnectionMethod.CTM,
                decision.resolvedMethod().orElseThrow());
        assertTrue(decision.evidence().contains(
                "transparent_perimeter_frame_native_ctm"));
    }

    @Test
    void opaquePartialSurfaceIsNotAutomaticallyConnected() {
        InferenceDecision decision = InferencePolicy.decide(
                ConnectionMethod.AUTO,
                facts(true, false, false, false, true));

        assertEquals(ConnectionMethod.NONE, decision.resolvedMethod().orElseThrow());
        assertTrue(decision.evidence().contains("partial_block_not_auto_eligible"));
    }

    @Test
    void transparentSelfCullingPartialSurfaceRetainsCtm() {
        InferenceDecision decision = TransparentSelfConnectionInference.decide(
                ConnectionMethod.AUTO,
                facts(false, true, false, false, true),
                true);

        assertEquals(ConnectionMethod.CTM, decision.resolvedMethod().orElseThrow());
        assertTrue(decision.evidence().contains(
                "transparent_self_culling_partial_surface_ctm"));
    }

    @Test
    void transparentPartialSurfaceWithoutNativeSelfCullingIsNotConnected() {
        InferenceDecision decision = TransparentSelfConnectionInference.decide(
                ConnectionMethod.AUTO,
                facts(false, true, false, false, true),
                false);

        assertEquals(ConnectionMethod.NONE, decision.resolvedMethod().orElseThrow());
        assertTrue(decision.evidence().contains("partial_block_not_auto_eligible"));
    }

    @Test
    void explicitMethodStillOverridesPartialGeometry() {
        InferenceDecision decision = InferencePolicy.decide(
                ConnectionMethod.CTM,
                facts(true, false, false, false, true));

        assertEquals(ConnectionMethod.CTM, decision.resolvedMethod().orElseThrow());
        assertTrue(decision.manual());
    }

    @Test
    void topOnlyPartialSurfaceRetainsTopMethod() {
        InferenceDecision decision = InferencePolicy.decide(
                ConnectionMethod.AUTO,
                facts(true, false, false, false, true, true));

        assertEquals(ConnectionMethod.TOP, decision.resolvedMethod().orElseThrow());
        assertTrue(decision.evidence().contains("top_only_face_domain"));
    }

    @Test
    void tintedTranslucentFramedFullBlockSurfaceInfersOverlay() {
        // 中文：tinted translucent + framed alpha 的完整方块表面（如草侧 overlay 层）必须是
        // OVERLAY 供体，不能因透明边框被误判为 CTM 而退出 overlay 发射。
        // English: A tinted translucent framed-alpha full-block face (e.g. a grass-side overlay
        // layer) must infer OVERLAY so it can donate; the transparent frame must not demote it
        // to CTM and drop the overlay emission.
        InferenceDecision decision = InferencePolicy.decide(
                ConnectionMethod.AUTO,
                facts(false, true, true, true, false));

        assertEquals(
                ConnectionMethod.OVERLAY,
                decision.resolvedMethod().orElseThrow(),
                "tinted translucent overlay layer must infer OVERLAY");
        assertTrue(
                decision.evidence().contains(
                        "tinted_translucent_overlay_layer"));
    }

    @Test
    void manualMethodAlwaysOverridesFacts() {
        InferenceDecision decision = InferencePolicy.decide(
                ConnectionMethod.NONE,
                InferenceFacts.unknown());

        assertEquals(
                ConnectionMethod.NONE,
                decision.resolvedMethod().orElseThrow());
        assertTrue(decision.manual());
    }

    private static InferenceFacts facts(
            boolean opaque,
            boolean framed,
            boolean tinted,
            boolean fullBlock,
            boolean partialGeometry) {
        return facts(
                opaque,
                framed,
                tinted,
                fullBlock,
                partialGeometry,
                false);
    }

    private static InferenceFacts facts(
            boolean opaque,
            boolean framed,
            boolean tinted,
            boolean fullBlock,
            boolean partialGeometry,
            boolean topOnly) {
        return new InferenceFacts(
                FactState.TRUE,
                FactState.TRUE,
                FactState.TRUE,
                FactState.TRUE,
                FactState.TRUE,
                FactState.of(opaque),
                FactState.of(framed),
                FactState.FALSE,
                FactState.of(tinted),
                FactState.of(fullBlock),
                FactState.of(partialGeometry),
                FactState.of(topOnly),
                FactState.FALSE,
                FactState.TRUE,
                EnumSet.of(
                        ConnectionAxis.HORIZONTAL,
                        ConnectionAxis.VERTICAL));
    }
}
