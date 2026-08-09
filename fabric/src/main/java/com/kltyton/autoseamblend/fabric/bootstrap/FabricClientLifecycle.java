package com.kltyton.autoseamblend.fabric.bootstrap;

import com.kltyton.autoseamblend.engine.registry.EngineRegistryRuntimeState;
import com.kltyton.autoseamblend.fabric.command.FabricClientCommands;
import com.kltyton.autoseamblend.fabric.frontend.uilib.entry.UilibWorkbenchEntry;
import com.kltyton.autoseamblend.fabric.reload.lifecycle.FabricReloadOrchestrator;
import com.kltyton.autoseamblend.fabric.runtime.texture.atlas.FabricSpriteSourceRegistration;
import com.kltyton.autoseamblend.selection.compiled.SelectorGenerationLifecycle;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * 中文：注册 PLAY 注册表生命周期、命令、按键提示与唯一资源 reload listener。
 * English: Registers PLAY-registry lifecycles, commands, key hints, and the
 * sole resource-reload listener.
 */
public final class FabricClientLifecycle {
    private FabricClientLifecycle() {}

    public static void register(
            EngineRegistryRuntimeState engines) {
        FabricSpriteSourceRegistration.register();
        FabricClientCommands.register();
        UilibWorkbenchEntry.registerKeyMapping();
        FabricReloadOrchestrator.register();
        ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> {
                    SelectorGenerationLifecycle
                            .bindPlayRegistries(
                                    handler.registryAccess());
                    if (client.player != null) {
                        UilibWorkbenchEntry.showHintOnce(
                                client.player);
                    }
                });
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) ->
                        SelectorGenerationLifecycle
                                .unbindPlayRegistries());
    }
}
