package com.kltyton.autoseamblend.fabric.compat.athena.mixin;

import earth.terrarium.athena.api.client.fabric.AthenaBakedModel;
import earth.terrarium.athena.api.client.models.AthenaBlockModel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.resources.model.sprite.Material;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 中文：精确暴露 Athena 已烘焙模型与材质，用于逐 Quad 识别精确源精灵。
 * English: Precisely exposes Athena's baked model and materials for exact
 * per-quad source-sprite identification.
 */
@Mixin(AthenaBakedModel.class)
public interface AthenaBakedModelAccessor {
    @Accessor("model")
    AthenaBlockModel autoseamblend$getModel();

    @Accessor("materials")
    Int2ObjectMap<Material.Baked> autoseamblend$getMaterials();
}
