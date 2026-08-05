package com.kltyton.autoseamblend.compat.continuity.document;

import com.kltyton.autoseamblend.authoring.format.mcpatcher.MCPatcherAuthorExtension;
import com.kltyton.autoseamblend.authoring.format.mcpatcher.MCPatcherNativeProperties.CapturedDocument;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import java.util.List;
import java.util.Optional;

/**
 * 中文：已接受 Continuity properties 上的作者来源、槽位与原始文档证据桥。
 *
 * English: Authoring provenance, slot, and captured-document evidence bridge on accepted
 * Continuity properties.
 */
public interface ContinuityPropertiesExtensionCarrier {
    ContinuityPropertiesExtensionState autoseamblend$extensionState();

    default Optional<MCPatcherAuthorExtension> autoseamblend$authorExtension() {
        return autoseamblend$extensionState().authorExtension();
    }

    default List<NativeSlot> autoseamblend$nativeSlots() {
        return autoseamblend$extensionState().nativeSlots();
    }

    default Optional<CapturedDocument> autoseamblend$capturedDocument() {
        return autoseamblend$extensionState().capturedDocument();
    }
}
