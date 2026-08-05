package com.kltyton.autoseamblend.authoring.storage;

import java.nio.file.Path;
import java.util.Objects;
import net.minecraft.client.Minecraft;

/** 中文：固定的持久创作工作区；构造它不会执行文件系统写入。 / English: Fixed persistent authoring workspace; constructing it performs no filesystem write. */
public record ManagedPackLayout(
        Path resourcePacksRoot,
        Path root) {
    public ManagedPackLayout {
        resourcePacksRoot = Objects.requireNonNull(
                        resourcePacksRoot,
                        "resourcePacksRoot")
                .toAbsolutePath()
                .normalize();
        root = Objects.requireNonNull(root, "root")
                .toAbsolutePath()
                .normalize();
        if (!Objects.equals(
                root.getParent(),
                resourcePacksRoot)) {
            throw new IllegalArgumentException(
                    "Managed pack must be a direct resourcepacks child");
        }
    }

    public static ManagedPackLayout current(
            Minecraft minecraft) {
        Path resourcePacks = Objects.requireNonNull(
                        minecraft,
                        "minecraft")
                .getResourcePackDirectory()
                .toAbsolutePath()
                .normalize();
        return new ManagedPackLayout(
                resourcePacks,
                resourcePacks.resolve(
                        ManagedPackIdentity.DISPLAY_NAME));
    }

}
