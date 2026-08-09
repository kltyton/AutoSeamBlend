package com.kltyton.autoseamblend.mixin.continuity;

import me.pepperbell.continuity.client.processor.ProcessingPredicate;
import me.pepperbell.continuity.client.processor.simple.SimpleQuadProcessor;
import me.pepperbell.continuity.client.processor.simple.SpriteProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 中文：读取锁定 Continuity simple 处理器的原生查询状态。 / English: Reads native query state retained by the locked Continuity simple processor. */
@Mixin(value = SimpleQuadProcessor.class, remap = false)
public interface SimpleQuadProcessorAccessor {
    @Accessor(value = "processingPredicate", remap = false)
    ProcessingPredicate autoseamblend$processingPredicate();

    @Accessor(value = "spriteProvider", remap = false)
    SpriteProvider autoseamblend$spriteProvider();
}
