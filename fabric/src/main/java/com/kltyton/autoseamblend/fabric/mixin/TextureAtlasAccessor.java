package com.kltyton.autoseamblend.fabric.mixin;

import java.util.Map;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 中文：暴露方块 Atlas 的精灵表，供预览场景按 UV 解析精确精灵。
 * English: Exposes the block atlas sprite table so the preview scene can
 * resolve exact sprites by UV.
 */
@Mixin(TextureAtlas.class)
public interface TextureAtlasAccessor {
    @Accessor("texturesByName")
    Map<ResourceLocation, TextureAtlasSprite>
            autoseamblend$texturesByName();
}
