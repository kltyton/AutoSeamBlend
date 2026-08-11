package com.kltyton.autoseamblend.compat.continuity.runtime;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import me.pepperbell.continuity.client.model.QuadProcessors;

/**
 * 中文：为共享 Continuity holder 列表 Mixin 提供 Loader 运行时端口。
 * English: Provides the Loader runtime port for the shared Continuity holder-list mixin.
 */
public final class ContinuityProcessorListHooks {
    private static final Hooks EMPTY = new Hooks() {};
    private static final AtomicReference<Hooks> ACTIVE = new AtomicReference<>(EMPTY);

    private ContinuityProcessorListHooks() {
    }

    public static void install(Hooks hooks) {
        Objects.requireNonNull(hooks, "hooks");
        if (!ACTIVE.compareAndSet(EMPTY, hooks)) {
            throw new IllegalStateException("Continuity processor-list hooks already installed");
        }
    }

    public static void begin() {
        ACTIVE.get().begin();
    }

    public static void complete(List<QuadProcessors.ProcessorHolder> holders) {
        ACTIVE.get().complete(Objects.requireNonNull(holders, "holders"));
    }

    public interface Hooks {
        default void begin() {
        }

        default void complete(List<QuadProcessors.ProcessorHolder> holders) {
        }
    }
}
