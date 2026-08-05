package com.kltyton.autoseamblend.mixin.continuity;

import me.pepperbell.continuity.client.processor.overlay.StandardOverlayQuadProcessor;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 中文：读取 Continuity 已选择的 overlay 标记精灵。
 * English: Reads Continuity's selected overlay marker sprites.
 */
@Mixin(value = StandardOverlayQuadProcessor.SpriteCollector.class, remap = false)
public interface SpriteCollectorAccessor {
    @Accessor(value = "sprites", remap = false)
    TextureAtlasSprite[] autoseamblend$sprites();

    @Accessor(value = "spriteAmount", remap = false)
    int autoseamblend$spriteAmount();
}
