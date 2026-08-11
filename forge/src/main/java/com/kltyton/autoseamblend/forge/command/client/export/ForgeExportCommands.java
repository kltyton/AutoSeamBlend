package com.kltyton.autoseamblend.forge.command.client.export;

import com.kltyton.autoseamblend.export.managed.ManagedExportProfile;
import com.kltyton.autoseamblend.export.managed.ManagedExportRequest;
import com.kltyton.autoseamblend.authoring.export.ManagedExportService;
import com.kltyton.autoseamblend.authoring.export.SystemExportDestinationPicker;
import com.kltyton.autoseamblend.forge.authoring.export.ForgeExportMetadata;
import com.kltyton.autoseamblend.forge.engine.registry.ForgeEngineRegistry;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** 中文：一键 baked 资源包导出，路由到拥有查询的原生引擎。 / English: One-click baked resource-pack export routed to the owning native engines. */
public final class ForgeExportCommands {
    private ForgeExportCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack>
            command() {
        return Commands.literal("export")
                .executes(context ->
                        chooseDestination(
                                context.getSource()));
    }

    private static int chooseDestination(
            CommandSourceStack source) {
        Minecraft minecraft =
                Minecraft.getInstance();
        success(
                source,
                "export_picker_opened");
        new SystemExportDestinationPicker(minecraft)
                .choose()
                .completion()
                .whenComplete((selected, failed) ->
                        minecraft.execute(() -> {
                            if (failed != null) {
                                failure(
                                        source,
                                        "export_rejected",
                                        detail(failed));
                                return;
                            }
                            if (selected == null
                                    || selected.isEmpty()) {
                                success(
                                        source,
                                        "export_cancelled");
                                return;
                            }
                            export(
                                    source,
                                    selected.orElseThrow());
                        }));
        return 1;
    }

    private static void export(
            CommandSourceStack source,
            Path parent) {
        try {
            Minecraft minecraft =
                    Minecraft.getInstance();
            Path destination =
                    parent.toAbsolutePath()
                            .normalize();
            success(
                    source,
                    "export_route",
                    destination.toString());
            ManagedExportRequest request =
                    new ManagedExportRequest(
                            ManagedExportProfile.BAKED,
                            destination,
                            false,
                            false);
            ManagedExportService.ExportHandle
                    handle =
                            ManagedExportService
                                    .instance(
                                            minecraft,
                                            ForgeEngineRegistry.RUNTIME.current(),
                                            ForgeExportMetadata::metadata)
                                    .exportConfigured(
                                            request);
            success(
                    source,
                    "export_queued");
            handle.future()
                    .whenComplete((result, failed) ->
                            minecraft.execute(() -> {
                                if (failed != null) {
                                    failure(
                                            source,
                                            "export_failed",
                                            detail(failed));
                                    return;
                                }
                                success(
                                        source,
                                        "export_complete",
                                        result.destination()
                                                .toString(),
                                        routes(result));
                            }));
        } catch (RuntimeException exception) {
            failure(
                    source,
                    "export_rejected",
                    detail(exception));
        }
    }

    private static String routes(
            ManagedExportService.ExportResult
                    result) {
        return String.join(
                ", ",
                result.partitions()
                        .keySet()
                        .stream()
                        .map(engineId ->
                                engineId
                                        + '/'
                                        + ForgeEngineRegistry.RUNTIME
                                                .family(
                                                        engineId)
                                                .formatId())
                        .toList());
    }

    private static String detail(
            Throwable failed) {
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
        String message =
                current.getMessage();
        return message == null
                        || message.isBlank()
                ? current.getClass()
                        .getSimpleName()
                : message;
    }

    private static void success(
            CommandSourceStack source,
            String key,
            Object... arguments) {
        source.sendSuccess(
                () -> Component.translatable(
                        "command.autoseamblend."
                                + key,
                        arguments),
                false);
    }

    private static void failure(
            CommandSourceStack source,
            String key,
            Object... arguments) {
        source.sendFailure(
                Component.translatable(
                        "command.autoseamblend."
                                + key,
                        arguments));
    }
}
