package com.kltyton.autoseamblend.engine.routing.query;

import java.util.Objects;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：引擎摘要查询缓存键，保留方块状态身份与 Continuity 精确所有权标志。
 *
 * <p>English: Engine-summary cache key retaining block-state identity and the Continuity exact-
 * ownership flag.
 */
public record EngineSummaryKey(
        BlockState state,
        boolean continuityNativeExact) {
    public EngineSummaryKey {
        Objects.requireNonNull(state, "state");
    }
}
