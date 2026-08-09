package com.kltyton.autoseamblend.authoring.selector;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 中文：用字符串属性值表示的不可变方块状态。 English: Immutable block state represented by string property values. */
public record NativeBlockSelectorState(String blockId, Map<String, String> values) {
    public NativeBlockSelectorState {
        blockId = Objects.requireNonNull(blockId, "blockId").trim();
        if (blockId.isEmpty()) throw new IllegalArgumentException("blockId must not be blank");
        LinkedHashMap<String, String> copied = new LinkedHashMap<>();
        Objects.requireNonNull(values, "values").forEach((name, value) -> copied.put(requireNonBlank(name, "propertyName"), requireNonBlank(value, "propertyValue")));
        values = Collections.unmodifiableMap(copied);
    }
    private static String requireNonBlank(String value, String label) { String normalized = Objects.requireNonNull(value, label).trim(); if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be blank"); return normalized; }
}
