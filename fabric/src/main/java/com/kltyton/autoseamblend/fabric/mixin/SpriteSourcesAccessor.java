package com.kltyton.autoseamblend.fabric.mixin;

import com.google.common.collect.BiMap;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 中文：1.20.1 的 SpriteSources.TYPES 是私有 BiMap 且 Fabric API 0.92.11 没有公开的
 * AtlasSource 注册表；此访问器让 AutoSeamBlend 把生成精灵源类型注册进原版调度表。
 *
 * English: 1.20.1 SpriteSources.TYPES is a private BiMap and Fabric API 0.92.11 exposes no
 * public atlas-source registry; this accessor registers AutoSeamBlend's generated sprite
 * source type in the vanilla dispatch table.
 */
@Mixin(SpriteSources.class)
public interface SpriteSourcesAccessor {
    @Accessor("TYPES")
    static BiMap<ResourceLocation, SpriteSourceType> autoseamblend$types() {
        throw new AssertionError("mixin accessor was not transformed");
    }
}
