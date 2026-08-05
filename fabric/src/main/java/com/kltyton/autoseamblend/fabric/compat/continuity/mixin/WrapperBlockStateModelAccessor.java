package com.kltyton.autoseamblend.fabric.compat.continuity.mixin;

import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 中文：读取 Fabric API wrapper 链的直接被包装模型。
 * English: Reads the directly wrapped model in a Fabric API wrapper chain.
 */
@Mixin(value = WrapperBlockStateModel.class, remap = false)
public interface WrapperBlockStateModelAccessor {
    @Accessor(value = "wrapped", remap = false)
    BlockStateModel autoseamblend$wrapped();
}
