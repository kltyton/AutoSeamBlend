package com.kltyton.autoseamblend.neoforge.compat.athena.mixin;

import earth.terrarium.athena.api.client.models.AthenaModelAttributes;
import earth.terrarium.athena.impl.client.models.ConnectedBlockModel;
import earth.terrarium.athena.impl.client.models.ctm.ConnectedTextureMap;
import java.util.function.BiPredicate;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 中文：精确暴露 Athena 4.0.6 通用模型的原生连接合同。
 *
 * English: Precisely exposes Athena 4.0.6's native connection contract.
 */
@Mixin(ConnectedBlockModel.class)
public interface ConnectedBlockModelAccessor {
    @Accessor("materials")
    ConnectedTextureMap autoseamblend$getTextures();

    @Accessor("connectTo")
    BiPredicate<BlockState, BlockState> autoseamblend$getConnectTo();

    @Accessor("attributes")
    AthenaModelAttributes autoseamblend$getAttributes();
}
