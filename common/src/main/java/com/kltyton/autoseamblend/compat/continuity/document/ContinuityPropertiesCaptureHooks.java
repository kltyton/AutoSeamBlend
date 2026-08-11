package com.kltyton.autoseamblend.compat.continuity.document;

import com.kltyton.autoseamblend.authoring.format.mcpatcher.MCPatcherNativeProperties.CapturedDocument;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 中文：共享 Continuity properties Mixin 的 Loader 接线端口。
 * English: Loader wiring port for the shared Continuity properties mixin.
 */
public final class ContinuityPropertiesCaptureHooks {
    private static final Hooks EMPTY = new Hooks() {};
    private static final AtomicReference<Hooks> ACTIVE = new AtomicReference<>(EMPTY);

    private ContinuityPropertiesCaptureHooks() {
    }

    public static void install(Hooks hooks) {
        Objects.requireNonNull(hooks, "hooks");
        if (!ACTIVE.compareAndSet(EMPTY, hooks)) {
            throw new IllegalStateException("Continuity properties capture hooks already installed");
        }
    }

    public static Optional<CapturedDocument> captureDocument(
            PackResources pack,
            ResourceLocation resourceId) {
        return Objects.requireNonNull(
                ACTIVE.get().captureDocument(pack, resourceId), "captured document");
    }

    public static void nativeSlotsCaptured(
            ResourceManager resources,
            ResourceLocation resourceId,
            List<NativeSlot> nativeSlots) {
        ACTIVE.get().nativeSlotsCaptured(
                Objects.requireNonNull(resources, "resources"),
                Objects.requireNonNull(resourceId, "resourceId"),
                List.copyOf(Objects.requireNonNull(nativeSlots, "nativeSlots")));
    }

    public interface Hooks {
        default Optional<CapturedDocument> captureDocument(
                PackResources pack,
                ResourceLocation resourceId) {
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(resourceId, "resourceId");
            return Optional.empty();
        }

        default void nativeSlotsCaptured(
                ResourceManager resources,
                ResourceLocation resourceId,
                List<NativeSlot> nativeSlots) {
        }
    }
}
