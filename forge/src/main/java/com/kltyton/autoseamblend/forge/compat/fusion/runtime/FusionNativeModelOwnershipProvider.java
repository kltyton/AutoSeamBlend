package com.kltyton.autoseamblend.forge.compat.fusion.runtime;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.routing.NativeModelOwnershipProvider;
import com.kltyton.autoseamblend.compat.fusion.runtime.FusionNativeModelOwnership;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;

/** 中文：仅在安装 Fusion 时注册的 Fusion 链接所有权探针。 / English: Fusion-linked ownership probe registered only when Fusion is installed. */
public enum FusionNativeModelOwnershipProvider
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
    public boolean owns(BakedModel model) {
        return FusionNativeModelOwnership.owns(model);
    }

    @Override
    public void beginCapture(
            long generation) {
        FusionNativeQueryOwnership.INSTANCE.beginModelCapture(
                generation);
    }

    @Override
    public void capture(
            BlockState state,
            BakedModel model) {
        FusionNativeQueryOwnership.INSTANCE.captureModel(
                state,
                model);
    }

    @Override
    public void endCapture() {
        FusionNativeQueryOwnership.INSTANCE.endModelCapture();
    }

    @Override
    public void abortCapture(
            long generation) {
        FusionNativeQueryOwnership.INSTANCE.abortModelCapture(
                generation);
    }
}
