package com.kltyton.autoseamblend.fabric.compat.athena.mixin;

import earth.terrarium.athena.impl.client.models.ConnectedBlockModel;
import java.util.function.BiPredicate;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 中文：Athena 3.1.2 ConnectedBlockModel.connectTo 私有字段访问器。 / English: Athena 3.1.2 ConnectedBlockModel.connectTo private-field accessor. */
@Mixin(ConnectedBlockModel.class)
public interface ConnectedBlockModelAccessor {
    @Accessor("connectTo")
    BiPredicate<BlockState, BlockState> autoseamblend$getConnectTo();
}
