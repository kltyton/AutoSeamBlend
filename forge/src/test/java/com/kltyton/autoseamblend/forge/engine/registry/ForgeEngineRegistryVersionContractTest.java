package com.kltyton.autoseamblend.forge.engine.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 中文：锁定 Forge 引擎版本门对 1.20.1 两个 Continuity 实现与其他原生运行时产物的
 * 精确版本。官方 Continuity Forge 产物与 Constancy 的 `continuity` stub 共用现有适配器；
 * 未审计版本仍必须拒绝。
 *
 * <p>English: Locks the Forge engine registry to the exact native 1.20.1 runtime
 * artifacts. The official Continuity Forge artifact and Constancy's compatibility
 * `continuity` stub share the existing adapter while unreviewed versions remain rejected.
 */
class ForgeEngineRegistryVersionContractTest {
    @Test
    void continuityPinMatchesTheDocumentedForgePort() {
        assertEquals(
                "3.0.0+1.20.1.forge",
                ForgeEngineRegistry.expectedVersion("continuity"));
    }

    @Test
    void continuityFamilyAcceptsBothForgeImplementationsOnly() {
        assertEquals(
                List.of(
                        "3.0.0+1.20.1.forge",
                        "3.0.01.20.1.forge",
                        "0.1.1+1.20.1.forge.build.4"),
                ForgeEngineRegistry.acceptedVersions("continuity"));
        assertTrue(ForgeEngineRegistry.acceptsVersion(
                "continuity", "3.0.0+1.20.1.forge"));
        assertTrue(ForgeEngineRegistry.acceptsVersion(
                "continuity", "3.0.01.20.1.forge"));
        assertTrue(ForgeEngineRegistry.acceptsVersion(
                "continuity", "0.1.1+1.20.1.forge.build.4"));
        assertFalse(ForgeEngineRegistry.acceptsVersion("continuity", "3.0.1"));
    }

    @Test
    void ctmPinMatchesForge1201RuntimeArtifact() {
        assertEquals(
                "1.20.1-1.1.10",
                ForgeEngineRegistry.expectedVersion("ctm"));
    }

    @Test
    void fusionPinStaysOneThreeTwelve() {
        assertEquals(
                "1.3.12",
                ForgeEngineRegistry.expectedVersion("fusion"));
    }

    @Test
    void athenaPinMatchesForge1201RuntimeArtifact() {
        assertEquals(
                "3.1.2",
                ForgeEngineRegistry.expectedVersion("athena"));
    }
}
