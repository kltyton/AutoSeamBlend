package com.kltyton.autoseamblend.compat.continuity.runtime;

import com.kltyton.autoseamblend.authoring.format.mcpatcher.MCPatcherNativeProperties.CapturedDocument;
import com.kltyton.autoseamblend.engine.ownership.NativeRuleSource;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：不含 Loader 或 Continuity API 的已接受 holder 证据。
 * English: Accepted-holder evidence without Loader or Continuity APIs.
 */
public record ContinuityAcceptedDocument(
        NativeRuleSource source,
        Optional<ConnectionMethod> requestedMethod,
        Optional<ConnectionMethod> resolvedMethod,
        List<NativeSlot> slots,
        Optional<CapturedDocument> capturedDocument) {
    public ContinuityAcceptedDocument {
        Objects.requireNonNull(source, "source");
        requestedMethod = Objects.requireNonNull(requestedMethod, "requestedMethod");
        resolvedMethod = Objects.requireNonNull(resolvedMethod, "resolvedMethod");
        if (resolvedMethod.filter(method -> method == ConnectionMethod.AUTO).isPresent()) {
            throw new IllegalArgumentException("accepted method must be concrete");
        }
        slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
        capturedDocument = Objects.requireNonNull(capturedDocument, "capturedDocument");
    }
}
