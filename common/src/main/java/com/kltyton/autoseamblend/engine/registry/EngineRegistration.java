package com.kltyton.autoseamblend.engine.registry;

import com.kltyton.autoseamblend.engine.EngineAdapter;
import com.kltyton.autoseamblend.engine.EngineDescriptor;
import com.kltyton.autoseamblend.engine.capability.CapabilityMatrix;
import java.util.Objects;
import java.util.Optional;

/** 中文：不可变验证结果；缺少适配器表示其外部类型从未加载。 / English: Immutable validation result; absent adapter means its external types were never loaded. */
public record EngineRegistration(
        EngineDescriptor descriptor,
        Optional<String> installedVersion,
        CapabilityMatrix capabilities,
        EngineStatus status,
        Optional<EngineAdapter> adapter) {
    public EngineRegistration {
        Objects.requireNonNull(descriptor, "descriptor");
        installedVersion = Objects.requireNonNull(installedVersion, "installedVersion");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(status, "status");
        adapter = Objects.requireNonNull(adapter, "adapter");
        if (status.selectable() && (adapter.isEmpty() || !capabilities.isComplete())) {
            throw new IllegalArgumentException("product-selectable engine requires an adapter and all 52 cells");
        }
    }

    public boolean discovered() {
        return installedVersion.isPresent();
    }

    public boolean versionValid() {
        return discovered() && status.state() != EngineStatus.State.INVALID_VERSION;
    }

    public boolean adapterConstructable() {
        return adapter.isPresent();
    }

    public boolean productSelectable() {
        return status.selectable() && adapterConstructable() && capabilities.isComplete();
    }
}
