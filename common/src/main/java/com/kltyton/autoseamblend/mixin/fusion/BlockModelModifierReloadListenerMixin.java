package com.kltyton.autoseamblend.mixin.fusion;

import com.kltyton.autoseamblend.compat.fusion.runtime.FusionModifierLifecycleHooks;
import com.supermartijn642.fusion.model.modifiers.block.BlockModelModifierReloadListener;
import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 中文：统一捕获 Fusion 原生 parser 扫描与接受生命周期。 / English: Uniformly captures Fusion native-parser scan and acceptance lifecycles. */
@Mixin(value = BlockModelModifierReloadListener.class, remap = false)
public abstract class BlockModelModifierReloadListenerMixin {
    @Unique
    private static final FileToIdConverter AUTOSEAMBLEND_ID_CONVERTER =
            FileToIdConverter.json("fusion/model_modifiers/blocks");

    @Shadow
    @Final
    private Map<BlockState, List<?>> modifiers;

    @Inject(
            method = "reload(Lnet/minecraft/server/packs/resources/ResourceManager;)V",
            at = @At("HEAD"),
            remap = false)
    private void autoseamblend$captureWinningResources(
            ResourceManager resourceManager,
            CallbackInfo callback) {
        FusionModifierLifecycleHooks.begin(
                resourceManager,
                AUTOSEAMBLEND_ID_CONVERTER.listMatchingResources(resourceManager));
    }

    @Inject(
            method = "applyModelModifiers(Lnet/minecraft/client/resources/model/ModelBakery$BakingResult;Lnet/minecraft/client/resources/model/ModelBakery$ModelBakerImpl;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Map;clear()V",
                    shift = At.Shift.BEFORE,
                    remap = false),
            remap = false)
    private void autoseamblend$publishAcceptedDocuments(
            ModelBakery.BakingResult bakingResult,
            @Coerce Object baker,
            CallbackInfo callback) {
        FusionModifierLifecycleHooks.publish(bakingResult, modifiers);
    }
}
