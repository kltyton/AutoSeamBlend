package com.kltyton.autoseamblend.frontend.port;

import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDraftFields;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction;
import com.kltyton.autoseamblend.frontend.model.WorkbenchViewModel;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 中文：领域层唯一动作端口；实现方负责验证、原生 I/O、保存事务、导出和一次重载语义。
 *
 * <p>English: Sole action port into the domain layer. Its implementation owns
 * validation, native I/O, save transactions, export, and exactly-once reload
 * semantics.
 */
@FunctionalInterface
public interface WorkbenchActionPort<T extends WorkbenchDraftFields> {
    /**
     * 中文：同步返回已完成视图或已开始操作；异步完成只能在本方法返回后于客户端线程发布。
     *
     * <p>English: Returns either a settled view or a started operation. An
     * asynchronous completion may be published only after this method returns,
     * and only on the client thread.
     */
    Submission<T> submit(
            Request<T> request,
            CompletionSink<T> completions);

    /**
     * 中文：控制器已经发布忙碌视图后才允许启动底层异步操作，避免同步完成先于令牌安装。
     * English: Starts the underlying asynchronous operation only after the
     * controller has published its busy view, preventing inline completion
     * from racing token installation.
     */
    default void pendingPublished(OperationToken token) {
        // 中文：旧端口若没有异步操作无需处理。 / English: Legacy settled-only ports need no hook.
    }

    /**
     * 中文：一次动作捕获的不可变输入；保存与导出实现必须按修订号冻结完整工作区，不得重读持久化状态。
     *
     * <p>English: Immutable input captured for one action. Save and export
     * implementations must freeze the complete workspace at this revision and
     * must not reread persisted state as the source of truth.
     */
    record Request<T extends WorkbenchDraftFields>(
            long requestId,
            long publicationVersion,
            long documentRevision,
            OptionalLong previewGeneration,
            Optional<OperationToken> pendingOperation,
            WorkbenchAction action,
            WorkbenchViewModel<T> current) {
        public Request {
            if (requestId < 0
                    || publicationVersion < 0
                    || documentRevision < 0) {
                throw new IllegalArgumentException(
                        "request versions must be nonnegative");
            }
            previewGeneration = Objects.requireNonNull(
                    previewGeneration,
                    "previewGeneration");
            pendingOperation = Objects.requireNonNull(
                    pendingOperation,
                    "pendingOperation");
            action = Objects.requireNonNull(action, "action");
            current = Objects.requireNonNull(current, "current");
            if (current.document().revision() != documentRevision) {
                throw new IllegalArgumentException(
                        "request revision and view revision differ");
            }
            if (previewGeneration.isPresent()
                    && previewGeneration.orElseThrow() < 0) {
                throw new IllegalArgumentException(
                        "preview generation must be nonnegative");
            }
            if (isPreviewAction(action)
                    && previewGeneration.isEmpty()) {
                throw new IllegalArgumentException(
                        "preview action requires a captured generation");
            }
        }

        private static boolean isPreviewAction(
                WorkbenchAction action) {
            return action instanceof WorkbenchAction.ToggleNeighbor
                    || action instanceof WorkbenchAction.ObserveFace
                    || action instanceof WorkbenchAction.CycleReceiver
                    || action instanceof WorkbenchAction.ClearNeighbors;
        }
    }

    /** 中文：异步完成发布器。 / English: Asynchronous completion publisher. */
    @FunctionalInterface
    interface CompletionSink<T extends WorkbenchDraftFields> {
        boolean publish(Completion<T> completion);
    }

    /** 中文：动作提交结果。 / English: Action-submission result. */
    sealed interface Submission<T extends WorkbenchDraftFields>
            permits Settled, Pending {
        WorkbenchViewModel<T> view();
    }

    /** 中文：同步完成的不可变视图。 / English: Immutable synchronously settled view. */
    record Settled<T extends WorkbenchDraftFields>(
            WorkbenchViewModel<T> view)
            implements Submission<T> {
        public Settled {
            view = Objects.requireNonNull(view, "view");
            if (view.operationInProgress()) {
                throw new IllegalArgumentException(
                        "settled view cannot be in progress");
            }
        }
    }

    /** 中文：异步操作已开始及其忙碌视图。 / English: Started asynchronous operation and its busy view. */
    record Pending<T extends WorkbenchDraftFields>(
            WorkbenchViewModel<T> view,
            OperationToken token)
            implements Submission<T> {
        public Pending {
            view = Objects.requireNonNull(view, "view");
            token = Objects.requireNonNull(token, "token");
            if (!view.operationInProgress()
                    || view.actionsEnabled()) {
                throw new IllegalArgumentException(
                        "pending view must be busy and disable actions");
            }
        }
    }

    /**
     * 中文：保存不可取消；baked 导出可取消。令牌把完成结果绑定到请求和提交修订。
     *
     * <p>English: Save is not cancellable; baked export is cancellable. The
     * token binds a completion to its request and submitted revision.
     */
    record OperationToken(
            long requestId,
            Kind kind,
            long submittedRevision) {
        public OperationToken {
            if (requestId < 0 || submittedRevision < 0) {
                throw new IllegalArgumentException(
                        "operation versions must be nonnegative");
            }
            kind = Objects.requireNonNull(kind, "kind");
        }

        public boolean cancellable() {
            return kind == Kind.BAKED_EXPORT;
        }

        /** 中文：唯一允许挂起的领域操作。 / English: Domain operations allowed to remain pending. */
        public enum Kind {
            SAVE,
            BAKED_EXPORT
        }
    }

    /** 中文：与精确令牌绑定的最终视图和类型化收据。 / English: Final view and typed receipt bound to the exact token. */
    record Completion<T extends WorkbenchDraftFields>(
            OperationToken token,
            WorkbenchViewModel<T> view,
            Receipt receipt) {
        public Completion {
            token = Objects.requireNonNull(token, "token");
            view = Objects.requireNonNull(view, "view");
            receipt = Objects.requireNonNull(receipt, "receipt");
            if (view.operationInProgress()) {
                throw new IllegalArgumentException(
                        "completed view cannot be in progress");
            }
        }
    }

    /** 中文：完成结果的封闭收据集合。 / English: Closed set of completion receipts. */
    sealed interface Receipt
            permits SaveReceipt,
                    BakedExportReceipt,
                    Failed,
                    Cancelled {}

    /**
     * 中文：成功保存必须证明只完成一次资源重载，并报告原子工作区事务的变更。
     *
     * <p>English: A successful save must prove exactly one completed resource
     * reload and report the atomic workspace transaction changes.
     */
    record SaveReceipt(
            long persistedRevision,
            int completedResourceReloads,
            boolean workspaceCreated,
            List<String> changedPaths,
            boolean selectionChanged)
            implements Receipt {
        public SaveReceipt {
            if (persistedRevision < 0
                    || completedResourceReloads != 1) {
                throw new IllegalArgumentException(
                        "successful save requires exactly one completed reload");
            }
            changedPaths = List.copyOf(
                    Objects.requireNonNull(
                            changedPaths,
                            "changedPaths"));
            if (changedPaths.stream().anyMatch(
                    path -> path == null || path.isBlank())) {
                throw new IllegalArgumentException(
                        "changed paths must be nonblank");
            }
        }
    }

    /**
     * 中文：只表示 baked 导出；目的地和非空分区列表共同证明导出完成。
     *
     * <p>English: Represents baked export only. Destination and a nonempty
     * partition list together prove completion.
     */
    record BakedExportReceipt(
            long sourceRevision,
            String destination,
            List<String> partitions)
            implements Receipt {
        public BakedExportReceipt {
            if (sourceRevision < 0
                    || destination == null
                    || destination.isBlank()) {
                throw new IllegalArgumentException(
                        "baked export source and destination are required");
            }
            partitions = List.copyOf(
                    Objects.requireNonNull(
                            partitions,
                            "partitions"));
            if (partitions.isEmpty()
                    || partitions.stream().anyMatch(
                            value -> value == null
                                    || value.isBlank())) {
                throw new IllegalArgumentException(
                        "baked export requires nonblank partitions");
            }
        }
    }

    /** 中文：带稳定诊断码的失败结果。 / English: Failure with a stable diagnostic code. */
    record Failed(String diagnosticCode) implements Receipt {
        public Failed {
            requireDiagnostic(diagnosticCode);
        }
    }

    /** 中文：仅可取消 baked 导出。 / English: Cancellation result for baked export only. */
    record Cancelled(String diagnosticCode) implements Receipt {
        public Cancelled {
            requireDiagnostic(diagnosticCode);
        }
    }

    private static void requireDiagnostic(
            String diagnosticCode) {
        if (diagnosticCode == null || diagnosticCode.isBlank()) {
            throw new IllegalArgumentException(
                    "diagnostic code must be nonblank");
        }
    }
}
