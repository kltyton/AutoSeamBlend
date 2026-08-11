package com.kltyton.autoseamblend.inference;

import java.util.Objects;
import java.util.function.Function;

/**
 * 中文：统一模型纹理槽位语法校验、槽位提取和查找失败码；具体 Material 查找由 Loader 端口提供。
 * <p>
 * English: Common texture-slot syntax validation, slot extraction, and lookup failure codes;
 * concrete Material lookup is supplied by a Loader port.
 */
public final class SurfaceMaterialResolution {
    private SurfaceMaterialResolution() {
    }

    /**
     * 中文：使用 Loader 提供的槽位查找函数解析一个模型材质引用。
     * <p>
     * English: Resolves one model material reference using a Loader-supplied slot lookup.
     */
    public static <T> Result<T> resolve(
            String reference,
            Function<String, T> slotLookup) {
        Objects.requireNonNull(slotLookup, "slotLookup");
        if (reference == null
                || !reference.startsWith("#")
                || reference.length() == 1) {
            return Result.failure(reference, Failure.UNKNOWN_TEXTURE_REFERENCE);
        }
        String slot = reference.substring(1);
        T material = slotLookup.apply(slot);
        return material == null
                ? Result.failure(reference, Failure.UNRESOLVED_TEXTURE_SLOT)
                : Result.success(reference, slot, material);
    }

    /**
     * 中文：解析并要求一个材质；失败时抛出带稳定诊断码的公共异常。
     * English: Resolves and requires one material, throwing a shared exception with the stable
     * diagnostic code on failure.
     */
    public static <T> T require(
            String reference,
            Function<String, T> slotLookup) {
        Result<T> resolved = resolve(reference, slotLookup);
        if (!resolved.accepted()) {
            throw new Rejected(resolved.diagnostic());
        }
        return resolved.material();
    }

    /**
     * 中文：两个 Loader 冻结阶段共享的可恢复材质拒绝信号。
     * English: Recoverable material-rejection signal shared by both Loader freeze phases.
     */
    public static final class Rejected extends RuntimeException {
        private Rejected(String diagnostic) {
            super(Objects.requireNonNull(diagnostic, "diagnostic"));
        }
    }

    public enum Failure {
        NONE,
        UNKNOWN_TEXTURE_REFERENCE,
        UNRESOLVED_TEXTURE_SLOT
    }

    public record Result<T>(
            String reference,
            String slot,
            T material,
            Failure failure) {
        public Result {
            Objects.requireNonNull(failure, "failure");
            if (failure == Failure.NONE) {
                Objects.requireNonNull(slot, "slot");
                Objects.requireNonNull(material, "material");
            } else if (slot != null || material != null) {
                throw new IllegalArgumentException("failed material resolution cannot carry a value");
            }
        }

        private static <T> Result<T> success(
                String reference,
                String slot,
                T material) {
            return new Result<>(reference, slot, material, Failure.NONE);
        }

        private static <T> Result<T> failure(
                String reference,
                Failure failure) {
            return new Result<>(reference, null, null, failure);
        }

        public boolean accepted() {
            return failure == Failure.NONE;
        }

        /** 中文：返回与现有 Loader 诊断完全一致的失败码。 / English: Returns the exact failure diagnostic used by both Loader adapters. */
        public String diagnostic() {
            if (accepted()) {
                throw new IllegalStateException("accepted material resolution has no diagnostic");
            }
            return failure.name() + ":" + reference;
        }
    }
}
