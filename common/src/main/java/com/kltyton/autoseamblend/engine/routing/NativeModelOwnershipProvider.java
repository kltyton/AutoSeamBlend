package com.kltyton.autoseamblend.engine.routing;

import com.kltyton.autoseamblend.engine.EngineFamily;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;

/** 中文：不依赖第三方类型的探针，仅在每个引擎的 compat 包中实现。 / English: Third-party-free probe implemented only inside each engine compat package. */
public interface NativeModelOwnershipProvider {
    String engineId();

    EngineFamily family();

    boolean owns(BlockStateModel model);

    /** 中文：为指定根代次启动一次查询级原生所有权捕获。 / English: Starts one query-level native ownership capture for the specified root generation. */
    default void beginCapture(long generation) {}

    /** 中文：捕获一个方块状态所接受的精确原生模型。 / English: Captures the exact native model accepted for one block state. */
    default void capture(
            BlockState state,
            BlockStateModel model) {}

    /** 中文：冻结完整候选，但仍不对查询线程可见。 / English: Freezes the complete candidate while keeping it invisible to query threads. */
    default void endCapture() {}

    /** 中文：丢弃不完整或未提交候选，同时保留当前活动查询事实。 / English: Discards an incomplete or uncommitted candidate while retaining current active query facts. */
    default void abortCapture(long generation) {}
}
