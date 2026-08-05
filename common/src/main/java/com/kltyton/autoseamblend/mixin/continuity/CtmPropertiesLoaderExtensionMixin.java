package com.kltyton.autoseamblend.mixin.continuity;

import com.kltyton.autoseamblend.compat.continuity.document.ContinuityPropertiesLoaderBridge;
import java.util.Properties;
import me.pepperbell.continuity.api.client.CtmLoader;
import me.pepperbell.continuity.api.client.CtmProperties;
import me.pepperbell.continuity.client.resource.CtmPropertiesLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 中文：两端共用的 Continuity 原生 loader 执行副本入口。
 * English: Shared Continuity native-loader entry for the in-memory execution copy.
 */
@Mixin(value = CtmPropertiesLoader.class, remap = false)
public abstract class CtmPropertiesLoaderExtensionMixin {
    @Inject(
            method = "load(Ljava/util/Properties;Lnet/minecraft/resources/Identifier;Lnet/minecraft/server/packs/PackResources;I)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void autoseamblend$prepareExecutionProperties(
            Properties properties,
            Identifier resourceId,
            PackResources pack,
            int packPriority,
            CallbackInfo callback) {
        if (ContinuityPropertiesLoaderBridge.apply(
                properties,
                resourceId,
                pack,
                packPriority,
                this::autoseamblend$invokeNativeLoad)) {
            callback.cancel();
        }
    }

    @Invoker(value = "load", remap = false)
    protected abstract <T extends CtmProperties> void autoseamblend$invokeNativeLoad(
            CtmLoader<T> loader,
            Properties properties,
            Identifier resourceId,
            PackResources pack,
            int packPriority,
            String method);
}
