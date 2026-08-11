package com.kltyton.autoseamblend.mixin.continuity;

import me.pepperbell.continuity.client.processor.overlay.StandardOverlayQuadProcessor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 中文：读取 Continuity 3.0.0 StandardOverlayQuadProcessor.OverlayEmitter 已选择的
 * overlay 标记精灵（sprites/spriteAmount 字段）。
 *
 * English: Reads the selected overlay marker sprites of Continuity 3.0.0's
 * StandardOverlayQuadProcessor.OverlayEmitter (sprites/spriteAmount fields).
 */
@Mixin(value = StandardOverlayQuadProcessor.OverlayEmitter.class, remap = false)
public interface OverlayEmitterAccessor {
    @Accessor(value = "sprites", remap = false)
    TextureAtlasSprite[] autoseamblend$sprites();

    @Accessor(value = "spriteAmount", remap = false)
    int autoseamblend$spriteAmount();
}
