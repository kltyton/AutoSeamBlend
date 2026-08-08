package com.kltyton.autoseamblend.compat.continuity.document;

import com.kltyton.autoseamblend.compat.continuity.document.ContinuityPropertiesCaptureHooks.Hooks;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 中文：按本轮资源管理器保存 NeoContinuity 已解析的逐槽证据，供可视化编辑器复用原生解析结果。
 *
 * English:
 * Retains NeoContinuity's parsed per-slot evidence for the active resource
 * manager so the visual editor can reuse native parsing results.
 */
public final class ContinuityNativeDocumentCatalog implements Hooks {
    public static final ContinuityNativeDocumentCatalog INSTANCE =
            new ContinuityNativeDocumentCatalog();
    private static ResourceManager owner;
    private static final ConcurrentHashMap<
                    Identifier,
                    NativeDocument>
            DOCUMENTS = new ConcurrentHashMap<>();

    private ContinuityNativeDocumentCatalog() {}

    @Override
    public void nativeSlotsCaptured(
            ResourceManager resources,
            Identifier resourceId,
            List<NativeSlot> nativeSlots) {
        record(resources, resourceId, nativeSlots);
    }

    public static synchronized void record(
            ResourceManager resources,
            Identifier resourceId,
            List<NativeSlot> slots) {
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(resourceId, "resourceId");
        slots = List.copyOf(
                Objects.requireNonNull(slots, "slots"));
        if (owner != resources) {
            owner = resources;
            DOCUMENTS.clear();
        }
        DOCUMENTS.put(
                resourceId,
                new NativeDocument(
                        resourceId,
                        slots));
    }

    public static synchronized Optional<NativeDocument>
            document(
                    ResourceManager resources,
                    Identifier resourceId) {
        return lookup(resources, resourceId)
                .document();
    }

    /**
     * 中文：单次原子查询结果；ownerMatches 表示目录归属的资源管理器与查询一致，
     * document 为命中文档（可能为空），两个字段来自同一次同步查询。
     *
     * English: Result of one atomic lookup; ownerMatches reports whether the
     * catalog's current resource manager matches the query, and document is the
     * hit (possibly empty). Both fields come from the same synchronized query.
     */
    public record NativeDocumentLookup(
            boolean ownerMatches,
            Optional<NativeDocument> document) {
        public NativeDocumentLookup {
            document = Objects.requireNonNull(
                    document,
                    "document");
        }
    }

    /**
     * 中文：在一次同步临界区内同时返回所有权与文档命中，避免调用方分两次观察不一致状态。
     *
     * English: Returns ownership and document hit inside one synchronized
     * section so callers never observe a split state across two queries.
     */
    public static synchronized NativeDocumentLookup
            lookup(
                    ResourceManager resources,
                    Identifier resourceId) {
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(resourceId, "resourceId");
        boolean ownerMatches = owner == resources;
        Optional<NativeDocument> hit = ownerMatches
                ? Optional.ofNullable(
                        DOCUMENTS.get(resourceId))
                : Optional.empty();
        return new NativeDocumentLookup(
                ownerMatches,
                hit);
    }

    public record NativeDocument(
            Identifier resourceId,
            List<NativeSlot> slots) {
        public NativeDocument {
            Objects.requireNonNull(
                    resourceId,
                    "resourceId");
            slots = List.copyOf(
                    Objects.requireNonNull(
                            slots,
                            "slots"));
        }
    }
}
