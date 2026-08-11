package com.kltyton.autoseamblend.compat.fusion.runtime;

import com.supermartijn642.fusion.api.model.custom.quad.MutableQuad;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 中文：隔离 Fusion Fabric/NeoForge MutableQuad 染色方法的 ABI 差异。
 * English: Isolates the Fusion Fabric/NeoForge MutableQuad color-method ABI difference.
 */
public final class FusionMutableQuadHooks {
    private static final AtomicReference<Hooks> ACTIVE = new AtomicReference<>();

    private FusionMutableQuadHooks() {
    }

    public static void install(Hooks hooks) {
        Objects.requireNonNull(hooks, "hooks");
        if (!ACTIVE.compareAndSet(null, hooks)) {
            throw new IllegalStateException("Fusion mutable-quad hooks already installed");
        }
    }

    public static void color(MutableQuad quad, int argb) {
        Hooks hooks = ACTIVE.get();
        if (hooks == null) {
            throw new IllegalStateException("Fusion mutable-quad hooks are not installed");
        }
        hooks.color(Objects.requireNonNull(quad, "quad"), argb);
    }

    @FunctionalInterface
    public interface Hooks {
        void color(MutableQuad quad, int argb);
    }
}
