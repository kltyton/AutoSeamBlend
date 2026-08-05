package com.kltyton.autoseamblend.mixin.continuity;

import com.kltyton.autoseamblend.compat.continuity.document.ContinuityPropertiesCaptureBridge;
import com.kltyton.autoseamblend.compat.continuity.document.ContinuityPropertiesCaptureHooks;
import com.kltyton.autoseamblend.compat.continuity.document.ContinuityPropertiesExtensionCarrier;
import com.kltyton.autoseamblend.compat.continuity.document.ContinuityPropertiesExtensionState;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import java.util.List;
import java.util.Properties;
import me.pepperbell.continuity.client.properties.BaseCtmProperties;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 中文：统一附加 Continuity 作者扩展、原生槽位和 Loader 捕获结果。
 * English: Uniformly attaches Continuity author extensions, native slots, and Loader capture results.
 */
@Mixin(value = BaseCtmProperties.class, remap = false)
public abstract class BaseCtmPropertiesExtensionMixin
        implements ContinuityPropertiesExtensionCarrier {
    @Unique
    private ContinuityPropertiesExtensionState autoseamblend$extensionState =
            new ContinuityPropertiesExtensionState();
    @Unique
    private ResourceManager autoseamblend$resourceManager;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void autoseamblend$captureAuthorExtension(
            Properties properties,
            Identifier resourceId,
            PackResources pack,
            int packPriority,
            ResourceManager resourceManager,
            String method,
            CallbackInfo callback) {
        ContinuityPropertiesCaptureBridge.captureAuthorState(
                autoseamblend$extensionState,
                ContinuityPropertiesCaptureHooks.captureDocument(pack, resourceId));
        autoseamblend$resourceManager = resourceManager;
    }

    @Inject(method = "init", at = @At("RETURN"), remap = false)
    private void autoseamblend$captureNativeSlots(CallbackInfo callback) {
        BaseCtmProperties properties = (BaseCtmProperties) (Object) this;
        List<NativeSlot> nativeSlots = ContinuityPropertiesCaptureBridge.captureNativeSlots(
                autoseamblend$extensionState,
                properties,
                autoseamblend$resourceManager);
        ContinuityPropertiesCaptureHooks.nativeSlotsCaptured(
                autoseamblend$resourceManager,
                properties.getResourceId(),
                nativeSlots);
    }

    @Override
    public ContinuityPropertiesExtensionState autoseamblend$extensionState() {
        return autoseamblend$extensionState;
    }
}
