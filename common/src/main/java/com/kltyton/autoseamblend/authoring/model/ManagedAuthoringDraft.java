package com.kltyton.autoseamblend.authoring.model;

import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDraftFields;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;

/**
 * 中文：跨 Loader 共享的 Managed 创作草稿字段。
 *
 * English: Loader-neutral fields for one Managed authoring draft.
 *
 * @param targetBlockId 中文：目标方块 ID。 / English: Target block id.
 * @param sourceTextureId 中文：源纹理 ID。 / English: Source texture id.
 * @param originalModelId 中文：原始模型 ID。 / English: Original model id.
 * @param requestedMethod 中文：请求的连接方法。 / English: Requested connection method.
 * @param resolvedMethod 中文：解析后的具体方法。 / English: Resolved concrete method.
 * @param compatibility 中文：是否按兼容策略补齐。 / English: Whether completion follows the compatibility policy.
 * @param pane 中文：是否应用玻璃板语义。 / English: Whether pane semantics apply.
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
