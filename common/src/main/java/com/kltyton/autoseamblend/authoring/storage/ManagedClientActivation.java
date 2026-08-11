package com.kltyton.autoseamblend.authoring.storage;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.LongSupplier;
import net.minecraft.client.Minecraft;

/**
 * 中文：把保存后的 Managed 激活和唯一一次资源重载排队到 Minecraft owning thread。
 *
 * English: Queues post-save Managed activation and the single resource reload
 * onto Minecraft's owning thread.
 */
public final class ManagedClientActivation {
    private final Minecraft minecraft;

    public ManagedClientActivation(Minecraft minecraft) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
    }

    /**
     * 中文：后台写入线程只能调用这个边界；owning thread 在仓库选择与唯一一次 reload 前重验代次。
     *
     * English: The background writer calls only this boundary; the owning thread rechecks the
     * generation before repository selection and the single reload.
     */
    public CompletionStage<Result> activateAndReload(
            long expectedGeneration,
            LongSupplier currentGeneration) {
        return activateAndReload(
                expectedGeneration,
                currentGeneration,
                () -> {});
    }

    /**
     * 中文：在唯一一次 reload future 创建前通知公共保存状态机。
     *
     * English: Notify the common save state machine immediately before creating
     * the one reload future.
     */
    public CompletionStage<Result> activateAndReload(
            long expectedGeneration,
            LongSupplier currentGeneration,
            Runnable beforeReload) {
        if (expectedGeneration < 0) {
            throw new IllegalArgumentException("expected generation must be non-negative");
        }
        Objects.requireNonNull(currentGeneration, "currentGeneration");
        Objects.requireNonNull(beforeReload, "beforeReload");
        CompletableFuture<Result> result = new CompletableFuture<>();
        try {
            minecraft.execute(() -> activateOnOwner(
                    expectedGeneration,
                    currentGeneration,
                    beforeReload,
                    result));
        } catch (RuntimeException exception) {
            result.completeExceptionally(new IOException("PACK_ACTIVATION_QUEUE_FAILED", exception));
        }
        return result;
    }

    private void activateOnOwner(
            long expectedGeneration,
            LongSupplier currentGeneration,
            Runnable beforeReload,
            CompletableFuture<Result> result) {
        ManagedPackRepositoryOrder.Result ordering = null;
        try {
            requireCurrent(expectedGeneration, currentGeneration);
            ordering = MinecraftManagedPackRepositoryOrder.ensureSelected(minecraft);
            ManagedPackRepositoryOrder.Result completedOrdering = ordering;
            requireCurrent(expectedGeneration, currentGeneration);
            beforeReload.run();
            minecraft.reloadResourcePacks().whenComplete((ignored, failure) -> {
                Runnable complete = () -> {
                    if (failure != null) {
                        try {
                            completedOrdering.rollback();
                        } catch (RuntimeException rollbackFailure) {
                            failure.addSuppressed(rollbackFailure);
                        }
                        result.completeExceptionally(new IOException("RESOURCE_RELOAD_FAILED", failure));
                    } else {
                        result.complete(new Result(completedOrdering.selectionChanged()));
                    }
                };
                try {
                    minecraft.execute(complete);
                } catch (RuntimeException exception) {
                    try {
                        completedOrdering.rollback();
                    } catch (RuntimeException rollbackFailure) {
                        exception.addSuppressed(rollbackFailure);
                    }
                    result.completeExceptionally(new IOException("RESOURCE_RELOAD_COMPLETION_QUEUE_FAILED", exception));
                }
            });
        } catch (IOException | RuntimeException exception) {
            if (ordering != null) {
                try {
                    ordering.rollback();
                } catch (RuntimeException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
            }
            result.completeExceptionally(exception instanceof StaleGenerationException
                    ? exception
                    : new IOException("PACK_ACTIVATION_FAILED", exception));
        }
    }

    private static void requireCurrent(
            long expectedGeneration,
            LongSupplier currentGeneration) throws StaleGenerationException {
        if (currentGeneration.getAsLong() != expectedGeneration) {
            throw new StaleGenerationException();
        }
    }

    public record Result(boolean selectionChanged) {}

    private static final class StaleGenerationException extends IOException {
        private StaleGenerationException() {
            super("WORKBENCH_GENERATION_STALE");
        }
    }
}
