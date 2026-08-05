package com.kltyton.autoseamblend.texture.generation;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 中文：把同一表面的预缝合全方法域隔离为稳定的“方法/原生槽位”键。
 * English: Isolates one surface's pre-stitched full-method domain with stable
 * method/native-slot keys.
 */
public final class GeneratedSlotKey {
    private static final char SEPARATOR = '/';

    private GeneratedSlotKey() {}

    public static String encode(ConnectionMethod method, String nativeSlot) {
        Objects.requireNonNull(method, "method");
        if (method == ConnectionMethod.AUTO || method == ConnectionMethod.NONE) {
            throw new IllegalArgumentException("generated slots require a concrete rendering method");
        }
        if (nativeSlot == null || nativeSlot.isBlank() || nativeSlot.indexOf(SEPARATOR) >= 0) {
            throw new IllegalArgumentException("nativeSlot must be nonblank and path-segment safe");
        }
        return method.serializedName() + SEPARATOR + nativeSlot;
    }

    public static <T> Map<String, T> select(
            Map<String, T> qualifiedSlots,
            ConnectionMethod method) {
        Objects.requireNonNull(qualifiedSlots, "qualifiedSlots");
        Objects.requireNonNull(method, "method");
        String prefix = method.serializedName() + SEPARATOR;
        LinkedHashMap<String, T> selected = new LinkedHashMap<>();
        qualifiedSlots.forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                selected.put(key.substring(prefix.length()), value);
            }
        });
        return Collections.unmodifiableMap(selected);
    }
}
