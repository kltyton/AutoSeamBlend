package com.kltyton.autoseamblend.authoring.storage;

import com.kltyton.autoseamblend.texture.budget.TextureInputBudget;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 中文：执行一次 Managed 同级临时文件写入、原子替换和可回滚提交。
 *
 * English: Executes one Managed sibling-temporary write, atomic replacement,
 * and rollback-capable commit.
 *
 * <p>This class intentionally contains only Java NIO and the common path
 * policy. Loader code supplies the layout and metadata, while client/Loader
 * activation remains outside this transaction.</p>
 */
public final class ManagedPackTransaction {
    private ManagedPackTransaction() {}

    /**
     * 中文：在同一进程内串行提交，避免同一 Managed 工作区的交错替换。
     *
     * English: Serialize commits in-process so one Managed workspace cannot
     * observe interleaved replacements.
     */
    /**
     * 中文：可选地在相对路径规范化后补齐默认 pack.mcmeta；保留 Loader 原有覆盖语义。
     *
     * English: Optionally add the default pack.mcmeta after relative-path
     * normalization, preserving the loader's existing override semantics.
     */
    public static synchronized CommitResult commit(
            ManagedPackWriteLayout layout,
            Map<String, byte[]> requested,
            byte[] defaultPackMetadata)
            throws IOException {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(requested, "requested");
        LinkedHashMap<String, byte[]> files = defensiveFiles(requested);
        if (defaultPackMetadata != null) {
            files.putIfAbsent("pack.mcmeta", defaultPackMetadata.clone());
        }
        ManagedPathPolicy.rejectCaseCollisions(files.keySet());
        ManagedPathPolicy.rejectWorkspaceRoot(
                layout.resourcePacksRoot(),
                layout.root());

        boolean created = !Files.exists(
                layout.root(),
                LinkOption.NOFOLLOW_LINKS);
        ArrayList<PendingWrite> pending = new ArrayList<>();
        ArrayList<AppliedWrite> applied = new ArrayList<>();
        try {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                Path target = ManagedPathPolicy.resolveContained(
                        layout.resourcePacksRoot(),
                        layout.root(),
                        entry.getKey());
                byte[] content = entry.getValue();
                if (Files.isRegularFile(
                                target,
                                LinkOption.NOFOLLOW_LINKS)
                        && Arrays.equals(
                                readExisting(target, entry.getKey()),
                                content)) {
                    continue;
                }
                Files.createDirectories(target.getParent());
                Path temporary = Files.createTempFile(
                        target.getParent(),
                        ".autoseamblend-",
                        ".tmp");
                Files.write(temporary, content);
                pending.add(new PendingWrite(
                        entry.getKey(),
                        temporary,
                        target));
            }
            for (PendingWrite write : pending) {
                Path backup = backup(write.target());
                AppliedWrite appliedWrite = new AppliedWrite(
                        write.relativePath(),
                        write.target(),
                        backup);
                applied.add(appliedWrite);
                replace(write.temporary(), write.target());
            }
        } catch (IOException | RuntimeException failure) {
            rollbackApplied(
                    layout,
                    created,
                    applied,
                    failure);
            throw failure;
        } finally {
            for (PendingWrite write : pending) {
                Files.deleteIfExists(write.temporary());
            }
        }
        return new CommitResult(
                layout,
                created,
                applied);
    }

    private static LinkedHashMap<String, byte[]> defensiveFiles(
            Map<String, byte[]> requested) {
        LinkedHashMap<String, byte[]> files = new LinkedHashMap<>();
        requested.forEach((path, content) -> {
            String safe = ManagedPathPolicy.validateRelative(path);
            byte[] copy = Objects.requireNonNull(
                            content,
                            "content for " + safe)
                    .clone();
            if (files.putIfAbsent(safe, copy) != null) {
                throw new IllegalArgumentException(
                        "duplicate Managed path: " + safe);
            }
        });
        return files;
    }

    /**
     * 中文：读取既有 Managed 文件时按资源后缀施加有界输入预算。
     *
     * English: Apply the bounded input budget by resource suffix when reading an existing
     * Managed file.
     */
    private static byte[] readExisting(Path target, String relativePath)
            throws IOException {
        TextureInputBudget.InputKind kind = inputKind(relativePath);
        return TextureInputBudget.DEFAULT.read(
                target,
                kind,
                "managed-existing:" + relativePath);
    }

    /**
     * 中文：PNG、pack 元数据和其他原生文档使用各自固定上限。
     *
     * English: PNGs, pack metadata, and other native documents use their fixed limits.
     */
    private static TextureInputBudget.InputKind inputKind(String relativePath) {
        String normalized = relativePath.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".png")) {
            return TextureInputBudget.InputKind.PNG;
        }
        if (normalized.endsWith(".mcmeta")) {
            return TextureInputBudget.InputKind.METADATA;
        }
        return TextureInputBudget.InputKind.NATIVE_DOCUMENT;
    }

    private static void replace(
            Path source,
            Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path backup(Path target) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(
                    "Managed target is not a regular file: " + target);
        }
        Path backup = Files.createTempFile(
                target.getParent(),
                ".autoseamblend-",
                ".rollback");
        try {
            Files.copy(
                    target,
                    backup,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
            return backup;
        } catch (IOException | RuntimeException failure) {
            Files.deleteIfExists(backup);
            throw failure;
        }
    }

    private static void rollbackApplied(
            ManagedPackWriteLayout layout,
            boolean workspaceCreated,
            List<AppliedWrite> applied,
            Throwable failure) {
        for (int index = applied.size() - 1;
                index >= 0;
                index--) {
            AppliedWrite write = applied.get(index);
            try {
                write.rollback();
                cleanupEmptyParents(
                        write.target().getParent(),
                        layout.root());
            } catch (IOException | RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
        if (workspaceCreated) {
            try {
                Files.deleteIfExists(layout.root());
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }

    private static void cleanupEmptyParents(
            Path start,
            Path root) {
        Path current = start;
        while (current != null
                && current.startsWith(root)
                && !current.equals(root)) {
            try {
                Files.delete(current);
            } catch (IOException notEmptyOrUnavailable) {
                return;
            }
            current = current.getParent();
        }
    }

    /**
     * 中文：提交后由 owning-thread/激活层决定完成或回滚；此对象不执行资源重载。
     *
     * English: The owning-thread/activation layer decides whether to finish or
     * roll back after commit; this object never reloads resources.
     */
    public static final class CommitResult {
        private final ManagedPackWriteLayout layout;
        private final boolean workspaceCreated;
        private final List<AppliedWrite> applied;
        private final List<String> changedPaths;
        private boolean resolved;

        private CommitResult(
                ManagedPackWriteLayout layout,
                boolean workspaceCreated,
                List<AppliedWrite> applied) {
            this.layout = Objects.requireNonNull(layout, "layout");
            this.workspaceCreated = workspaceCreated;
            this.applied = List.copyOf(applied);
            this.changedPaths = this.applied.stream()
                    .map(AppliedWrite::relativePath)
                    .toList();
        }

        public boolean workspaceCreated() {
            return workspaceCreated;
        }

        public List<String> changedPaths() {
            return changedPaths;
        }

        public synchronized void finish() throws IOException {
            if (resolved) {
                return;
            }
            IOException failure = null;
            for (AppliedWrite write : applied) {
                try {
                    write.discardBackup();
                } catch (IOException cleanupFailure) {
                    if (failure == null) {
                        failure = cleanupFailure;
                    } else {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
            }
            resolved = true;
            if (failure != null) {
                throw failure;
            }
        }

        public synchronized void rollback() throws IOException {
            if (resolved) {
                return;
            }
            IOException failure = null;
            for (int index = applied.size() - 1;
                    index >= 0;
                    index--) {
                AppliedWrite write = applied.get(index);
                try {
                    write.rollback();
                    cleanupEmptyParents(
                            write.target().getParent(),
                            layout.root());
                } catch (IOException rollbackFailure) {
                    if (failure == null) {
                        failure = rollbackFailure;
                    } else {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
            }
            if (workspaceCreated) {
                try {
                    Files.deleteIfExists(layout.root());
                } catch (IOException rollbackFailure) {
                    if (failure == null) {
                        failure = rollbackFailure;
                    } else {
                        failure.addSuppressed(rollbackFailure);
                    }
                }
            }
            resolved = true;
            if (failure != null) {
                throw failure;
            }
        }
    }

    private record PendingWrite(
            String relativePath,
            Path temporary,
            Path target) {}

    private record AppliedWrite(
            String relativePath,
            Path target,
            Path backup) {
        private void rollback() throws IOException {
            if (backup == null) {
                Files.deleteIfExists(target);
                return;
            }
            replace(backup, target);
        }

        private void discardBackup() throws IOException {
            if (backup != null) {
                Files.deleteIfExists(backup);
            }
        }
    }
}
