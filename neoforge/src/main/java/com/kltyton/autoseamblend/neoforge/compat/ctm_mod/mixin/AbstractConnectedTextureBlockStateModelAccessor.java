package com.kltyton.autoseamblend.neoforge.compat.ctm_mod.mixin;

import io.github.chiselteam.ctm.client.AbstractConnectedTextureBlockStateModel;
import java.util.Map;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 中文：查询所有权所需的精确版本只读 CTM Lib 模型元数据。 / English: Exact-version read-only CTM Lib model metadata needed for query ownership. */
@Mixin(
        value = AbstractConnectedTextureBlockStateModel.class,
        remap = false)
public interface AbstractConnectedTextureBlockStateModelAccessor {
    @Accessor(value = "baseQuads", remap = false)
    Map<Direction, BakedQuad[]> autoseamblend$baseQuads();
}
