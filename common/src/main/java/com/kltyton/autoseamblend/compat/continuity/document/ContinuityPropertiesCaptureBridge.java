package com.kltyton.autoseamblend.compat.continuity.document;

import com.kltyton.autoseamblend.authoring.format.mcpatcher.MCPatcherExtensionContext;
import com.kltyton.autoseamblend.authoring.format.mcpatcher.MCPatcherNativeProperties.CapturedDocument;
import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityNativeSlotEvidence;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import me.pepperbell.continuity.client.properties.BaseCtmProperties;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 中文：集中保存两端 BaseCtmProperties 的作者来源、原生槽位和捕获文档状态更新。
 * English: Centralizes author provenance, native-slot, and captured-document state updates for
 * BaseCtmProperties on both loaders.
 */
public final class ContinuityPropertiesCaptureBridge {
    private ContinuityPropertiesCaptureBridge() {}

    /**
     * 中文：保存当前扩展帧并以空文档开始；Loader 可在其边界补充受控文档捕获。
     * English: Saves the current extension frame with an empty document; a loader may add its
     * bounded document capture at its boundary.
     */
    public static void captureAuthorState(
            ContinuityPropertiesExtensionState state) {
        captureAuthorState(state, Optional.empty());
    }

    /**
     * 中文：在原生 properties 构造完成后保存当前扩展帧和可选原始文档。
     * English: Saves the current extension frame and optional source document after native
     * properties construction.
     */
    public static void captureAuthorState(
            ContinuityPropertiesExtensionState state,
            Optional<CapturedDocument> capturedDocument) {
        Objects.requireNonNull(state, "state");
        state.captureAuthorExtension(
                Objects.requireNonNull(
                        MCPatcherExtensionContext.current(),
                        "current extension frame"));
        state.captureDocument(
                Objects.requireNonNull(capturedDocument, "capturedDocument"));
    }

    /**
     * 中文：只复用 Continuity 已解析的 sprite id 证据，不重新解释 properties。
     * English: Reuses Continuity's parsed sprite-id evidence without reinterpreting properties.
     */
    public static List<NativeSlot> captureNativeSlots(
            ContinuityPropertiesExtensionState state,
            BaseCtmProperties properties,
            ResourceManager resources) {
        Objects.requireNonNull(state, "state");
        List<NativeSlot> nativeSlots = ContinuityNativeSlotEvidence.capture(
                Objects.requireNonNull(properties, "properties"),
                Objects.requireNonNull(resources, "resources"));
        state.captureNativeSlots(nativeSlots);
        return nativeSlots;
    }
}
