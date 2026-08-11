package com.kltyton.autoseamblend.fabric.command;

import com.kltyton.autoseamblend.authoring.export.ManagedExportService;
import com.kltyton.autoseamblend.authoring.export.SystemExportDestinationPicker;
import com.kltyton.autoseamblend.export.managed.ManagedExportProfile;
import com.kltyton.autoseamblend.export.managed.ManagedExportRequest;
import com.kltyton.autoseamblend.fabric.authoring.export.FabricExportMetadata;
import com.kltyton.autoseamblend.fabric.engine.registry.FabricEngineRegistry;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 中文：一键 baked 资源包导出；目标文件夹选择使用系统选择器，不打开 Minecraft Screen。
 * English: One-click baked resource-pack export using the system folder picker
 * without opening a Minecraft Screen.
 */
public final class FabricExportCommands {
    private FabricExportCommands() {}

    public static LiteralArgumentBuilder<
                    FabricClientCommandSource>
            command() {
        return ClientCommandManager.literal("export")
                .executes(context ->
                        chooseDestination(
                                context.getSource()));
    }

    private static int chooseDestination(
            FabricClientCommandSource source) {
        Minecraft minecraft = Minecraft.getInstance();
        source.sendFeedback(
                Component.translatable(
                        "command.autoseamblend.export_picker_opened"));
        new SystemExportDestinationPicker(minecraft)
                .choose()
                .completion()
                .whenComplete((selected, failed) ->
                        minecraft.execute(() -> {
                            if (failed != null) {
                                source.sendError(
                                        Component.translatable(
                                                "command.autoseamblend.export_rejected",
                                                detail(failed)));
                                return;
                            }
                            if (selected == null
                                    || selected.isEmpty()) {
                                source.sendFeedback(
                                        Component.translatable(
                                                "command.autoseamblend.export_cancelled"));
                                return;
                            }
                            export(
                                    source,
                                    selected.orElseThrow());
                        }));
        return 1;
    }

    private static void export(
            FabricClientCommandSource source,
            Path parent) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Path destination =
                    parent.toAbsolutePath()
                            .normalize();
            source.sendFeedback(
                    Component.translatable(
                            "command.autoseamblend.export_route",
                            destination.toString()));
            ManagedExportRequest request =
                    new ManagedExportRequest(
                            ManagedExportProfile.BAKED,
                            destination,
                            false,
                            false);
            ManagedExportService.ExportHandle handle =
                    ManagedExportService.instance(
                                    minecraft,
                                    FabricEngineRegistry.RUNTIME
                                            .current(),
                                    FabricExportMetadata::metadata)
                            .exportConfigured(request);
            source.sendFeedback(
                    Component.translatable(
                            "command.autoseamblend.export_queued"));
            handle.future()
                    .whenComplete((result, failed) ->
                            minecraft.execute(() -> {
                                if (failed != null) {
                                    source.sendError(
                                            Component.translatable(
                                                    "command.autoseamblend.export_failed",
                                                    detail(failed)));
                                    return;
                                }
                                source.sendFeedback(
                                        Component.translatable(
                                                "command.autoseamblend.export_complete",
                                                result.destination()
                                                        .toString()));
                            }));
        } catch (RuntimeException exception) {
            source.sendError(
                    Component.translatable(
                            "command.autoseamblend.export_rejected",
                            detail(exception)));
        }
    }

    private static String detail(Throwable failed) {
        Throwable current = failed;
        while ((current
                                instanceof java.util.concurrent
                                        .CompletionException
                        || current
                                instanceof java.util.concurrent
                                        .ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null
                        || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }
}
