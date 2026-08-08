package com.kltyton.autoseamblend.authoring.storage;

import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringProject;
import com.kltyton.autoseamblend.authoring.document.NativeDocumentOperations;
import com.kltyton.autoseamblend.authoring.property.NativePropertyPatch;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;

/** 中文：一次显式原生文档写入，随后恰好执行一次客户端资源重载。 / English: One explicit native-document write followed by exactly one client resource reload. */
public final class ManagedSaveCoordinator {
    private static final AtomicReference<ManagedSaveCoordinator>
            INSTANCE = new AtomicReference<>();

    private final Minecraft minecraft;
    private final ManagedPackWriteLayout layout;
    private final NativeDocumentOperations documentOperations;
    private final ThreadPoolExecutor writer;
    private final AtomicReference<Status> status =
            new AtomicReference<>(Status.idle());

    private ManagedSaveCoordinator(
            Minecraft minecraft,
            NativeDocumentOperations documentOperations) {
        this.minecraft = Objects.requireNonNull(
                minecraft,
                "minecraft");
        this.documentOperations = Objects.requireNonNull(
                documentOperations,
                "documentOperations");
        ManagedPackLayout nativeLayout = ManagedPackLayout.current(minecraft);
        this.layout = new ManagedPackWriteLayout(
                nativeLayout.resourcePacksRoot(),
                nativeLayout.root());
        ThreadFactory threads = runnable -> {
            Thread thread = new Thread(
                    runnable,
                    "AutoSeamBlend Managed Save");
            thread.setDaemon(true);
            return thread;
        };
        this.writer = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                threads,
                new ThreadPoolExecutor.AbortPolicy());
    }

    public static ManagedSaveCoordinator instance(
            Minecraft minecraft) {
        return instance(minecraft, NativeDocumentOperations.shared());
    }

    public static ManagedSaveCoordinator instance(
            Minecraft minecraft,
            NativeDocumentOperations documentOperations) {
        ManagedSaveCoordinator existing =
                INSTANCE.get();
        if (existing != null) {
            return existing;
        }
        ManagedSaveCoordinator created =
                new ManagedSaveCoordinator(minecraft, documentOperations);
        if (INSTANCE.compareAndSet(null, created)) {
            return created;
        }
        created.writer.shutdownNow();
        return INSTANCE.get();
    }

    public CompletableFuture<SaveResult> save(
            ManagedAuthoringProject project) {
        return save(
                project,
                Map.of());
    }

    public CompletableFuture<SaveResult> save(
            ManagedAuthoringProject project,
            Map<String, byte[]> editedFiles) {
        return save(
                project,
                editedFiles,
                List.of());
    }

    public CompletableFuture<SaveResult> save(
            ManagedAuthoringProject project,
            Map<String, byte[]> editedFiles,
            List<NativePropertyPatch> propertyPatches) {
        return save(
                List.of(Objects.requireNonNull(
                        project,
                        "project")),
                editedFiles,
                propertyPatches);
    }

    public CompletableFuture<SaveResult> save(
            List<ManagedAuthoringProject> projects,
            Map<String, byte[]> editedFiles,
            List<NativePropertyPatch> propertyPatches) {
        List<ManagedAuthoringProject> submittedProjects =
                List.copyOf(
                Objects.requireNonNull(
                        projects,
                        "projects"));
        LinkedHashMap<String, byte[]> files =
                new LinkedHashMap<>();
        Objects.requireNonNull(
                        editedFiles,
                        "editedFiles")
                .forEach((path, content) ->
                        files.put(
                                Objects.requireNonNull(
                                        path,
                                        "edited path"),
                                Objects.requireNonNull(
                                                content,
                                                "edited content")
                                        .clone()));
        List<NativePropertyPatch> patches =
                List.copyOf(
                        Objects.requireNonNull(
                                propertyPatches,
                                "propertyPatches"));
        if (submittedProjects.isEmpty()
                && files.isEmpty()
                && patches.isEmpty()) {
            throw new IllegalArgumentException(
                    "save requires a project, edited file, or native property patch");
        }
        Status previous = status.get();
        if (previous.phase().busy()) {
            return CompletableFuture.completedFuture(
                    SaveResult.failure(
                            "SAVE_ALREADY_IN_PROGRESS",
                            null));
        }
        status.set(new Status(
                Phase.WRITING,
                "WRITING_NATIVE_DOCUMENTS",
                null));
        CompletableFuture<SaveResult> result =
                new CompletableFuture<>();
        try {
            writer.execute(
                    () -> write(
                            submittedProjects,
                            files,
                            patches,
                            result));
        } catch (RejectedExecutionException exception) {
            status.set(new Status(
                    Phase.FAILED,
                    "SAVE_QUEUE_FULL",
                    exception));
            result.complete(SaveResult.failure(
                    "SAVE_QUEUE_FULL",
                    exception));
        }
        return result;
    }

    public Status status() {
        return status.get();
    }

    private void write(
            List<ManagedAuthoringProject> projects,
            Map<String, byte[]> editedFiles,
            List<NativePropertyPatch> propertyPatches,
            CompletableFuture<SaveResult> result) {
        ManagedSaveTransaction operation = null;
        try {
            operation = ManagedSaveTransaction.prepare(
                    layout,
                    projects,
                    editedFiles,
                    propertyPatches,
                    documentOperations);
            operation.commit();
            ManagedSaveTransaction completedOperation = operation;
            minecraft.execute(
                    () -> activate(completedOperation, result));
        } catch (IOException | RuntimeException exception) {
            rollbackFiles(operation, exception);
            fail(
                    "NATIVE_DOCUMENT_WRITE_FAILED",
                    exception,
                    result);
        }
    }

    private void activate(
            ManagedSaveTransaction operation,
            CompletableFuture<SaveResult> result) {
        com.kltyton.autoseamblend.authoring.storage.ManagedPackRepositoryOrder.Result ordering = null;
        try {
            status.set(new Status(
                    Phase.ACTIVATING,
                    "UPDATING_PACK_ORDER",
                    null));
            ordering = MinecraftManagedPackRepositoryOrder.ensureSelected(
                    minecraft);
            com.kltyton.autoseamblend.authoring.storage.ManagedPackRepositoryOrder.Result completedOrdering =
                    ordering;
            status.set(new Status(
                    Phase.RELOADING,
                    "RESOURCE_RELOAD_REQUESTED",
                    null));
            com.kltyton.autoseamblend.authoring.storage.ManagedPackRepositoryOrder.Result finalOrdering = ordering;
            operation.beginReload();
            minecraft.reloadResourcePacks()
                    .whenComplete((ignored, failure) -> {
                        if (failure != null) {
                            rollback(
                                    operation,
                                    completedOrdering,
                                    failure);
                            fail(
                                    "RESOURCE_RELOAD_FAILED",
                                    failure,
                                    result);
                            return;
                        }
                        IOException cleanupFailure = null;
                        try {
                            finishCommit(operation);
                        } catch (IOException cleanupException) {
                            // 中文：提交已持久化，备份清理失败不回滚，也不把已成功保存改判为失败；失败经结果契约传播。
                            // English: The commit is durable; a backup-cleanup failure neither rolls back nor flips the successful save to failure; the failure propagates through the result contract.
                            cleanupFailure = cleanupException;
                        }
                        status.set(Status.idle());
                        ManagedSaveTransaction.CommitSummary summary = operation.summary();
                        result.complete(new SaveResult(
                                true,
                                "SAVED",
                                summary.workspaceCreated(),
                                summary.changedPaths(),
                                finalOrdering.selectionChanged(),
                                cleanupFailure));
                    });
        } catch (IOException | RuntimeException exception) {
            rollback(operation, ordering, exception);
            fail(
                    "PACK_ACTIVATION_FAILED",
                    exception,
                    result);
        }
    }

    private void rollback(
            ManagedSaveTransaction operation,
            com.kltyton.autoseamblend.authoring.storage.ManagedPackRepositoryOrder.Result ordering,
            Throwable failure) {
        if (ordering != null) {
            try {
                ordering.rollback();
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
        rollbackFiles(operation, failure);
    }

    private static void rollbackFiles(
            ManagedSaveTransaction operation,
            Throwable failure) {
        if (operation == null) {
            return;
        }
        try {
            operation.rollback();
        } catch (IOException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    /**
     * 中文：成功重载后结束事务并丢弃回滚备份；提交已持久化，备份清理失败以 IOException 传播且不得回滚。
     *
     * English: Finalizes the transaction after a successful reload and discards
     * rollback backups; the commit is already durable, so a backup-cleanup
     * failure propagates as IOException and must not trigger rollback.
     */
    private static void finishCommit(
            ManagedSaveTransaction operation)
            throws IOException {
        operation.finish();
    }

    private void fail(
            String detail,
            Throwable failure,
            CompletableFuture<SaveResult> result) {
        status.set(new Status(
                Phase.FAILED,
                detail,
                failure));
        result.complete(
                SaveResult.failure(detail, failure));
    }

    public enum Phase {
        IDLE,
        WRITING,
        ACTIVATING,
        RELOADING,
        FAILED;

        boolean busy() {
            return this == WRITING
                    || this == ACTIVATING
                    || this == RELOADING;
        }
    }

    public record Status(
            Phase phase,
            String detail,
            Throwable failure) {
        public Status {
            Objects.requireNonNull(phase, "phase");
            if (detail == null || detail.isBlank()) {
                throw new IllegalArgumentException(
                        "status detail must not be blank");
            }
        }

        static Status idle() {
            return new Status(
                    Phase.IDLE,
                    "IDLE",
                    null);
        }
    }

    public record SaveResult(
            boolean success,
            String detail,
            boolean workspaceCreated,
            List<String> changedPaths,
            boolean selectionChanged,
            Throwable failure) {
        public SaveResult {
            if (detail == null || detail.isBlank()) {
                throw new IllegalArgumentException(
                        "save detail must not be blank");
            }
            changedPaths = List.copyOf(
                    Objects.requireNonNull(
                            changedPaths,
                            "changedPaths"));
        }

        static SaveResult failure(
                String detail,
                Throwable failure) {
            return new SaveResult(
                    false,
                    detail,
                    false,
                    List.of(),
                    false,
                    failure);
        }
    }
}
