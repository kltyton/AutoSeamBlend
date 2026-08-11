package com.kltyton.autoseamblend.fabric.reload.lifecycle;

import com.kltyton.autoseamblend.engine.routing.ModelOwnershipRuntime;
import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteAtlasResolution;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSetCatalog;
import com.kltyton.autoseamblend.texture.atlas.ResolvedSpriteCatalog;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：1.20.1 Fabric 客户端资源重载的唯一根 listener；在 MODELS 之后于游戏
 * owning thread 上完成模型事实与生成精灵解析，并触发一次根发布。
 *
 * English: Sole root reload listener for the 1.20.1 Fabric client. It runs
 * after MODELS on the game owning thread, stages model facts and resolved
 * sprites, and triggers one root publication.
 */
public final class FabricReloadOrchestrator {
    private static final ResourceLocation LISTENER_ID =
            new ResourceLocation(
                    Constants.MOD_ID,
                    "root_generation");
    private static final Object LOCK = new Object();
    private static boolean registered;

    private FabricReloadOrchestrator() {}

    public static void register() {
        synchronized (LOCK) {
            if (registered) {
                return;
            }
            ResourceManagerHelper
                    .get(PackType.CLIENT_RESOURCES)
                    .registerReloadListener(
                            new RootReloadListener());
            registered = true;
        }
    }

    private static final class RootReloadListener
            implements IdentifiableResourceReloadListener {
        @Override
        public ResourceLocation getFabricId() {
            return LISTENER_ID;
        }

        @Override
        public java.util.Collection<ResourceLocation>
                getFabricDependencies() {
            java.util.LinkedHashSet<ResourceLocation>
                    dependencies =
                            new java.util.LinkedHashSet<>();
            dependencies.add(
                    ResourceReloadListenerKeys.MODELS);
            return dependencies;
        }

        @Override
        public CompletableFuture<Void> reload(
                PreparationBarrier barrier,
                ResourceManager resources,
                ProfilerFiller preparationsProfiler,
                ProfilerFiller reloadProfiler,
                Executor backgroundExecutor,
                Executor gameExecutor) {
            return CompletableFuture.completedFuture(null)
                    .thenCompose(ignored ->
                            barrier.wait(null))
                    .thenCompose(ignored ->
                            CompletableFuture.runAsync(
                                    FabricReloadOrchestrator::apply,
                                    gameExecutor));
        }
    }

    private static void apply() {
        ReloadPublication.Generation prepared =
                ReloadPublication.pendingPreparation()
                        .orElse(null);
        if (prepared == null) {
            return;
        }
        long generation = prepared.generation();
        Map<BlockState, BakedModel> models;
        try {
            models = captureModels();
        } catch (RuntimeException exception) {
            ReloadPublication.discardPending(generation);
            Constants.LOG.error(
                    "AutoSeamBlend Fabric model capture rejected generation {}",
                    generation,
                    exception);
            return;
        }
        ModelOwnershipRuntime.PreparedCapture ownership = null;
        try {
            ownership = ModelOwnershipRuntime.prepare(
                    models,
                    generation);
            MinecraftSurfaceCatalog.Snapshot surfaces =
                    MinecraftSurfaceCatalog.prepare(
                            models,
                            prepared.preparedMethods(),
                            generation);
            ReloadPublication.stageModelFacts(
                    ownership,
                    surfaces);
        } catch (RuntimeException exception) {
            if (ownership != null) {
                ModelOwnershipRuntime.abort(ownership);
            }
            ReloadPublication.discardPending(generation);
            Constants.LOG.error(
                    "AutoSeamBlend Fabric model-fact staging rejected generation {}",
                    generation,
                    exception);
            return;
        }
        resolveSprites(generation);
    }

    private static Map<BlockState, BakedModel> captureModels() {
        return new LinkedHashMap<>(
                FabricModelCapture
                        .latestBaseModels());
    }

    private static void resolveSprites(long generation) {
        try {
            GeneratedSpriteSetCatalog.Snapshot catalog =
                    ReloadPublication.atlasCatalog();
            ResolvedSpriteCatalog resolved =
                    GeneratedSpriteAtlasResolution.resolve(
                            Minecraft.getInstance()
                                    .getModelManager()
                                    .getAtlas(
                                            TextureAtlas
                                                    .LOCATION_BLOCKS),
                            catalog);
            ReloadPublication.stageResolvedSprites(
                    resolved);
        } catch (RuntimeException exception) {
            ReloadPublication.discardPending(generation);
            Constants.LOG.error(
                    "AutoSeamBlend Fabric generated-sprite resolution rejected generation {}",
                    generation,
                    exception);
        }
    }
}
