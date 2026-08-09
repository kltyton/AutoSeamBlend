package com.kltyton.autoseamblend.frontend.controller;

import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDraftFields;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort;
import com.kltyton.autoseamblend.frontend.port.WorkbenchExitPort;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort.BakedExportReceipt;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort.Cancelled;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort.Completion;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort.Failed;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort.OperationToken;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort.Pending;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort.Request;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort.SaveReceipt;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort.Settled;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort.Submission;
import com.kltyton.autoseamblend.frontend.port.WorkbenchExitPort.Reason;
import com.kltyton.autoseamblend.frontend.model.PreviewViewModel;
import com.kltyton.autoseamblend.frontend.model.WorkbenchViewModel;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;

/**
 * 中文：在客户端线程原子替换不可变视图；控件事件从不直接触及文件、引擎或重载。
 *
 * <p>English: Replaces immutable views on the client thread. Widget events
 * never touch files, engines, or reloads directly.
 */
public final class UilibWorkbenchController<T extends WorkbenchDraftFields> {
    private final WorkbenchActionPort<T> actions;
    private final WorkbenchExitPort exits;
    private final Thread ownerThread;
    private WorkbenchViewModel<T> view;
    private long publicationVersion;
    /** 中文：布局代次仅在控件树重建时递增，绘画像素发布不使画布租约失效。 / English: Layout generation advances only on widget-tree rebuilds so pixel publications never invalidate the active canvas lease. */
    private long layoutGeneration;
    private long nextRequestId;
    private boolean exitRequested;
    private Optional<OperationToken> pendingOperation = Optional.empty();
    private Consumer<WorkbenchViewModel<T>> listener = ignored -> {};

    public UilibWorkbenchController(
            WorkbenchViewModel<T> initial,
            WorkbenchActionPort<T> actions,
            WorkbenchExitPort exits) {
        this.view = Objects.requireNonNull(initial, "initial");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.exits = Objects.requireNonNull(exits, "exits");
        ownerThread = Thread.currentThread();
        if (initial.operationInProgress()) {
            throw new IllegalArgumentException(
                    "initial workbench view cannot be in progress");
        }
    }

    public WorkbenchViewModel<T> view() {
        return view;
    }

    public long publicationVersion() {
        return publicationVersion;
    }

    public long layoutGeneration() {
        return layoutGeneration;
    }

    public Optional<OperationToken> pendingOperation() {
        return pendingOperation;
    }

    /** 中文：Screen 被外部移除时关闭会话并拒绝迟到完成。 / English: Dispose the session when the Screen is removed externally and reject late completions. */
    public void close() {
        requireOwnerThread();
        pendingOperation = Optional.empty();
        exitRequested = true;
    }

    public void setListener(
            Consumer<WorkbenchViewModel<T>> replacement) {
        listener = Objects.requireNonNull(replacement, "replacement");
    }

    /**
     * 中文：控制器是提交门禁的唯一权威；返回 false 表示动作在进入领域端口前已被拒绝。
     *
     * <p>English: The controller is the authoritative submission gate. A false
     * result means the action was rejected before reaching the domain port.
     */
    public boolean dispatch(WorkbenchAction action) {
        requireOwnerThread();
        action = Objects.requireNonNull(action, "action");
        if (!canDispatch(action)) {
            return false;
        }
        long requestId = nextRequestId;
        nextRequestId = Math.addExact(nextRequestId, 1);
        OptionalLong previewGeneration =
                capturedPreviewGeneration(action);
        Request<T> request = new Request<>(
                requestId,
                publicationVersion,
                view.document().revision(),
                previewGeneration,
                pendingOperation,
                action,
                view);
        Submission<T> submission = Objects.requireNonNull(
                actions.submit(request, this::complete),
                "action port submission");
        OperationToken existing = request.pendingOperation()
                .orElse(null);
        if (existing != null) {
            if (!(submission instanceof Pending<?> pending)
                    || !existing.equals(pending.token())) {
                throw new IllegalArgumentException(
                        "cancellation must preserve the pending export token");
            }
            replace(submission.view(), true);
            return true;
        }
        if (submission instanceof Settled<?>) {
            if (action instanceof WorkbenchAction.ExportRequested
                    || action instanceof WorkbenchAction.SaveRequested
                            && request.current().dirty()) {
                throw new IllegalArgumentException(
                        "dirty save and baked export require a typed completion");
            }
            replace(
                    submission.view(),
                    requiresLayoutRefresh(action));
            exitAfterSettled(request);
            return true;
        }
        Pending<?> pending = (Pending<?>) submission;
        validatePending(request, pending.token());
        pendingOperation = Optional.of(pending.token());
        replace(submission.view(), true);
        // 中文：先发布忙碌令牌，再启动异步操作，避免同步完成丢失。
        // English: Publish the busy token before starting async work so inline
        // completion cannot be rejected as tokenless.
        actions.pendingPublished(pending.token());
        return true;
    }

    /**
     * 中文：只接受当前精确令牌的客户端线程完成；过期、错序或类型不符的结果直接丢弃。
     *
     * <p>English: Accepts only a client-thread completion carrying the exact
     * current token. Stale, reordered, or incorrectly typed results are
     * discarded.
     */
    public boolean complete(Completion<T> completion) {
        requireOwnerThread();
        completion = Objects.requireNonNull(completion, "completion");
        if (exitRequested) {
            return false;
        }
        OperationToken token = pendingOperation.orElse(null);
        if (token == null || !token.equals(completion.token())) {
            return false;
        }
        if (!validReceipt(token, completion)) {
            return false;
        }
        pendingOperation = Optional.empty();
        replace(completion.view(), true);
        if (completion.receipt() instanceof SaveReceipt
                && completion.view().document().revision()
                        == token.submittedRevision()) {
            exit(Reason.SAVED);
        }
        return true;
    }

    /**
     * 中文：非操作型异步发布仍需匹配版本，且不得越过挂起操作。
     *
     * <p>English: Non-operation asynchronous publication must still match the
     * version and cannot cross a pending operation.
     */
    public boolean publish(
            long expectedPublicationVersion,
            WorkbenchViewModel<T> next) {
        requireOwnerThread();
        next = Objects.requireNonNull(next, "next");
        if (exitRequested
                || pendingOperation.isPresent()
                || expectedPublicationVersion != publicationVersion
                || next.document().revision()
                        < view.document().revision()
                || next.operationInProgress()) {
            return false;
        }
        replace(next, true);
        return true;
    }

    private boolean canDispatch(WorkbenchAction action) {
        if (exitRequested) {
            return false;
        }
        OperationToken token = pendingOperation.orElse(null);
        if (token != null) {
            return action instanceof WorkbenchAction.CancelRequested
                    && token.cancellable();
        }
        if (action instanceof WorkbenchAction.CancelRequested cancel) {
            return !view.dirty() || cancel.discardConfirmed();
        }
        return action instanceof WorkbenchAction.ShowMode
                || view.canSubmit();
    }

    private void exitAfterSettled(Request<T> request) {
        if (request.action() instanceof WorkbenchAction.CancelRequested) {
            exit(request.current().dirty()
                    ? Reason.DISCARDED
                    : Reason.CLEAN);
            return;
        }
        if (request.action() instanceof WorkbenchAction.SaveRequested
                && !request.current().dirty()) {
            exit(Reason.CLEAN);
        }
    }

    private void exit(Reason reason) {
        exitRequested = true;
        exits.exit(reason);
    }

    private OptionalLong capturedPreviewGeneration(
            WorkbenchAction action) {
        if (!(action instanceof WorkbenchAction.ToggleNeighbor)
                && !(action instanceof WorkbenchAction.ObserveFace)
                && !(action instanceof WorkbenchAction.CycleReceiver)
                && !(action instanceof WorkbenchAction.ClearNeighbors)) {
            return OptionalLong.empty();
        }
        Optional<PreviewViewModel.RuntimeSurface> surface =
                view.preview().flatMap(PreviewViewModel::surface);
        return surface.isEmpty()
                ? OptionalLong.empty()
                : OptionalLong.of(
                        surface.orElseThrow().generation());
    }

    private void validatePending(
            Request<T> request,
            OperationToken token) {
        OperationToken.Kind expectedKind;
        if (request.action() instanceof WorkbenchAction.SaveRequested) {
            if (!request.current().dirty()) {
                throw new IllegalArgumentException(
                        "clean save must settle without a reload");
            }
            expectedKind = OperationToken.Kind.SAVE;
        } else if (request.action()
                instanceof WorkbenchAction.ExportRequested) {
            expectedKind = OperationToken.Kind.BAKED_EXPORT;
        } else {
            throw new IllegalArgumentException(
                    "only save and baked export may remain pending");
        }
        if (token.requestId() != request.requestId()
                || token.submittedRevision()
                        != request.documentRevision()
                || token.kind() != expectedKind) {
            throw new IllegalArgumentException(
                    "pending token does not match its request");
        }
    }

    private boolean validReceipt(
            OperationToken token,
            Completion<T> completion) {
        if (completion.view().document().revision()
                < token.submittedRevision()) {
            return false;
        }
        if (completion.receipt() instanceof Failed) {
            return true;
        }
        if (completion.receipt() instanceof Cancelled) {
            return token.cancellable();
        }
        if (token.kind() == OperationToken.Kind.SAVE
                && completion.receipt()
                        instanceof SaveReceipt receipt) {
            return receipt.persistedRevision()
                            == token.submittedRevision()
                    && completion.view()
                            .document()
                            .persistedRevision()
                            == token.submittedRevision();
        }
        return token.kind()
                        == OperationToken.Kind.BAKED_EXPORT
                && completion.receipt()
                        instanceof BakedExportReceipt receipt
                && receipt.sourceRevision()
                        == token.submittedRevision();
    }

    private static boolean requiresLayoutRefresh(
            WorkbenchAction action) {
        if (action instanceof WorkbenchAction.PaintStrokeStarted
                || action instanceof WorkbenchAction.PaintPixel) {
            // 中文：笔画中的逐像素发布不重建控件树，避免画布实例被替换。
            // English: Per-pixel publications inside a stroke must not rebuild
            // the widget tree, or the active canvas instance would be replaced.
            return false;
        }
        if (action instanceof WorkbenchAction.ToggleNeighbor
                || action instanceof WorkbenchAction.ObserveFace
                || action instanceof WorkbenchAction.CycleReceiver
                || action instanceof WorkbenchAction.ClearNeighbors) {
            // 中文：预览场景直接渲染可变场景状态，无需重建布局。
            // English: The preview scene renders mutable scene state directly
            // and does not need a layout rebuild.
            return false;
        }
        return true;
    }

    private void replace(
            WorkbenchViewModel<T> next,
            boolean notifyLayout) {
        if (next == view) {
            return;
        }
        view = next;
        publicationVersion = Math.addExact(
                publicationVersion,
                1);
        if (notifyLayout) {
            layoutGeneration = Math.addExact(
                    layoutGeneration,
                    1);
            listener.accept(next);
        }
    }

    private void requireOwnerThread() {
        if (Thread.currentThread() != ownerThread) {
            throw new IllegalStateException(
                    "workbench publication must run on the client thread");
        }
    }
}
