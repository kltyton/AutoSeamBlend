package com.kltyton.autoseamblend.forge.compat.athena.runtime;

import com.kltyton.autoseamblend.forge.testing.ForgeTestBootstrap;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：AthenaRenderPassPolicy 的纯策略锁定测试；只用 JDK List 与 Minecraft RenderType
 * 单例，不依赖 generation/引擎/Atlas。合同：仅在需要 overlay 时广告 cutout；solid pass
 * 只发 base，额外 cutout pass 只发 overlay，原生 cutout pass 同时发 base 与 overlay。
 *
 * <p>English: Pure-policy lock tests for AthenaRenderPassPolicy; uses only JDK List and
 * Minecraft RenderType singletons, with no generation/engine/atlas dependency. Contract:
 * cutout is advertised only when an overlay is needed; the solid pass emits base only, the
 * extra cutout pass emits overlay only, and a native cutout pass emits base and overlay.
 */
class AthenaRenderPassPolicyTest {

    @BeforeAll
    static void bootstrapRegistries() {
        // 中文：独立 JVM 测试需要游戏版本与注册表引导，否则 RenderType 静态初始化抛
        // ExceptionInInitializerError；与 CTM 政策测试同型，仅测试初始化。
        // English: Standalone JVM tests need a game version and registry bootstrap or
        // RenderType static init throws ExceptionInInitializerError; same shape as the
        // CTM policy test, test-only initialization.
        ForgeTestBootstrap.bootStrap();
    }

    @Test
    void advertisedTypesUnionsCutoutOnlyWhenNeedsOverlay() {
        List<RenderType> original = List.of(RenderType.solid());

        Set<RenderType> advertised = Set.copyOf(
                AthenaRenderPassPolicy.advertisedTypes(original, true));
        assertTrue(advertised.contains(RenderType.solid()));
        assertTrue(advertised.contains(RenderType.cutout()));

        Set<RenderType> without = Set.copyOf(
                AthenaRenderPassPolicy.advertisedTypes(original, false));
        assertTrue(without.contains(RenderType.solid()));
        assertFalse(without.contains(RenderType.cutout()));
    }

    @Test
    void solidCallIsBaseOnly() {
        AthenaRenderPassPolicy.PassDecision decision =
                AthenaRenderPassPolicy.decision(
                        List.of(RenderType.solid(), RenderType.cutout()),
                        RenderType.solid(),
                        true);
        assertTrue(decision.basePass());
        assertFalse(decision.overlayPass());
    }

    @Test
    void extraCutoutCallIsOverlayOnly() {
        AthenaRenderPassPolicy.PassDecision decision =
                AthenaRenderPassPolicy.decision(
                        List.of(RenderType.solid()),
                        RenderType.cutout(),
                        true);
        assertFalse(decision.basePass());
        assertTrue(decision.overlayPass());
    }

    @Test
    void nativeCutoutCallIsBaseAndOverlay() {
        AthenaRenderPassPolicy.PassDecision decision =
                AthenaRenderPassPolicy.decision(
                        List.of(RenderType.solid(), RenderType.cutout()),
                        RenderType.cutout(),
                        true);
        assertTrue(decision.basePass());
        assertTrue(decision.overlayPass());
    }

    @Test
    void noNeedsOverlayNeverAddsCutoutPass() {
        AthenaRenderPassPolicy.PassDecision decision =
                AthenaRenderPassPolicy.decision(
                        List.of(RenderType.solid(), RenderType.cutout()),
                        RenderType.cutout(),
                        false);
        assertTrue(decision.basePass());
        assertFalse(decision.overlayPass());
    }

    @Test
    void nullRenderTypeIsBasePass() {
        AthenaRenderPassPolicy.PassDecision decision =
                AthenaRenderPassPolicy.decision(
                        List.of(RenderType.solid()),
                        null,
                        true);
        assertTrue(decision.basePass());
        assertFalse(decision.overlayPass());
    }
}
