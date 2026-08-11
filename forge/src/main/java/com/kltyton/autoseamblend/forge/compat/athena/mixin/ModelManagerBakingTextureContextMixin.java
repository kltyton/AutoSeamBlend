package com.kltyton.autoseamblend.forge.compat.athena.mixin;

import com.kltyton.autoseamblend.forge.compat.athena.runtime.ForgeModelBakeTextureContext;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.AtlasSet;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 中文：Forge 1.20.1 的 ModifyBakingResult 不携带 textureGetter；在事件分发的精确
 * 作用域内，把 ModelManager.loadModels 已持有的 StitchResult 解析器提供给 Athena。
 *
 * <p>English: Forge 1.20.1 does not expose a texture getter on ModifyBakingResult. During the
 * exact event-dispatch scope, supplies Athena with the StitchResult resolver already owned by
 * ModelManager.loadModels.
 */
@Mixin(ModelManager.class)
public abstract class ModelManagerBakingTextureContextMixin {

    @Redirect(
            method = "loadModels",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/client/ForgeHooksClient;onModifyBakingResult(Ljava/util/Map;Lnet/minecraft/client/resources/model/ModelBakery;)V",
                    remap = false))
    private void autoseamblend$provideCurrentStitchResult(
            Map<ResourceLocation, BakedModel> models,
            ModelBakery eventBakery,
            ProfilerFiller profiler,
            Map<ResourceLocation, AtlasSet.StitchResult> atlasPreparations,
            ModelBakery enclosingBakery) {
        Function<Material, TextureAtlasSprite> getter = material -> {
            AtlasSet.StitchResult stitch = atlasPreparations.get(
                    material.atlasLocation());
            return stitch == null
                    ? null
                    : stitch.getSprite(material.texture());
        };
        ForgeModelBakeTextureContext.runWith(
                getter,
                () -> ForgeHooksClient.onModifyBakingResult(
                        models,
                        eventBakery));
    }
}
