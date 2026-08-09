package com.kltyton.autoseamblend.mixin.minecraft;

import com.kltyton.autoseamblend.texture.atlas.InitialAtlasPreparationHooks;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
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
 * sources (1.21.1 hooks SpriteLoader.loadAndStitch instead of 26.1's AtlasManager.reload).
 *
 * 中文：1.21.1 的 AtlasSet.scheduleLoad 传给 loadAndStitch 的 location 是 Atlas 信息位置
 * （方块 Atlas 为 "minecraft:blocks"），不是 Atlas 纹理位置（"minecraft:textures/atlas/blocks"），
 * 因此这里必须按 SpriteLoader 自身绑定的 Atlas 位置判断，而不是比较 location 参数。
 *
 * English: In 1.21.1, AtlasSet.scheduleLoad passes the atlas INFO location
 * ("minecraft:blocks" for the block atlas) to loadAndStitch, not the atlas texture
 * location ("minecraft:textures/atlas/blocks"), so the block-atlas check must use the
 * SpriteLoader instance's own bound atlas location instead of the location argument.
 */
@Mixin(SpriteLoader.class)
public abstract class AtlasManagerInitialPreparationMixin {
    @Shadow
    private ResourceLocation location;

    @Unique
    private boolean autoseamblend$preparedReentry;

    @Inject(
            method = "loadAndStitch(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/ResourceLocation;ILjava/util/concurrent/Executor;Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"),
            cancellable = true)
    private void autoseamblend$prepareGeneratedSprites(
            ResourceManager resourceManager,
            ResourceLocation location,
            int mipLevel,
            Executor taskExecutor,
            Collection<MetadataSectionSerializer<?>> sectionSerializers,
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
                                            taskExecutor,
                                            sectionSerializers);
                        }));
    }
}
