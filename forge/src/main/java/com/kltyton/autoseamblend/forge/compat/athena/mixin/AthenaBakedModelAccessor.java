package com.kltyton.autoseamblend.forge.compat.athena.mixin;

import earth.terrarium.athena.api.client.models.AthenaBlockModel;
import earth.terrarium.athena.api.client.forge.AthenaBakedModel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 中文：用于向已接受 Athena 模型查询一个表面的精确版本只读模型数据。 / English: Exact-version read-only Athena model data used to ask its accepted model about one face. */
@Mixin(
        value = AthenaBakedModel.class,
        remap = false)
public interface AthenaBakedModelAccessor {
    @Accessor(value = "model", remap = false)
    AthenaBlockModel autoseamblend$model();

    @Accessor(value = "textures", remap = false)
    Int2ObjectMap<TextureAtlasSprite> autoseamblend$textures();
}
