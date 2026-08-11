package com.kltyton.autoseamblend.authoring.preview;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 中文：保存进程级原生预览能力。 / English: Stores process-lifetime native preview capabilities. */
public final class PreviewProviderRegistry {
    private static final ConcurrentHashMap<String, PreviewProvider> PROVIDERS =
            new ConcurrentHashMap<>();

    private PreviewProviderRegistry() {}

    public static void register(PreviewProvider provider) {
        PreviewProvider checked = Objects.requireNonNull(provider, "provider");
        PreviewProvider previous = PROVIDERS.putIfAbsent(
                checked.engineId(),
                checked);
        if (previous != null
                && previous != checked
                && !previous.getClass().equals(checked.getClass())) {
            throw new IllegalStateException(
                    "preview provider already registered: " + checked.engineId());
        }
    }

    public static boolean available(String engineId) {
        return PROVIDERS.containsKey(Objects.requireNonNull(engineId, "engineId"));
    }

    public static Optional<PreviewProvider> find(String engineId) {
        return Optional.ofNullable(
                PROVIDERS.get(Objects.requireNonNull(engineId, "engineId")));
    }
}
