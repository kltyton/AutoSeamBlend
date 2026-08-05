package com.kltyton.autoseamblend.authoring.export;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * 中文：两端共用的非阻塞系统目录选择器；取消立即恢复调用方，迟到的原生结果被丢弃。
 * English: Shared nonblocking system folder picker. Cancellation immediately
 * restores the caller and discards a late native result.
 */
public final class SystemExportDestinationPicker implements ExportDestinationPort {
    private final Minecraft minecraft;

    public SystemExportDestinationPicker(Minecraft minecraft) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
    }

    @Override
    public Selection choose() {
        String title = Component.translatable(
                        "command.autoseamblend.export_choose_destination")
                .getString();
        String initialPath = minecraft.gameDirectory.toPath()
                .resolve("resourcepacks")
                .toAbsolutePath()
                .normalize()
                .toString();
        CompletableFuture<Optional<Path>> completion = new CompletableFuture<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        Thread.ofPlatform()
                .daemon()
                .name("AutoSeamBlend Export Folder Picker")
                .start(() -> {
                    try {
                        String selected = TinyFileDialogs.tinyfd_selectFolderDialog(title, initialPath);
                        if (cancelled.get()) return;
                        if (selected == null || selected.isBlank()) {
                            completion.complete(Optional.empty());
                            return;
                        }
                        completion.complete(Optional.of(ExportDestinationPathPolicy.availableDestination(
                                Path.of(selected).toAbsolutePath().normalize())));
                    } catch (RuntimeException | UnsatisfiedLinkError exception) {
                        if (!cancelled.get()) completion.completeExceptionally(exception);
                    }
                });
        return new Selection(completion, () -> {
            if (cancelled.compareAndSet(false, true)) {
                completion.complete(Optional.empty());
            }
        });
    }

}
