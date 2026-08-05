package com.kltyton.autoseamblend.authoring.selector;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 中文：一次选择器操作需要的不可变方块属性与状态事实。 English: Immutable block-property and state facts required by one selector operation. */
public record NativeBlockSelectorFacts(String blockId, Map<String, List<String>> availableProperties, NativeBlockSelectorState defaultState, List<NativeBlockSelectorState> possibleStates) {
    public NativeBlockSelectorFacts {
        blockId = requireNonBlank(blockId, "blockId");
        String resolvedBlockId = blockId;
        LinkedHashMap<String, List<String>> copied = new LinkedHashMap<>();
        Objects.requireNonNull(availableProperties, "availableProperties").forEach((name, values) -> copied.put(requireNonBlank(name, "propertyName"), List.copyOf(Objects.requireNonNull(values, "propertyValues"))));
        availableProperties = Collections.unmodifiableMap(copied);
        defaultState = requireState(defaultState, resolvedBlockId, "defaultState");
        possibleStates = List.copyOf(Objects.requireNonNull(possibleStates, "possibleStates").stream().map(value -> requireState(value, resolvedBlockId, "possibleState")).toList());
    }
    private static NativeBlockSelectorState requireState(NativeBlockSelectorState state, String blockId, String label) { NativeBlockSelectorState value = Objects.requireNonNull(state, label); if (!blockId.equals(value.blockId())) throw new IllegalArgumentException(label + " must belong to blockId"); return value; }
    private static String requireNonBlank(String value, String label) { String normalized = Objects.requireNonNull(value, label).trim(); if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be blank"); return normalized; }
}
