package com.kltyton.autoseamblend.compat.continuity.document;

import com.kltyton.autoseamblend.compat.continuity.document.ContinuityPropertiesCaptureHooks.Hooks;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
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
                    ResourceLocation,
                    NativeDocument>
            DOCUMENTS = new ConcurrentHashMap<>();

    private ContinuityNativeDocumentCatalog() {}

    @Override
    public void nativeSlotsCaptured(
            ResourceManager resources,
            ResourceLocation resourceId,
            List<NativeSlot> nativeSlots) {
        record(resources, resourceId, nativeSlots);
    }

    public static synchronized void record(
            ResourceManager resources,
            ResourceLocation resourceId,
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
                    ResourceLocation resourceId) {
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(resourceId, "resourceId");
        if (owner != resources) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                DOCUMENTS.get(resourceId));
    }

    public record NativeDocument(
            ResourceLocation resourceId,
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
