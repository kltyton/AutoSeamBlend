package com.kltyton.autoseamblend.neoforge.command.client;

import com.kltyton.autoseamblend.neoforge.command.client.export.NeoForgeExportCommands;
import com.kltyton.autoseamblend.neoforge.frontend.uilib.controller.UilibWorkbenchController;
import com.kltyton.autoseamblend.foundation.Constants;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** 中文：客户端命令根；writer、engine 和 format 在内部选择。 / English: Client command root; writer, engine, and format are selected internally. */
public final class NeoForgeClientCommands {
    private NeoForgeClientCommands() {}

    public static void register(
            RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal(Constants.COMMAND_ROOT)
                        .executes(context -> {
                            try {
                                return UilibWorkbenchController.open();
                            } catch (RuntimeException exception) {
                                context.getSource().sendFailure(
                                        Component.translatable(
                                                "command.autoseamblend.workbench_failed",
                                                exception.getClass()
                                                        .getSimpleName()));
                                return 0;
                            }
                        })
                        .then(NeoForgeExportCommands
                                .command()));
    }
}
