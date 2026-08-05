package com.kltyton.autoseamblend.mixin.minecraft;

import com.kltyton.autoseamblend.texture.atlas.InitialAtlasPreparationHooks;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 中文：在 AtlasManager 开始枚举来源前完成不可变生成精灵规划。
 * English: Completes immutable generated-sprite planning before AtlasManager enumerates sources.
 */
@Mixin(AtlasManager.class)
public abstract class AtlasManagerInitialPreparationMixin {
    @Unique
    private boolean autoseamblend$preparedReentry;

    @Inject(method = "reload", at = @At("HEAD"), cancellable = true)
    private void autoseamblend$prepareGeneratedSprites(
            PreparableReloadListener.SharedState currentReload,
            Executor taskExecutor,
            PreparableReloadListener.PreparationBarrier preparationBarrier,
            Executor reloadExecutor,
            CallbackInfoReturnable<CompletableFuture<Void>> callback) {
        if (autoseamblend$preparedReentry) {
            autoseamblend$preparedReentry = false;
            return;
        }
        callback.setReturnValue(
                InitialAtlasPreparationHooks.prepare(
                                currentReload.resourceManager(),
                                taskExecutor)
                        .thenCompose(ignored -> {
                            autoseamblend$preparedReentry = true;
                            return ((AtlasManager) (Object) this)
                                    .reload(
                                            currentReload,
                                            taskExecutor,
                                            preparationBarrier,
                                            reloadExecutor);
                        }));
    }
}
