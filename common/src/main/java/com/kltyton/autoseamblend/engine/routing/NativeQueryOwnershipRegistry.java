package com.kltyton.autoseamblend.engine.routing;

import com.kltyton.autoseamblend.engine.EngineDescriptor;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 中文：按稳定引擎 ID 保存原生精确查询提供器，并在读取时校验已验证引擎身份。
 *
 * <p>English: Stores native exact-query providers by stable engine ID and validates the selected
 * engine identity when a provider is read.
 */
public final class NativeQueryOwnershipRegistry {
    private final ConcurrentHashMap<String, NativeQueryOwnershipProvider> providers =
            new ConcurrentHashMap<>();

    public void register(NativeQueryOwnershipProvider provider) {
        Objects.requireNonNull(provider, "provider");
        NativeQueryOwnershipProvider previous =
                providers.putIfAbsent(provider.engineId(), provider);
        if (previous != null
                && previous != provider
                && !previous.getClass().equals(provider.getClass())) {
            throw new IllegalStateException(
                    "native query ownership provider already registered: " + provider.engineId());
        }
    }

    public boolean registered(String engineId) {
        return providers.containsKey(Objects.requireNonNull(engineId, "engineId"));
    }

    public NativeQueryOwnershipProvider require(EngineDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        NativeQueryOwnershipProvider provider = providers.get(descriptor.engineId());
        if (provider == null) {
            throw new IllegalStateException(
                    "validated engine has no native query provider: " + descriptor.engineId());
        }
        if (!provider.engineId().equals(descriptor.engineId())
                || provider.family() != descriptor.family()) {
            throw new IllegalStateException(
                    "native query provider identity does not match validated engine: "
                            + descriptor.engineId());
        }
        return provider;
    }
}
