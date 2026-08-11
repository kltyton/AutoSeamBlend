package com.kltyton.autoseamblend.engine.plan;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.selection.method.MethodSlotDomain;
import java.util.Objects;
import java.util.function.Function;

/** 中文：一个公开方法到引擎原生方法合同的映射。 / English: One public method mapped onto an engine-native method contract. */
public record NativeMethodMapping(
        ConnectionMethod method,
        String nativeMethodId,
        int slotCount,
        Behavior behavior) {
    public NativeMethodMapping {
        if (method == null || behavior == null) throw new NullPointerException();
        if (nativeMethodId == null || nativeMethodId.isBlank()) {
            throw new IllegalArgumentException("nativeMethodId must not be blank");
        }
        if (slotCount < 0) throw new IllegalArgumentException("slotCount must be non-negative");
    }

    /**
     * 中文：统一 AUTO、NONE 与普通原生方法的映射合同。
     * English: Uniform mapping contract for AUTO, NONE, and regular native methods.
     */
    public static NativeMethodMapping standard(
            ConnectionMethod method,
            Function<ConnectionMethod, String> nativeMethodId) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(nativeMethodId, "nativeMethodId");
        if (method == ConnectionMethod.AUTO) {
            return new NativeMethodMapping(
                    method,
                    "resolve-auto-first",
                    0,
                    Behavior.RESOLVE_AUTO_FIRST);
        }
        if (method == ConnectionMethod.NONE) {
            return new NativeMethodMapping(method, "passthrough", 0, Behavior.PASSTHROUGH);
        }
        return new NativeMethodMapping(
                method,
                nativeMethodId.apply(method),
                MethodSlotDomain.of(method).slots().size(),
                Behavior.NATIVE);
    }

    public enum Behavior {
        RESOLVE_AUTO_FIRST,
        NATIVE,
        PASSTHROUGH
    }
}
