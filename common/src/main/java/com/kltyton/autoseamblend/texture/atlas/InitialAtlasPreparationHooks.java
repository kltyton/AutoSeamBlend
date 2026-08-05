package com.kltyton.autoseamblend.texture.atlas;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 中文：为共享 Atlas 初始准备 Mixin 提供 Loader 生命周期端口。
 * English: Provides the Loader lifecycle port for the shared initial-atlas preparation mixin.
 */
public final class InitialAtlasPreparationHooks {
    private static final Preparation EMPTY =
            (resources, executor) -> CompletableFuture.completedFuture(null);
    private static final AtomicReference<Preparation> ACTIVE =
            new AtomicReference<>(EMPTY);

    private InitialAtlasPreparationHooks() {
    }

    public static void install(Preparation preparation) {
        Objects.requireNonNull(preparation, "preparation");
        if (!ACTIVE.compareAndSet(EMPTY, preparation)) {
            throw new IllegalStateException("initial atlas preparation hook already installed");
        }
    }

    public static CompletableFuture<Void> prepare(
            ResourceManager resources,
            Executor executor) {
        return Objects.requireNonNull(
                ACTIVE.get().prepare(
                        Objects.requireNonNull(resources, "resources"),
                        Objects.requireNonNull(executor, "executor")),
                "preparation future");
    }

    @FunctionalInterface
    public interface Preparation {
        CompletableFuture<Void> prepare(ResourceManager resources, Executor executor);
    }
}
