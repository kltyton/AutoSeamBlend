package com.kltyton.autoseamblend.compat.athena.runtime;

import earth.terrarium.athena.api.client.utils.CtmState;
import java.util.Objects;

/**
 * 中文：集中 Athena 原生 CtmState 与项目无 Loader 状态值对象之间的 ABI 桥接。
 * English: Centralizes the ABI bridge between Athena's native CtmState and the loader-neutral
 * project state value object.
 *
 * <p>The common source set compiles against the Fabric Athena artifact because the Fabric and
 * NeoForge 4.0.6 artifacts expose the same CtmState constructor and accessors. Each loader keeps
 * supplying its own external Athena artifact at runtime; this bridge never bundles that engine.</p>
 */
public final class AthenaCtmStateBridge {
    private AthenaCtmStateBridge() {}

    /**
     * 中文：读取 Athena 八方向字段，不改变其原生字段顺序。
     * English: Reads Athena's eight directional fields without changing native field order.
     */
    public static AthenaConnectionState toCommon(CtmState state) {
        Objects.requireNonNull(state, "state");
        return AthenaConnectionState.of(
                state.up(),
                state.down(),
                state.left(),
                state.right(),
                state.upLeft(),
                state.upRight(),
                state.downLeft(),
                state.downRight());
    }

    /**
     * 中文：按 Athena 原生构造器顺序创建状态。
     * English: Creates a native state using Athena's constructor order.
     */
    public static CtmState toNative(AthenaConnectionState state) {
        Objects.requireNonNull(state, "state");
        return state.map(CtmState::new);
    }
}
