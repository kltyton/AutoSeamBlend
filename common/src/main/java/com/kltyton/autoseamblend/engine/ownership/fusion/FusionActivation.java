package com.kltyton.autoseamblend.engine.ownership.fusion;

import java.util.Objects;

import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：表示 Fusion 冻结的条件分支求值器；不依赖 Fusion API 或 Loader。
 * <p>
 * English: Loader-neutral evaluator for a condition branch frozen from Fusion model data.
 */
@FunctionalInterface
public interface FusionActivation {
    /** 中文：无条件激活分支。 / English: Activation that always accepts the branch. */
    FusionActivation ALWAYS = (level, pos, state) -> true;

    boolean test(BlockAndTintGetter level, BlockPos pos, BlockState state);

    /**
     * 中文：按原生短路顺序组合两个冻结条件。
     * <p>
     * English: Combines two frozen conditions with the native short-circuit order.
     */
    default FusionActivation and(FusionActivation other) {
        Objects.requireNonNull(other, "other");
        return (level, pos, state) -> test(level, pos, state)
                && other.test(level, pos, state);
    }
}
