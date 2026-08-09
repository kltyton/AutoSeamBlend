package com.kltyton.autoseamblend.neoforge.compat.ctm_mod.mixin;

import com.google.common.collect.ListMultimap;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import team.chisel.ctm.client.model.AbstractCTMBakedModel;

/**
 * 中文：查询所有权所需的精确版本只读 CTM Lib 模型元数据。
 *
 * English: Exact-version read-only CTM Lib model metadata needed for query ownership.
 */
@Mixin(AbstractCTMBakedModel.class)
public interface AbstractConnectedTextureBlockStateModelAccessor {
    @Accessor("genQuads")
    List<BakedQuad> autoseamblend$genQuads();

    @Accessor("faceQuads")
    ListMultimap<Direction, BakedQuad> autoseamblend$faceQuads();
}
