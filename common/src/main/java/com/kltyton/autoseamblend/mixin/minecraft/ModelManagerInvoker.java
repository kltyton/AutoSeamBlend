package com.kltyton.autoseamblend.mixin.minecraft;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 中文：对原版异步方块模型加载器的精确版本访问。 / English: Exact-version access to vanilla's asynchronous block-model loader. */
@Mixin(ModelManager.class)
public interface ModelManagerInvoker {
    @Invoker("loadBlockModels")
    static CompletableFuture<Map<Identifier, UnbakedModel>> autoseamblend$loadBlockModels(
            ResourceManager resourceManager,
            Executor executor) {
        throw new AssertionError("mixin invoker was not transformed");
    }
}
