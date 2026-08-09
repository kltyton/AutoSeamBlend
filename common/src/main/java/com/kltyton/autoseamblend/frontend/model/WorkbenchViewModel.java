package com.kltyton.autoseamblend.frontend.model;

import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDocument;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDraftFields;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchMode;
import com.kltyton.autoseamblend.frontend.model.TargetRowView;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.chat.Component;

/** 中文：UILib 工作台一次发布的完整不可变视图。 / English: Complete immutable view published once to the UILib workbench. */
public record WorkbenchViewModel<T extends WorkbenchDraftFields>(
        WorkbenchDocument<T> document,
        WorkbenchMode mode,
        List<TargetRowView> targets,
        List<TargetRowView> availableTargets,
        List<NativePropertiesViewModel.SelectorCandidate> propertyCandidates,
        Optional<String> selectedEntryKey,
        Optional<PreviewViewModel> preview,
        Optional<PaintViewModel> paint,
        Optional<NativePropertiesViewModel> properties,
        Component engineStatus,
        Component operationStatus,
        boolean actionsEnabled,
        boolean operationInProgress) {
    public WorkbenchViewModel {
        document = Objects.requireNonNull(document, "document");
        mode = Objects.requireNonNull(mode, "mode");
        targets = List.copyOf(
                Objects.requireNonNull(targets, "targets"));
        availableTargets = List.copyOf(
                Objects.requireNonNull(
                        availableTargets,
                        "availableTargets"));
        propertyCandidates = List.copyOf(Objects.requireNonNull(
                propertyCandidates, "propertyCandidates"));
        selectedEntryKey = Objects.requireNonNull(
                selectedEntryKey,
                "selectedEntryKey");
        preview = Objects.requireNonNull(preview, "preview");
        paint = Objects.requireNonNull(paint, "paint");
        properties = Objects.requireNonNull(
                properties,
                "properties");
        engineStatus = Objects.requireNonNull(
                engineStatus,
                "engineStatus");
        operationStatus = Objects.requireNonNull(
                operationStatus,
                "operationStatus");
        if (mode != WorkbenchMode.TARGET_LIBRARY
                && selectedEntryKey.isEmpty()) {
            throw new IllegalArgumentException(
                    "non-library mode requires a selected target");
        }
        if (operationInProgress && actionsEnabled) {
            throw new IllegalArgumentException(
                    "in-progress operations must disable actions");
        }
    }

    public boolean dirty() {
        return document.dirty();
    }

    public boolean canSubmit() {
        return actionsEnabled && !operationInProgress;
    }
}
