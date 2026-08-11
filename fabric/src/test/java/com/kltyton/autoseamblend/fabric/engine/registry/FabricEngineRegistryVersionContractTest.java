package com.kltyton.autoseamblend.fabric.engine.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 中文：锁定 Fabric 引擎版本门对 1.20.1 运行时产物的精确期望版本。三个可选引擎的
 * registry pin 必须与已裁决的运行时 JAR 元数据一致（continuity 3.0.0+1.20.1、fusion
 * 1.3.12、athena 3.1.2）；否则 EngineRegistryBuilder 严格相等门返回 INVALID_VERSION、
 * linkableEngineIds 为空，引擎生命周期与 GUI 工作台不激活。
 *
 * <p>English: Locks the Fabric engine registry version gate to the adjudicated 1.20.1
 * runtime artifacts. The pins must equal the runtime JAR metadata; otherwise the strict
 * equality gate in EngineRegistryBuilder returns INVALID_VERSION, linkableEngineIds stays
 * empty, and engine lifecycles plus the GUI workbench never activate.
 */
class FabricEngineRegistryVersionContractTest {
    @Test
    void continuityPinMatchesFabric1201RuntimeArtifact() {
        assertEquals(
                "3.0.0+1.20.1",
                FabricEngineRegistry.expectedVersion("continuity"),
                "continuity runtime jar metadata is 3.0.0+1.20.1; a stale 3.0.0+1.21 pin "
                        + "rejects the installed engine and disables connected textures");
    }

    @Test
    void fusionPinStaysOneThreeTwelve() {
        assertEquals(
                "1.3.12",
                FabricEngineRegistry.expectedVersion("fusion"),
                "fusion runtime jar metadata is 1.3.12; the pin must not drift to the "
                        + "artifact filename 1.3.12-fabric-mc1.20.1");
    }

    @Test
    void athenaPinMatchesFabric1201RuntimeArtifact() {
        assertEquals(
                "3.1.2",
                FabricEngineRegistry.expectedVersion("athena"),
                "athena runtime jar metadata is 3.1.2; a stale 4.0.6 pin rejects the "
                        + "installed engine");
    }
}
