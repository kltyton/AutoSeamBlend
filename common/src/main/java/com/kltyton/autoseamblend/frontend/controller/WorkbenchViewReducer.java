package com.kltyton.autoseamblend.frontend.controller;

import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDraftFields;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchMode;
import com.kltyton.autoseamblend.frontend.model.NativePropertiesViewModel;
import com.kltyton.autoseamblend.frontend.model.PaintViewModel;
import com.kltyton.autoseamblend.frontend.model.PreviewViewModel;
import com.kltyton.autoseamblend.frontend.model.TargetRowView;
import com.kltyton.autoseamblend.frontend.model.WorkbenchViewModel;
import com.kltyton.autoseamblend.frontend.port.WorkbenchActionPort;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import net.minecraft.network.chat.Component;

/**
 * 中文：工作台视图的纯归约函数；Loader 只提供原生文档/预览投影。
 *
 * English: Pure workbench-view reducers. Loaders provide only native document
 * and preview projections; session-state transitions stay here.
 */
public final class WorkbenchViewReducer {
    private WorkbenchViewReducer() {}

    public static <T extends WorkbenchDraftFields>
            WorkbenchViewModel<T> preview(
                    WorkbenchViewModel<T> source,
                    WorkbenchAction action) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(action, "action");
        if (action instanceof WorkbenchAction.ToggleNeighbor toggle) {
            return updatePreview(source, current -> {
                LinkedHashSet<PreviewViewModel.NeighborPosition> next =
                        new LinkedHashSet<>(current.neighbors());
                if (!next.remove(toggle.position())) {
                    next.add(toggle.position());
                }
                return withPreview(
                        current,
                        Set.copyOf(next),
                        current.observedFace(),
                        current.receiverVariant());
            });
        }
        if (action instanceof WorkbenchAction.ObserveFace observe) {
            return updatePreview(source, current -> withPreview(
                    current,
                    current.neighbors(),
                    observe.face(),
                    current.receiverVariant()));
        }
        if (action instanceof WorkbenchAction.CycleReceiver) {
            return updatePreview(source, current -> withPreview(
                    current,
                    current.neighbors(),
                    current.observedFace(),
                    Math.addExact(current.receiverVariant(), 1)));
        }
        if (action instanceof WorkbenchAction.ClearNeighbors) {
            return updatePreview(source, current -> withPreview(
                    current,
                    Set.of(),
                    current.observedFace(),
                    current.receiverVariant()));
        }
        return source;
    }

    public static <T extends WorkbenchDraftFields>
            WorkbenchViewModel<T> updatePreview(
                    WorkbenchViewModel<T> source,
                    UnaryOperator<PreviewViewModel> update) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(update, "update");
        PreviewViewModel current = source.preview().orElseThrow(
                () -> new IllegalStateException("PREVIEW_UNAVAILABLE"));
        return copy(
                source,
                source.document(),
                source.mode(),
                source.targets(),
                source.availableTargets(),
                source.selectedEntryKey(),
                Optional.of(update.apply(current)),
                source.paint(),
                source.properties(),
                source.operationStatus(),
                true,
                false);
    }

    public static <T extends WorkbenchDraftFields>
            WorkbenchViewModel<T> status(
                    WorkbenchViewModel<T> source,
                    Component operationStatus,
                    boolean actionsEnabled,
                    boolean operationInProgress) {
        return copy(
                source,
                source.document(),
                source.mode(),
                source.targets(),
                source.availableTargets(),
                source.selectedEntryKey(),
                source.preview(),
                source.paint(),
                source.properties(),
                operationStatus,
                actionsEnabled,
                operationInProgress);
    }

    public static <T extends WorkbenchDraftFields>
            WorkbenchViewModel<T> copy(
                    WorkbenchViewModel<T> source,
                    com.kltyton.autoseamblend.authoring.workbench.WorkbenchDocument<T> document,
                    WorkbenchMode mode,
                    List<TargetRowView> targets,
                    List<TargetRowView> availableTargets,
                    Optional<String> selectedEntryKey,
                    Optional<PreviewViewModel> preview,
                    Optional<PaintViewModel> paint,
                    Optional<NativePropertiesViewModel> properties,
                Component operationStatus,
                boolean actionsEnabled,
                boolean operationInProgress) {
        return copy(
                source,
                document,
                mode,
                targets,
                availableTargets,
                source.propertyCandidates(),
                selectedEntryKey,
                preview,
                paint,
                properties,
                operationStatus,
                actionsEnabled,
                operationInProgress);
    }

    /** 中文：候选扫描完成时替换属性候选快照；其余视图字段保持同一发布。 / English: Replaces the property-candidate snapshot when a scan completes while preserving the rest of one publication. */
    public static <T extends WorkbenchDraftFields>
            WorkbenchViewModel<T> copy(
                    WorkbenchViewModel<T> source,
                    com.kltyton.autoseamblend.authoring.workbench.WorkbenchDocument<T> document,
                    WorkbenchMode mode,
                    List<TargetRowView> targets,
                    List<TargetRowView> availableTargets,
                    List<NativePropertiesViewModel.SelectorCandidate> propertyCandidates,
                    Optional<String> selectedEntryKey,
                    Optional<PreviewViewModel> preview,
                    Optional<PaintViewModel> paint,
                    Optional<NativePropertiesViewModel> properties,
                    Component operationStatus,
                    boolean actionsEnabled,
                    boolean operationInProgress) {
        Objects.requireNonNull(source, "source");
        return new WorkbenchViewModel<>(
                Objects.requireNonNull(document, "document"),
                Objects.requireNonNull(mode, "mode"),
                List.copyOf(Objects.requireNonNull(targets, "targets")),
                List.copyOf(Objects.requireNonNull(availableTargets, "availableTargets")),
                List.copyOf(Objects.requireNonNull(propertyCandidates, "propertyCandidates")),
                Objects.requireNonNull(selectedEntryKey, "selectedEntryKey"),
                Objects.requireNonNull(preview, "preview"),
                Objects.requireNonNull(paint, "paint"),
                Objects.requireNonNull(properties, "properties"),
                source.engineStatus(),
                Objects.requireNonNull(operationStatus, "operationStatus"),
                actionsEnabled,
                operationInProgress);
    }

    private static PreviewViewModel withPreview(
            PreviewViewModel source,
            Set<PreviewViewModel.NeighborPosition> neighbors,
            net.minecraft.core.Direction observedFace,
            int receiverVariant) {
        return new PreviewViewModel(
                source.surface(),
                source.unavailableReason(),
                neighbors,
                observedFace,
                receiverVariant);
    }
}
