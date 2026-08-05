package com.kltyton.autoseamblend.frontend.controller;

import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDraftFields;
import com.kltyton.autoseamblend.frontend.model.PreviewViewModel;
import com.kltyton.autoseamblend.frontend.model.WorkbenchViewModel;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort.Completion;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort.CompletionSink;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort.OperationToken;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort.OperationToken.Kind;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort.Pending;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort.Request;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort.Settled;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort.Submission;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.CompletionStage;
import java.util.function.LongSupplier;

/**
 * 中文：跨 Loader 共享的工作台操作协调器；只编排令牌、代次门和完成顺序。
 *
 * English: Loader-neutral workbench operation coordinator. It owns token
 * lifecycle, generation gates, cancellation, and completion ordering while
 * native services remain behind {@link DomainOperations}.
 */
public final class WorkbenchOperationCoordinator<T extends WorkbenchDraftFields>
        implements WorkbenchActionPort<T> {
    private final DomainOperations<T> operations;
    private final ClientDispatcher client;
    private final LongSupplier activeGeneration;
    private final long openedGeneration;
    private final Map<OperationToken, Prepared<T>> prepared = new HashMap<>();

    public WorkbenchOperationCoordinator(
            DomainOperations<T> operations,
            ClientDispatcher client,
            LongSupplier activeGeneration,
            long openedGeneration) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.client = Objects.requireNonNull(client, "client");
        this.activeGeneration = Objects.requireNonNull(
                activeGeneration,
                "activeGeneration");
        if (openedGeneration < 0) {
            throw new IllegalArgumentException(
                    "openedGeneration must be nonnegative");
        }
        this.openedGeneration = openedGeneration;
    }

    @Override
    public Submission<T> submit(
            Request<T> request,
            CompletionSink<T> completions) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(completions, "completions");
        OperationToken existing = request.pendingOperation().orElse(null);
        if (existing != null) {
            if (!(request.action() instanceof WorkbenchAction.CancelRequested)
                    || !existing.cancellable()) {
                throw new IllegalArgumentException(
                        "only a cancellable pending export accepts a cancel request");
            }
            operations.cancel(existing);
            return new Pending<>(request.current(), existing);
        }

        long currentGeneration = activeGeneration.getAsLong();
        if (request.action() instanceof WorkbenchAction.CancelRequested) {
            return new Settled<>(operations.apply(
                    request.action(),
                    request.current(),
                    currentGeneration));
        }
        if (currentGeneration != openedGeneration) {
            return new Settled<>(operations.rejected(
                    request.current(),
                    "WORKBENCH_GENERATION_STALE"));
        }
        if (isPreviewAction(request.action())
                && request.previewGeneration().isPresent()
                && request.previewGeneration().orElseThrow() != currentGeneration) {
            return new Settled<>(operations.rejected(
                    request.current(),
                    "PREVIEW_GENERATION_STALE"));
        }
        if (request.action() instanceof WorkbenchAction.SaveRequested
                && !request.current().dirty()) {
            return new Settled<>(operations.apply(
                    request.action(),
                    request.current(),
                    currentGeneration));
        }

        Kind kind = operationKind(request.action());
        if (kind == null) {
            return new Settled<>(operations.apply(
                    request.action(),
                    request.current(),
                    currentGeneration));
        }
        OperationToken token = new OperationToken(
                request.requestId(),
                kind,
                request.documentRevision());
        WorkbenchViewModel<T> frozen = request.current();
        WorkbenchViewModel<T> busy = operations.pending(frozen, token);
        Prepared<T> operation = new Prepared<>(
                new FrozenOperation<>(token, currentGeneration, frozen),
                completions);
        synchronized (prepared) {
            if (prepared.putIfAbsent(token, operation) != null) {
                throw new IllegalStateException(
                        "WORKBENCH_OPERATION_ALREADY_PREPARED");
            }
        }
        // 中文：这里只预留操作；pendingPublished 会在控制器安装令牌后启动它。
        // English: Reserve only; pendingPublished starts it after the controller installs the token.
        return new Pending<>(busy, token);
    }

    @Override
    public void pendingPublished(OperationToken token) {
        Prepared<T> operation;
        synchronized (prepared) {
            operation = prepared.remove(Objects.requireNonNull(token, "token"));
        }
        if (operation == null) {
            return;
        }
        CompletionStage<OperationResult<T>> stage;
        try {
            stage = Objects.requireNonNull(
                    operations.start(operation.frozen()),
                    "operation stage");
        } catch (RuntimeException failure) {
            publishFailure(operation, failure);
            return;
        }
        stage.whenComplete((result, failure) -> client.enqueue(() -> {
            OperationResult<T> completed = failure == null
                    ? Objects.requireNonNull(result, "operation result")
                    : operations.failed(operation.frozen().view(), failure);
            operation.completions().publish(new Completion<>(
                    operation.frozen().token(),
                    completed.view(),
                    completed.receipt()));
        }));
    }

    private void publishFailure(
            Prepared<T> operation,
            Throwable failure) {
        OperationResult<T> completed = operations.failed(
                operation.frozen().view(),
                failure);
        client.enqueue(() -> operation.completions().publish(new Completion<>(
                operation.frozen().token(),
                completed.view(),
                completed.receipt())));
    }

    private static Kind operationKind(WorkbenchAction action) {
        if (action instanceof WorkbenchAction.SaveRequested) {
            return Kind.SAVE;
        }
        if (action instanceof WorkbenchAction.ExportRequested) {
            return Kind.BAKED_EXPORT;
        }
        return null;
    }

    private static boolean isPreviewAction(WorkbenchAction action) {
        return action instanceof WorkbenchAction.ToggleNeighbor
                || action instanceof WorkbenchAction.ObserveFace
                || action instanceof WorkbenchAction.CycleReceiver
                || action instanceof WorkbenchAction.ClearNeighbors;
    }

    /** 中文：原生 Loader 的最小领域端口。 / English: Minimal native-loader domain port. */
    public interface DomainOperations<T extends WorkbenchDraftFields> {
        WorkbenchViewModel<T> apply(
                WorkbenchAction action,
                WorkbenchViewModel<T> frozen,
                long activeGeneration);

        WorkbenchViewModel<T> rejected(
                WorkbenchViewModel<T> frozen,
                String diagnosticCode);

        WorkbenchViewModel<T> pending(
                WorkbenchViewModel<T> frozen,
                OperationToken token);

        CompletionStage<OperationResult<T>> start(
                FrozenOperation<T> operation);

        boolean cancel(OperationToken token);

        OperationResult<T> failed(
                WorkbenchViewModel<T> frozen,
                Throwable failure);
    }

    /** 中文：客户端调度端口必须把回调放入下一次客户端队列。 / English: Client callbacks must be queued. */
    @FunctionalInterface
    public interface ClientDispatcher {
        void enqueue(Runnable callback);
    }

    public record FrozenOperation<T extends WorkbenchDraftFields>(
            OperationToken token,
            long capturedGeneration,
            WorkbenchViewModel<T> view) {
        public FrozenOperation {
            Objects.requireNonNull(token, "token");
            if (capturedGeneration < 0) {
                throw new IllegalArgumentException(
                        "captured generation must be nonnegative");
            }
            Objects.requireNonNull(view, "view");
            if (token.submittedRevision() != view.document().revision()) {
                throw new IllegalArgumentException(
                        "operation token and frozen view differ");
            }
        }
    }

    public record OperationResult<T extends WorkbenchDraftFields>(
            WorkbenchViewModel<T> view,
            WorkbenchActionPort.Receipt receipt) {
        public OperationResult {
            Objects.requireNonNull(view, "view");
            Objects.requireNonNull(receipt, "receipt");
            if (view.operationInProgress()) {
                throw new IllegalArgumentException(
                        "operation result must be settled");
            }
        }
    }

    private record Prepared<T extends WorkbenchDraftFields>(
            FrozenOperation<T> frozen,
            CompletionSink<T> completions) {
        private Prepared {
            Objects.requireNonNull(frozen, "frozen");
            Objects.requireNonNull(completions, "completions");
        }
    }
}
