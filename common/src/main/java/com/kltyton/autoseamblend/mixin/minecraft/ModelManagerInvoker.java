package com.kltyton.autoseamblend.mixin.minecraft;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 中文：对 1.20.1 原版异步方块模型/方块状态加载器的精确版本访问。
 * English: Exact-version access to vanilla's asynchronous block-model and
 * block-state loaders.
 */
@Mixin(ModelManager.class)
public interface ModelManagerInvoker {
    @Invoker("loadBlockModels")
    static CompletableFuture<Map<ResourceLocation, BlockModel>> autoseamblend$loadBlockModels(
            ResourceManager resourceManager,
            Executor executor) {
        throw new AssertionError("mixin invoker was not transformed");
    }

    @Invoker("loadBlockStates")
    static CompletableFuture<Map<ResourceLocation, List<ModelBakery.LoadedJson>>>
            autoseamblend$loadBlockStates(
                    ResourceManager resourceManager,
                    Executor executor) {
        throw new AssertionError("mixin invoker was not transformed");
    }
}
