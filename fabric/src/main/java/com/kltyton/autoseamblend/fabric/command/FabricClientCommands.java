package com.kltyton.autoseamblend.fabric.command;

import com.kltyton.autoseamblend.engine.registry.EngineRegistryRuntimeState;
import com.kltyton.autoseamblend.fabric.engine.registry.FabricEngineRegistry;
import com.kltyton.autoseamblend.fabric.frontend.uilib.controller.FabricWorkbenchController;
import com.kltyton.autoseamblend.foundation.Constants;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/**
 * 中文：Fabric 客户端命令根；打开与 NeoForge 相同的 UILib 工作台，并提供一键 baked 导出命令。
 * English: Fabric client command root; it opens the same UILib workbench as
 * NeoForge and exposes the one-click baked export.
 */
public final class FabricClientCommands {
    private FabricClientCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, buildContext) ->
                        dispatcher.register(root()));
    }

    private static LiteralArgumentBuilder<
                    FabricClientCommandSource>
            root() {
        return ClientCommandManager.literal(Constants.COMMAND_ROOT)
                .executes(context ->
                        openWorkbench(context.getSource()))
                .then(FabricExportCommands.command());
    }

    private static int openWorkbench(
            FabricClientCommandSource source) {
        try {
            return FabricWorkbenchController.open();
        } catch (RuntimeException exception) {
            source.sendError(
                    Component.translatable(
                            "command.autoseamblend.workbench_failed",
                            exception.getClass()
                                    .getSimpleName()));
            return 0;
        }
    }
}
