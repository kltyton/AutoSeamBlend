package com.kltyton.autoseamblend.mixin.minecraft;

import com.kltyton.autoseamblend.texture.atlas.InitialAtlasPreparationHooks;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 中文：在方块 Atlas 开始枚举来源前完成不可变生成精灵规划。
 *
 * English: Completes immutable generated-sprite planning before the block atlas enumerates
 * sources. 1.20.1 hooks SpriteLoader.loadAndStitch (the Mojmap name of the Yarn method
 * SpriteLoader.load); the block-atlas check uses the SpriteLoader instance's own bound
 * atlas location because AtlasSet passes the atlas INFO location to loadAndStitch.
 */
@Mixin(SpriteLoader.class)
public abstract class AtlasManagerInitialPreparationMixin {
    @Shadow
    private ResourceLocation location;

    @Unique
    private boolean autoseamblend$preparedReentry;

    @Inject(
            method = "loadAndStitch(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/ResourceLocation;ILjava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"),
            cancellable = true)
    private void autoseamblend$prepareGeneratedSprites(
            ResourceManager resourceManager,
            ResourceLocation location,
            int mipLevel,
            Executor taskExecutor,
            CallbackInfoReturnable<CompletableFuture<SpriteLoader.Preparations>> callback) {
        if (autoseamblend$preparedReentry) {
            autoseamblend$preparedReentry = false;
            return;
        }
        if (!TextureAtlas.LOCATION_BLOCKS.equals(this.location)) {
            return;
        }
        callback.setReturnValue(
                InitialAtlasPreparationHooks.prepare(
                                resourceManager,
                                taskExecutor)
                        .thenCompose(ignored -> {
                            autoseamblend$preparedReentry = true;
                            return ((SpriteLoader) (Object) this)
                                    .loadAndStitch(
                                            resourceManager,
                                            location,
                                            mipLevel,
                                            taskExecutor);
                        }));
    }
}
