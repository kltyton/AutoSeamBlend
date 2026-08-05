package com.kltyton.autoseamblend.compat.continuity.runtime;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.selection.method.MethodSlotDomain;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 中文：按 Continuity 方法域校验并排列生成槽位；Loader 只负责把结果接到原生 sprite 数组。
 *
 * English: Validates and orders generated slots by the Continuity method domain; loaders only
 * adapt the result to native sprite arrays.
 */
public final class ContinuitySlotLayout {
    private ContinuitySlotLayout() {}

    public static int slotCount(ConnectionMethod method) {
        return MethodSlotDomain.of(Objects.requireNonNull(method, "method"))
                .slots()
                .size();
    }

    public static <T> List<T> full(
            ConnectionMethod method,
            Map<String, T> generated) {
        Objects.requireNonNull(method, "method");
        Map<String, T> values = Map.copyOf(
                Objects.requireNonNull(generated, "generated"));
        int size = slotCount(method);
        ArrayList<T> ordered = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot++) {
            T value = values.get(Integer.toString(slot));
            if (value == null) {
                throw new IllegalStateException(
                        "CONTINUITY_GENERATED_SLOT_UNAVAILABLE:" + slot);
            }
            ordered.add(value);
        }
        return List.copyOf(ordered);
    }

    public static <T> Map<Integer, T> missing(
            List<Integer> slots,
            Map<String, T> generated) {
        List<Integer> required = List.copyOf(
                Objects.requireNonNull(slots, "slots"));
        Map<String, T> values = Map.copyOf(
                Objects.requireNonNull(generated, "generated"));
        LinkedHashMap<Integer, T> result = new LinkedHashMap<>();
        for (int slot : required) {
            T value = values.get(Integer.toString(slot));
            if (value != null) {
                result.put(slot, value);
            }
        }
        return Map.copyOf(result);
    }
}
