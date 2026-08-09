package com.kltyton.autoseamblend.authoring.export;

import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.authoring.export.ExportDraft;
import com.kltyton.autoseamblend.authoring.export.ExportDestinationPathPolicy;
import com.kltyton.autoseamblend.authoring.export.ManagedExportPartitionCapture;
import com.kltyton.autoseamblend.export.managed.ManagedExportDispatcher;
import com.kltyton.autoseamblend.export.managed.ManagedExportIr;
import com.kltyton.autoseamblend.export.managed.ManagedExportRequest;
import com.kltyton.autoseamblend.authoring.storage.ManagedPackLayout;
import com.kltyton.autoseamblend.engine.registry.EngineRegistryRuntimeState;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.minecraft.client.Minecraft;

/**
 * 中文：显式导出已选表面或配置目标；渲染状态在客户端线程冻结，原生组装、PNG 编码、暂存和发布在一个有界工作线程中运行。
 *
 * English:
 * Explicit selected-surface or configured-target export. Render state is
 * frozen on the client thread; native assembly, PNG encoding, staging and
 * publication run on one bounded worker.
 */
public final class ManagedExportService {
    private static final AtomicReference<
                    ManagedExportService>
            INSTANCE = new AtomicReference<>();

    private final Minecraft minecraft;
    private final EngineRegistryRuntimeState engines;
    private final Function<String, NativeExportRuntime.RuntimeMetadata> metadata;
    private final ManagedPackLayout managedLayout;
    private final ManagedExportDispatcher dispatcher =
            new ManagedExportDispatcher();
    private final ThreadPoolExecutor worker;
    private final AtomicReference<Status> status =
            new AtomicReference<>(Status.idle());

    private ManagedExportService(
            Minecraft minecraft,
            EngineRegistryRuntimeState engines,
            Function<String, NativeExportRuntime.RuntimeMetadata> metadata) {
        this.minecraft = Objects.requireNonNull(
                minecraft,
                "minecraft");
        this.engines = Objects.requireNonNull(
                engines,
                "engines");
        this.metadata = Objects.requireNonNull(
                metadata,
                "metadata");
        this.managedLayout =
                ManagedPackLayout.current(minecraft);
        ThreadFactory threads = runnable -> {
            Thread thread = new Thread(
                    runnable,
                    "AutoSeamBlend Native Export");
            thread.setDaemon(true);
            return thread;
        };
        this.worker = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(1),
                threads,
                new ThreadPoolExecutor.AbortPolicy());
    }

    public static ManagedExportService instance(
            Minecraft minecraft,
            EngineRegistryRuntimeState engines,
            Function<String, NativeExportRuntime.RuntimeMetadata> metadata) {
        ManagedExportService existing =
                INSTANCE.get();
        if (existing != null) {
            return existing;
        }
        ManagedExportService created =
                new ManagedExportService(
                        minecraft,
                        engines,
                        metadata);
        if (INSTANCE.compareAndSet(
                null,
                created)) {
            return created;
        }
        created.worker.shutdownNow();
        return INSTANCE.get();
    }

    public ExportHandle exportSelected(
            ManagedExportRequest request) {
        Objects.requireNonNull(request, "request");
        AtomicBoolean cancelled =
                new AtomicBoolean();
        CompletableFuture<ExportResult> result =
                new CompletableFuture<>();
        if (!begin(new Status(
                Phase.CAPTURING,
                "CAPTURING_RENDERED_SURFACE",
                null))) {
            result.completeExceptionally(
                    new IllegalStateException(
                            "EXPORT_ALREADY_IN_PROGRESS"));
            return new ExportHandle(
                    result,
                    cancelled);
        }
        minecraft.submit(this::capture)
                .whenComplete((capture, failure) -> {
                    if (failure != null) {
                        fail(
                                failure,
                                result);
                        return;
                    }
                    submit(
                            capture,
                            request,
                            cancelled,
                            result);
                });
        return new ExportHandle(
                result,
                cancelled);
    }

    public ExportHandle exportConfigured(
            ManagedExportRequest request) {
        Objects.requireNonNull(
                request,
                "request");
        AtomicBoolean cancelled =
                new AtomicBoolean();
        CompletableFuture<ExportResult> result =
                new CompletableFuture<>();
        if (!begin(new Status(
                Phase.CAPTURING,
                "CAPTURING_CONFIGURED_SURFACES",
                null))) {
            result.completeExceptionally(
                    new IllegalStateException(
                            "EXPORT_ALREADY_IN_PROGRESS"));
            return new ExportHandle(
                    result,
                    cancelled);
        }
        minecraft.submit(this::captureConfigured)
                .whenComplete((capture, failure) -> {
                    if (failure != null) {
                        fail(
                                failure,
                                result);
                        return;
                    }
                    submitConfigured(
                            capture,
                            request,
                            cancelled,
                            result);
                });
        return new ExportHandle(
                result,
                cancelled);
    }

    /**
     * 中文：导出调用方已经在客户端线程冻结的可视化工作区，不重新读取持久配置或 Managed 文件。
     *
     * English:
     * Exports a visual workspace already frozen by the caller on the client
     * thread, without rereading persisted config or Managed files.
     */
    public ExportHandle exportWorkspace(
            ManagedExportRequest request,
            Map<String, List<ExportDraft>>
                    partitions) {
        Objects.requireNonNull(request, "request");
        AtomicBoolean cancelled =
                new AtomicBoolean();
        CompletableFuture<ExportResult> result =
                new CompletableFuture<>();
        if (!begin(new Status(
                Phase.CAPTURING,
                "CAPTURING_WORKSPACE",
                null))) {
            result.completeExceptionally(
                    new IllegalStateException(
                            "EXPORT_ALREADY_IN_PROGRESS"));
            return new ExportHandle(
                    result,
                    cancelled);
        }
        ManagedExportPartitionCapture capture;
        try {
            capture = new ManagedExportPartitionCapture(
                    partitions);
            for (String engineId
                    : capture.partitions()
                            .keySet()) {
                if (!NativeExportRuntime
                        .available(engineId)) {
                    throw new IllegalStateException(
                            "ENGINE_ADAPTER_UNAVAILABLE:"
                                    + engineId);
                }
            }
        } catch (RuntimeException exception) {
            fail(exception, result);
            return new ExportHandle(
                    result,
                    cancelled);
        }
        submitConfigured(
                capture,
                request,
                cancelled,
                result);
        return new ExportHandle(
                result,
                cancelled);
    }

    public Status status() {
        return status.get();
    }

    /**
     * 中文：用 CAS 线性化导出入口，避免两个并发请求同时越过 busy 检查。
     * English: Linearizes export entry with CAS so concurrent requests cannot both pass the busy
     * check.
     */
    private boolean begin(Status started) {
        Objects.requireNonNull(started, "started");
        while (true) {
            Status current = status.get();
            if (current.phase().busy()) {
                return false;
            }
            if (status.compareAndSet(current, started)) {
                return true;
            }
        }
    }

    private Capture capture() {
        try {
            EngineQuerySelection
                    selection =
                            ExportDrafts.current(
                                    minecraft,
                                    engines)
                                    .orElseThrow(() ->
                                            new IllegalStateException(
                                                    "ENGINE_REQUIRED"));
            String engineId =
                    selection.engineId();
            if (!NativeExportRuntime
                    .available(engineId)) {
                throw new IllegalStateException(
                        "ENGINE_ADAPTER_UNAVAILABLE:"
                                + engineId);
            }
            Optional<ExportDraft> draft =
                    ExportDrafts
                            .currentSelection(
                                    minecraft,
                                    selection.family(),
                                    engines);
            return new Capture(
                    engineId,
                    draft.orElseThrow(() ->
                            new IllegalStateException(
                                    "SELECTED_SURFACE_REQUIRED")));
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }

    private void submit(
            Capture capture,
            ManagedExportRequest request,
            AtomicBoolean cancelled,
            CompletableFuture<ExportResult> result) {
        if (cancelled.get()) {
            result.cancel(false);
            status.set(Status.idle());
            return;
        }
        status.set(new Status(
                Phase.QUEUED,
                "EXPORT_QUEUED",
                null));
        try {
            worker.execute(() ->
                    run(
                            capture,
                            request,
                            cancelled,
                            result));
        } catch (RejectedExecutionException exception) {
            fail(
                    new IllegalStateException(
                            "EXPORT_QUEUE_FULL",
                            exception),
                    result);
        }
    }

    private ManagedExportPartitionCapture captureConfigured() {
        try {
            Map<String, List<ExportDraft>>
                    partitions =
                            ExportDrafts
                                    .configuredTargets(
                                            minecraft,
                                            engines);
            if (partitions.isEmpty()) {
                throw new IllegalStateException(
                        "NO_RESOLVED_SURFACES");
            }
            for (String engineId
                    : partitions.keySet()) {
                if (!NativeExportRuntime
                        .available(engineId)) {
                    throw new IllegalStateException(
                            "ENGINE_ADAPTER_UNAVAILABLE:"
                                    + engineId);
                }
            }
            return new ManagedExportPartitionCapture(
                    partitions);
        } catch (IOException exception) {
            throw new CompletionException(
                    exception);
        }
    }

    private void submitConfigured(
            ManagedExportPartitionCapture capture,
            ManagedExportRequest request,
            AtomicBoolean cancelled,
            CompletableFuture<ExportResult> result) {
        if (cancelled.get()) {
            result.cancel(false);
            status.set(Status.idle());
            return;
        }
        status.set(new Status(
                Phase.QUEUED,
                "EXPORT_QUEUED",
                null));
        try {
            worker.execute(() ->
                    runConfigured(
                            capture,
                            request,
                            cancelled,
                            result));
        } catch (RejectedExecutionException exception) {
            fail(
                    new IllegalStateException(
                            "EXPORT_QUEUE_FULL",
                            exception),
                    result);
        }
    }

    private void run(
            Capture capture,
            ManagedExportRequest request,
            AtomicBoolean cancelled,
            CompletableFuture<ExportResult> result) {
        try {
            ExportDestinationPathPolicy.requireOutsideManaged(
                    request.destination(),
                    managedLayout.root());
            status.set(new Status(
                    Phase.ASSEMBLING,
                    "ASSEMBLING_NATIVE_RESOURCES",
                    null));
            ManagedExportIr ir =
                    NativeExportRuntime
                            .assemble(
                                    capture.engineId(),
                                    capture.draft(),
                                    metadata.apply(
                                            capture.engineId()));
            status.set(new Status(
                    Phase.WRITING,
                    "WRITING_EXPORT",
                    null));
            ManagedExportDispatcher.Result written =
                    dispatcher.dispatch(
                            request,
                            ir,
                            cancelled::get,
                            ignored -> isCurrent(
                                    capture,
                                    ir));
            status.set(Status.idle());
            result.complete(new ExportResult(
                    written.destination(),
                    Map.of(
                            capture.engineId(),
                            written)));
        } catch (IOException | RuntimeException exception) {
            fail(exception, result);
        }
    }

    private void runConfigured(
            ManagedExportPartitionCapture capture,
            ManagedExportRequest request,
            AtomicBoolean cancelled,
            CompletableFuture<ExportResult> result) {
        try {
            ExportDestinationPathPolicy.requireOutsideManaged(
                    request.destination(),
                    managedLayout.root());
            status.set(new Status(
                    Phase.ASSEMBLING,
                    "ASSEMBLING_NATIVE_PARTITIONS",
                    null));
            ArrayList<ManagedExportIr> partitions =
                    new ArrayList<>();
            for (Map.Entry<
                            String,
                            List<ExportDraft>>
                    entry : capture.partitions()
                            .entrySet()) {
                partitions.add(
                        NativeExportRuntime
                                .assemble(
                                        entry.getKey(),
                                        entry.getValue(),
                                        metadata.apply(
                                                entry.getKey())));
            }
            status.set(new Status(
                    Phase.WRITING,
                    partitions.size() == 1
                            ? "WRITING_EXPORT"
                            : "WRITING_PARTITIONED_EXPORT",
                    null));
            if (partitions.size() == 1) {
                ManagedExportIr ir =
                        partitions.getFirst();
                ManagedExportDispatcher.Result
                        written =
                                dispatcher.dispatch(
                                        request,
                                        ir,
                                        cancelled::get,
                                        ignored ->
                                                isCurrent(
                                                        capture,
                                                        ir));
                status.set(Status.idle());
                result.complete(new ExportResult(
                        written.destination(),
                        Map.of(
                                ir.engine(),
                                written)));
                return;
            }
            ManagedExportDispatcher.PartitionedResult
                    written =
                            dispatcher.dispatchPartitions(
                                    request,
                                    partitions,
                                    cancelled::get,
                                    ir -> isCurrent(
                                            capture,
                                            ir));
            status.set(Status.idle());
            result.complete(new ExportResult(
                    written.destination(),
                    written.partitions()));
        } catch (IOException | RuntimeException exception) {
            fail(exception, result);
        }
    }

    private boolean isCurrent(
            Capture capture,
            ManagedExportIr ir) {
        return ExportDrafts.isCurrent(
                        capture.draft())
                && ir.runtimeGeneration()
                        == capture.draft()
                                .surfaceGeneration()
                && ir.managedGenerationHash()
                        .equals(
                                NativeExportRuntime
                                        .managedGenerationHash(
                                                List.of(
                                                        capture.draft())));
    }

    private boolean isCurrent(
            ManagedExportPartitionCapture capture,
            ManagedExportIr ir) {
        List<ExportDraft> drafts =
                capture.partitions()
                        .get(ir.engine());
        return drafts != null
                && !drafts.isEmpty()
                && drafts.stream()
                        .allMatch(
                                ExportDrafts::isCurrent)
                && ir.runtimeGeneration()
                        == drafts.getFirst()
                                .surfaceGeneration()
                && ir.managedGenerationHash()
                        .equals(
                                NativeExportRuntime
                                        .managedGenerationHash(
                                                drafts));
    }

    private void fail(
            Throwable failure,
            CompletableFuture<ExportResult> result) {
        Throwable cause = unwrap(failure);
        status.set(new Status(
                Phase.FAILED,
                cause.getMessage() == null
                        || cause.getMessage()
                                .isBlank()
                        ? cause.getClass()
                                .getSimpleName()
                        : cause.getMessage(),
                cause));
        result.completeExceptionally(cause);
    }

    private static Throwable unwrap(
            Throwable failure) {
        Throwable current = failure;
        while ((current
                                instanceof CompletionException
                        || current
                                instanceof java.util.concurrent
                                        .ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record Capture(
            String engineId,
            ExportDraft draft) {
        private Capture {
            Objects.requireNonNull(
                    engineId,
                    "engineId");
            Objects.requireNonNull(
                    draft,
                    "draft");
        }
    }

    public enum Phase {
        IDLE,
        CAPTURING,
        QUEUED,
        ASSEMBLING,
        WRITING,
        FAILED;

        public boolean busy() {
            return this != IDLE
                    && this != FAILED;
        }
    }

    public record Status(
            Phase phase,
            String detail,
            Throwable failure) {
        public Status {
            Objects.requireNonNull(
                    phase,
                    "phase");
            if (detail == null
                    || detail.isBlank()) {
                throw new IllegalArgumentException(
                        "export status detail must not be blank");
            }
        }

        static Status idle() {
            return new Status(
                    Phase.IDLE,
                    "IDLE",
                    null);
        }
    }

    public record ExportResult(
            Path destination,
            Map<String, ManagedExportDispatcher.Result>
                    partitions) {
        public ExportResult {
            Objects.requireNonNull(
                    destination,
                    "destination");
            partitions = java.util.Collections
                    .unmodifiableMap(
                            new LinkedHashMap<>(
                                    Objects.requireNonNull(
                                            partitions,
                                            "partitions")));
            if (partitions.isEmpty()) {
                throw new IllegalArgumentException(
                        "export result needs at least one partition");
            }
        }
    }

    public record ExportHandle(
            CompletableFuture<ExportResult> future,
            AtomicBoolean cancellation) {
        public ExportHandle {
            Objects.requireNonNull(
                    future,
                    "future");
            Objects.requireNonNull(
                    cancellation,
                    "cancellation");
        }

        public void cancel() {
            cancellation.set(true);
        }
    }
}
