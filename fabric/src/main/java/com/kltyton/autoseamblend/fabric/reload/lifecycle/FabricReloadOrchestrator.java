package com.kltyton.autoseamblend.fabric.reload.lifecycle;

import com.kltyton.autoseamblend.engine.routing.ModelOwnershipRuntime;
import com.kltyton.autoseamblend.fabric.reload.contribution.FabricReloadContribution;
import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteAtlasResolution;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSetCatalog;
import com.kltyton.autoseamblend.texture.atlas.ResolvedSpriteCatalog;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：26.1.2 Fabric 客户端资源重载的唯一根 listener；在 MODELS 与各引擎原生
 * listener 之后于游戏 owning thread 上完成模型事实与生成精灵解析，并触发一次根发布。
 *
 * English: Sole root reload listener for the 26.1.2 Fabric client. It runs
 * after MODELS and every engine native listener on the game owning thread,
 * stages model facts and resolved sprites, and triggers one root publication.
 */
public final class FabricReloadOrchestrator {
    private static final Identifier LISTENER_ID =
            Identifier.fromNamespaceAndPath(
                    Constants.MOD_ID,
                    "root_generation");
    private static final Object LOCK = new Object();
    private static final Map<String, FabricReloadContribution> CONTRIBUTIONS =
            new LinkedHashMap<>();
    private static boolean registered;

    private FabricReloadOrchestrator() {}

    public static void registerContribution(
            FabricReloadContribution contribution) {
        Objects.requireNonNull(contribution, "contribution");
        synchronized (LOCK) {
            if (registered) {
                throw new IllegalStateException(
                        "Fabric reload contributions are already frozen");
            }
            FabricReloadContribution previous =
                    CONTRIBUTIONS.putIfAbsent(
                            contribution.engineId(),
                            contribution);
            if (previous != null) {
                throw new IllegalStateException(
                        "duplicate Fabric reload contribution: "
                                + contribution.engineId());
            }
        }
    }

    public static void register() {
        synchronized (LOCK) {
            if (registered) {
                return;
            }
            ResourceLoader loader =
                    ResourceLoader.get(
                            PackType.CLIENT_RESOURCES);
            loader.registerReloadListener(
                    LISTENER_ID,
                    new RootReloadListener());
            loader.addListenerOrdering(
                    ResourceReloaderKeys.AFTER_VANILLA,
                    LISTENER_ID);
            loader.addListenerOrdering(
                    ResourceReloaderKeys.Client.MODELS,
                    LISTENER_ID);
            CONTRIBUTIONS.values().stream()
                    .flatMap(contribution ->
                            contribution.nativeReloadListenerIds()
                                    .stream())
                    .distinct()
                    .forEach(nativeId ->
                            loader.addListenerOrdering(
                                    nativeId,
                                    LISTENER_ID));
            registered = true;
        }
    }

    public static List<FabricReloadContribution> contributions() {
        synchronized (LOCK) {
            return List.copyOf(
                    CONTRIBUTIONS.values());
        }
    }

    private static final class RootReloadListener
            implements PreparableReloadListener {
        @Override
        public CompletableFuture<Void> reload(
                SharedState state,
                Executor taskExecutor,
                PreparationBarrier preparationBarrier,
                Executor reloadExecutor) {
            return CompletableFuture.completedFuture(null)
                    .thenCompose(ignored ->
                            preparationBarrier.wait(null))
                    .thenCompose(ignored ->
                            CompletableFuture.runAsync(
                                    FabricReloadOrchestrator::apply,
                                    reloadExecutor));
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
        Map<BlockState, BlockStateModel> models;
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
        for (FabricReloadContribution contribution
                : contributions()) {
            contribution.onPublished(
                    0,
                    generation);
        }
    }

    private static Map<BlockState, BlockStateModel> captureModels() {
        LinkedHashMap<BlockState, BlockStateModel> models =
                new LinkedHashMap<>(
                        FabricModelCapture
                                .latestBaseModels());
        return models;
    }

    private static void resolveSprites(long generation) {
        try {
            GeneratedSpriteSetCatalog.Snapshot catalog =
                    ReloadPublication.atlasCatalog();
            ResolvedSpriteCatalog resolved =
                    GeneratedSpriteAtlasResolution.resolve(
                            net.minecraft.client.Minecraft
                                    .getInstance()
                                    .getAtlasManager()
                                    .getAtlasOrThrow(
                                            net.minecraft.data.AtlasIds
                                                    .BLOCKS),
                            catalog);
            ReloadPublication.stageResolvedSprites(
                    resolved);
            Constants.LOG.info(
                    "Resolved AutoSeamBlend generated state sprites: generation={}, sets={}",
                    catalog.generation(),
                    resolved.spritesByKey().size());
        } catch (RuntimeException exception) {
            ReloadPublication.discardPending(generation);
            Constants.LOG.error(
                    "AutoSeamBlend Fabric generated-sprite resolution rejected generation {}",
                    generation,
                    exception);
        }
    }
}
