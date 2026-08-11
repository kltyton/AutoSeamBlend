package com.kltyton.autoseamblend.mixin.continuity;

import com.kltyton.autoseamblend.compat.continuity.document.ContinuityAcceptedHolderBridge;
import com.kltyton.autoseamblend.compat.continuity.document.ContinuityAcceptedHolderHooks;
import java.util.function.Function;
import me.pepperbell.continuity.api.client.CtmProperties;
import me.pepperbell.continuity.client.model.QuadProcessors;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 中文：在 Continuity 真正创建 holder 后统一发布接受证据。 / English: Uniformly publishes acceptance evidence after Continuity creates the real holder. */
@Mixin(
        targets = "me.pepperbell.continuity.client.resource.CtmPropertiesLoader$LoadingContainer",
        remap = false)
public abstract class ContinuityLoadingContainerMixin {
    @Shadow
    @Final
    private CtmProperties properties;

    @Inject(
            method = "toProcessorHolder(Ljava/util/function/Function;)Lme/pepperbell/continuity/client/model/QuadProcessors$ProcessorHolder;",
            at = @At("RETURN"),
            remap = false)
    private void autoseamblend$captureAcceptedHolder(
            Function<ResourceLocation, TextureAtlasSprite> spriteGetter,
            CallbackInfoReturnable<QuadProcessors.ProcessorHolder> callback) {
        ContinuityAcceptedHolderBridge.ifBaseProperties(
                properties,
                base -> ContinuityAcceptedHolderHooks.accepted(
                        base,
                        callback.getReturnValue().processor(),
                        spriteGetter));
    }
}
