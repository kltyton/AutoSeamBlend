package com.kltyton.autoseamblend.compat.continuity.document;

import com.kltyton.autoseamblend.authoring.format.mcpatcher.MCPatcherAuthorExtension;
import com.kltyton.autoseamblend.authoring.format.mcpatcher.MCPatcherNativeProperties.CapturedDocument;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：一个 Continuity properties 实例附带的共享扩展状态。
 *
 * English: Shared extension state attached to one Continuity properties instance.
 */
public final class ContinuityPropertiesExtensionState {
    private Optional<MCPatcherAuthorExtension> authorExtension = Optional.empty();
    private List<NativeSlot> nativeSlots = List.of();
    private Optional<CapturedDocument> capturedDocument = Optional.empty();

    public Optional<MCPatcherAuthorExtension> authorExtension() {
        return authorExtension;
    }

    public List<NativeSlot> nativeSlots() {
        return nativeSlots;
    }

    public Optional<CapturedDocument> capturedDocument() {
        return capturedDocument;
    }

    public void captureAuthorExtension(Optional<MCPatcherAuthorExtension> extension) {
        authorExtension = Objects.requireNonNull(extension, "extension");
    }

    public void captureNativeSlots(List<NativeSlot> slots) {
        nativeSlots = List.copyOf(Objects.requireNonNull(slots, "slots"));
    }

    public void captureDocument(Optional<CapturedDocument> document) {
        capturedDocument = Objects.requireNonNull(document, "document");
    }
}
