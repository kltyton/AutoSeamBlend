package com.kltyton.autoseamblend.fabric.compat.fusion.runtime;

import com.kltyton.autoseamblend.compat.fusion.runtime.FusionNativeModelOwnership;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.routing.NativeModelOwnershipProvider;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：仅在安装 Fusion 时注册的 Fusion 链接所有权探针。
 * English: Fusion-linked ownership probe registered only when Fusion is
 * installed.
 */
public enum FabricFusionNativeModelOwnershipProvider
        implements NativeModelOwnershipProvider {
    INSTANCE;

    @Override
    public String engineId() {
        return "fusion";
    }

    @Override
    public EngineFamily family() {
        return EngineFamily.FUSION;
    }

    @Override
    public boolean owns(BlockStateModel model) {
        return FusionNativeModelOwnership.owns(model);
    }

    @Override
    public void beginCapture(long generation) {
        FabricFusionNativeQueryOwnership.INSTANCE
                .beginModelCapture(generation);
    }

    @Override
    public void capture(
            BlockState state,
            BlockStateModel model) {
        FabricFusionNativeQueryOwnership.INSTANCE
                .captureModel(state, model);
    }

    @Override
    public void endCapture() {
        FabricFusionNativeQueryOwnership.INSTANCE
                .endModelCapture();
    }

    @Override
    public void abortCapture(long generation) {
        FabricFusionNativeQueryOwnership.INSTANCE
                .abortModelCapture(generation);
    }
}
