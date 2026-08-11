package com.kltyton.autoseamblend.engine;

import java.util.Objects;

/** 中文：稳定标识；外部引擎类绝不会越过此边界。 / English: Stable identity; external engine classes never cross this boundary. */
public record EngineDescriptor(
        String engineId,
        EngineFamily family,
        String formatId,
        String modId,
        String expectedVersion,
        String hookContract) {
    public EngineDescriptor {
        requireText(engineId, "engineId");
        Objects.requireNonNull(family, "family");
        requireText(formatId, "formatId");
        requireText(modId, "modId");
        requireText(expectedVersion, "expectedVersion");
        requireText(hookContract, "hookContract");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
