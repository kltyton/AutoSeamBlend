package com.kltyton.autoseamblend.authoring.storage;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 中文：Loader 中立的 Managed 工作区路径 DTO；构造它不会触发文件系统写入。
 *
 * English: Loader-neutral Managed workspace path DTO; construction performs no
 * filesystem writes.
 */
public record ManagedPackWriteLayout(Path resourcePacksRoot, Path root) {
    public ManagedPackWriteLayout {
        resourcePacksRoot = Objects.requireNonNull(resourcePacksRoot, "resourcePacksRoot")
                .toAbsolutePath()
                .normalize();
        root = Objects.requireNonNull(root, "root")
                .toAbsolutePath()
                .normalize();
        if (!Objects.equals(root.getParent(), resourcePacksRoot)) {
            throw new IllegalArgumentException(
                    "Managed pack must be a direct resourcepacks child");
        }
    }
}
