package com.kltyton.autoseamblend.engine;

import java.util.Objects;
import java.util.regex.Pattern;

/** 中文：引擎标识的唯一验证入口。 / English: Single validation entry point for engine identifiers. */
public final class EngineIdentifiers {
    private static final Pattern VALID = Pattern.compile("[a-z0-9_-]+");

    private EngineIdentifiers() {
    }

    public static String require(String engineId) {
        Objects.requireNonNull(engineId, "engineId");
        if (!VALID.matcher(engineId).matches()) {
            throw new IllegalArgumentException("invalid engine id: " + engineId);
        }
        return engineId;
    }
}
