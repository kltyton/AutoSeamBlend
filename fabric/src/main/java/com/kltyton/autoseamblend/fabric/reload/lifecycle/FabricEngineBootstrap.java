package com.kltyton.autoseamblend.fabric.reload.lifecycle;

import com.kltyton.autoseamblend.fabric.compat.athena.bootstrap.AthenaRuntimeBootstrap;
import com.kltyton.autoseamblend.fabric.compat.continuity.bootstrap.ContinuityBootstrap;
import com.kltyton.autoseamblend.fabric.compat.fusion.bootstrap.FusionBootstrap;
import java.util.Objects;

/**
 * 中文：已安装引擎的隔离 provider/bootstrap 注册门；只在无链接发现确认引擎存在后调用。
 * English: Isolated provider/bootstrap registration gate for installed engines;
 * invoked only after linkage-free discovery confirms the engine exists.
 */
public interface FabricEngineBootstrap {
    static FabricEngineBootstrap require(String engineId) {
        return switch (Objects.requireNonNull(
                engineId, "engineId")) {
            case "continuity" -> ContinuityBootstrap.INSTANCE;
            case "fusion" -> FusionBootstrap.INSTANCE;
            case "athena" -> AthenaRuntimeBootstrap.INSTANCE;
            default -> throw new IllegalStateException(
                    "unmapped Fabric engine id: " + engineId);
        };
    }

    void register();
}
