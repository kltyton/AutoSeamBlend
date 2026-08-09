package com.kltyton.autoseamblend.neoforge.compat.ctm_mod.runtime;

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
 * 中文：CtmModRenderPassPolicy 的纯策略锁定测试；只用 JDK List 与 Minecraft RenderType
 * 单例，不依赖 generation/引擎/Atlas。
 *
 * <p>English: Pure-policy lock tests for CtmModRenderPassPolicy; uses only JDK List and
 * Minecraft RenderType singletons, with no generation/engine/atlas dependency.
 */
class CtmModRenderPassPolicyTest {

    @BeforeAll
    static void bootstrapRegistries() {
        // 中文：独立 JVM 测试需要游戏版本与注册表引导，否则 RenderType 静态初始化抛
        // ExceptionInInitializerError；与 delegate 测试同型，仅测试初始化。
        // English: Standalone JVM tests need a game version and registry bootstrap or
        // RenderType static init throws ExceptionInInitializerError; same shape as the
        // delegate test, test-only initialization.
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        LoadingModList.of(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of());
        Bootstrap.bootStrap();
    }

    @Test
    void advertisedTypesUnionsCutoutOnlyWhenNeedsOverlay() {
        List<RenderType> original = List.of(RenderType.solid());

        Set<RenderType> advertised = Set.copyOf(
                CtmModRenderPassPolicy.advertisedTypes(original, true));
        assertTrue(advertised.contains(RenderType.solid()));
        assertTrue(advertised.contains(RenderType.cutout()));

        Set<RenderType> without = Set.copyOf(
                CtmModRenderPassPolicy.advertisedTypes(original, false));
        assertTrue(without.contains(RenderType.solid()));
        assertFalse(without.contains(RenderType.cutout()));
    }

    @Test
    void solidCallIsBaseOnly() {
        CtmModRenderPassPolicy.PassDecision decision =
                CtmModRenderPassPolicy.decision(
                        List.of(RenderType.solid(), RenderType.cutout()),
                        RenderType.solid(),
                        true);
        assertTrue(decision.basePass());
        assertFalse(decision.overlayPass());
    }

    @Test
    void extraCutoutCallIsOverlayOnly() {
        CtmModRenderPassPolicy.PassDecision decision =
                CtmModRenderPassPolicy.decision(
                        List.of(RenderType.solid()),
                        RenderType.cutout(),
                        true);
        assertFalse(decision.basePass());
        assertTrue(decision.overlayPass());
    }

    @Test
    void nativeCutoutCallIsBaseAndOverlay() {
        CtmModRenderPassPolicy.PassDecision decision =
                CtmModRenderPassPolicy.decision(
                        List.of(RenderType.solid(), RenderType.cutout()),
                        RenderType.cutout(),
                        true);
        assertTrue(decision.basePass());
        assertTrue(decision.overlayPass());
    }

    @Test
    void noNeedsOverlayNeverAddsCutoutPass() {
        CtmModRenderPassPolicy.PassDecision decision =
                CtmModRenderPassPolicy.decision(
                        List.of(RenderType.solid(), RenderType.cutout()),
                        RenderType.cutout(),
                        false);
        assertTrue(decision.basePass());
        assertFalse(decision.overlayPass());
    }

    @Test
    void nullRenderTypeIsBasePass() {
        CtmModRenderPassPolicy.PassDecision decision =
                CtmModRenderPassPolicy.decision(
                        List.of(RenderType.solid()),
                        null,
                        true);
        assertTrue(decision.basePass());
        assertFalse(decision.overlayPass());
    }
}
