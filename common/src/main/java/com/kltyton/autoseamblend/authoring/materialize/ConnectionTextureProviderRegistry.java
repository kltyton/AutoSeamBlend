package com.kltyton.autoseamblend.authoring.materialize;

import com.kltyton.autoseamblend.engine.EngineFamily;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 中文：按引擎族隔离连接纹理来源的并发注册表；具体 Provider ABI 仍由 Loader 适配器定义。
 *
 * English: Concurrent engine-family registry for connection-texture sources;
 * the concrete Provider ABI remains defined by the Loader adapter.
 */
public final class ConnectionTextureProviderRegistry<P> {
    private final ConcurrentHashMap<EngineFamily, P> providers =
            new ConcurrentHashMap<>();

    public void register(
            EngineFamily family,
            P provider) {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(provider, "provider");
        P previous = providers.putIfAbsent(family, provider);
        if (previous != null
                && previous != provider
                && !previous.getClass().equals(provider.getClass())) {
            throw new IllegalStateException(
                    "connection texture source provider already registered for "
                            + family.formatId());
        }
    }

    public boolean available(EngineFamily family) {
        return providers.containsKey(
                Objects.requireNonNull(family, "family"));
    }

    public Optional<P> find(EngineFamily family) {
        return Optional.ofNullable(providers.get(
                Objects.requireNonNull(family, "family")));
    }
}
