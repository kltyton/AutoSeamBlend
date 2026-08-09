package com.kltyton.autoseamblend.engine.registry;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 中文：统一引擎注册表的惰性构造与一次性发布；Loader 仅提供本地构造输入。
 *
 * English: Owns lazy engine-registry construction and one-time publication; loaders only provide
 * their local construction inputs.
 */
public final class EngineRegistryInitialization<T> {
    private final Supplier<EngineRegistrySnapshot> registrySupplier;
    private final Function<EngineRegistrySnapshot, T> stateFactory;
    private final AtomicReference<T> active = new AtomicReference<>();

    public EngineRegistryInitialization(
            Supplier<EngineRegistrySnapshot> registrySupplier,
            Function<EngineRegistrySnapshot, T> stateFactory) {
        this.registrySupplier = Objects.requireNonNull(registrySupplier, "registrySupplier");
        this.stateFactory = Objects.requireNonNull(stateFactory, "stateFactory");
    }

    public T initialize() {
        T current = active.get();
        if (current != null) {
            return current;
        }
        EngineRegistrySnapshot registry = Objects.requireNonNull(
                registrySupplier.get(),
                "registrySupplier returned null");
        T created = Objects.requireNonNull(
                stateFactory.apply(registry),
                "stateFactory returned null");
        active.compareAndSet(null, created);
        return active.get();
    }

    public T current() {
        T state = active.get();
        return state == null ? initialize() : state;
    }

    public boolean initialized() {
        return active.get() != null;
    }
}
