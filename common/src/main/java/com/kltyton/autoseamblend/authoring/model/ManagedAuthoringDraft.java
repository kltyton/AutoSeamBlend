package com.kltyton.autoseamblend.authoring.model;

import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDraftFields;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;

/**
 * 中文：跨 Loader 共享的 Managed 创作草稿字段。
 *
 * English: Loader-neutral fields for one Managed authoring draft.
 */
public record ManagedAuthoringDraft(
        String targetBlockId,
        String sourceTextureId,
        String originalModelId,
        ConnectionMethod requestedMethod,
        ConnectionMethod resolvedMethod,
        boolean compatibility,
        boolean pane) implements WorkbenchDraftFields {
    public ManagedAuthoringDraft {
        targetBlockId = nonBlank(targetBlockId, "targetBlockId");
        sourceTextureId = nonBlank(sourceTextureId, "sourceTextureId");
        originalModelId = nonBlank(originalModelId, "originalModelId");
        requestedMethod = Objects.requireNonNull(
                requestedMethod,
                "requestedMethod");
        resolvedMethod = Objects.requireNonNull(
                resolvedMethod,
                "resolvedMethod");
        if (resolvedMethod == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException(
                    "resolvedMethod must be concrete");
        }
        if (requestedMethod != ConnectionMethod.AUTO
                && requestedMethod != resolvedMethod) {
            throw new IllegalArgumentException(
                    "manual method must equal resolved method");
        }
    }

    private static String nonBlank(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }
}
