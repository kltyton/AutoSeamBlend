package com.kltyton.autoseamblend.engine.query;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Map;
import java.util.Objects;

/** 中文：一个状态、面、精灵和方法查询的引擎无关标识。 / English: Engine-neutral identity of one state/face/sprite/method query. */
public record ConnectionQuery(
        String blockId,
        Map<String, String> stateProperties,
        SurfaceFace face,
        String spriteId,
        ConnectionMethod requestedMethod) {
    public ConnectionQuery {
        requireText(blockId, "blockId");
        stateProperties = Map.copyOf(Objects.requireNonNull(stateProperties, "stateProperties"));
        Objects.requireNonNull(face, "face");
        requireText(spriteId, "spriteId");
        Objects.requireNonNull(requestedMethod, "requestedMethod");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
