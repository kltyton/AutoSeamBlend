package com.kltyton.autoseamblend.mixin.continuity;

import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityProcessorListHooks;
import java.util.List;
import java.util.function.Function;
import me.pepperbell.continuity.client.model.QuadProcessors;
import me.pepperbell.continuity.client.resource.CtmPropertiesLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 中文：捕获 Continuity 已接受的处理器列表，并把 Loader 特定处理委托给已安装端口。
 * English: Captures Continuity's accepted processor list and delegates Loader handling to the installed port.
 */
@Mixin(value = CtmPropertiesLoader.LoadingResult.class, remap = false)
public abstract class ContinuityProcessorListMixin {
    @Inject(method = "createProcessorHolders", at = @At("HEAD"), remap = false)
    private void autoseamblend$beginOwnershipGeneration(
            Function<Identifier, TextureAtlasSprite> spriteGetter,
            CallbackInfoReturnable<List<QuadProcessors.ProcessorHolder>> callback) {
        ContinuityProcessorListHooks.begin();
    }

    @Inject(method = "createProcessorHolders", at = @At("RETURN"), remap = false)
    private void autoseamblend$completeProcessorList(
            Function<Identifier, TextureAtlasSprite> spriteGetter,
            CallbackInfoReturnable<List<QuadProcessors.ProcessorHolder>> callback) {
        ContinuityProcessorListHooks.complete(callback.getReturnValue());
    }
}
