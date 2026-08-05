package com.kltyton.autoseamblend.mixin.continuity;

import me.pepperbell.continuity.client.processor.AbstractQuadProcessor;
import me.pepperbell.continuity.client.processor.ProcessingPredicate;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 中文：读取锁定 Continuity 处理器已接受的原生查询状态。 / English: Reads native query state retained by the locked Continuity processor. */
@Mixin(value = AbstractQuadProcessor.class, remap = false)
public interface AbstractQuadProcessorAccessor {
    @Accessor(value = "processingPredicate", remap = false)
    ProcessingPredicate autoseamblend$processingPredicate();

    @Accessor(value = "sprites", remap = false)
    TextureAtlasSprite[] autoseamblend$sprites();
}
