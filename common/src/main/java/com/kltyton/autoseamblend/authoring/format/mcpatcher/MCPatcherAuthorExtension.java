package com.kltyton.autoseamblend.authoring.format.mcpatcher;

import com.kltyton.autoseamblend.engine.ownership.AutoBlendPolicy;
import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * 中文：MCPatcher 作者文档进入任一原生引擎执行副本时保留的 Loader 中立来源。
 * English: Loader-neutral provenance retained while an MCPatcher author document enters any
 * native-engine execution copy.
 */
public record MCPatcherAuthorExtension(
        String packId,
        Identifier resourceId,
        int packPriority,
        boolean managed,
        ConnectionMethod requestedMethod,
        ConnectionMethod resolvedMethod,
        boolean exactSurfaceResolutionRequired,
        Optional<Boolean> compatibility) {
    public MCPatcherAuthorExtension {
        if (packId == null || packId.isBlank()) {
            throw new IllegalArgumentException("packId must not be blank");
        }
        Objects.requireNonNull(resourceId, "resourceId");
        if (packPriority < 0) {
            throw new IllegalArgumentException("packPriority must be non-negative");
        }
        Objects.requireNonNull(requestedMethod, "requestedMethod");
        Objects.requireNonNull(resolvedMethod, "resolvedMethod");
        compatibility = Objects.requireNonNull(compatibility, "compatibility");
        if (resolvedMethod == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException("execution method must be concrete");
        }
        if (exactSurfaceResolutionRequired
                && (requestedMethod != ConnectionMethod.AUTO
                        || resolvedMethod != ConnectionMethod.NONE)) {
            throw new IllegalArgumentException(
                    "only an unconstrained auto execution carrier requires exact-surface resolution");
        }
        if (managed && compatibility.isEmpty()) {
            throw new IllegalArgumentException(
                    "Managed MCPatcher documents require compatibility=true|false");
        }
    }

    public SourceTier sourceTier() {
        if (!managed) {
            return SourceTier.NATIVE_AUTHOR;
        }
        return compatibility.orElseThrow()
                ? SourceTier.MANAGED_COMPATIBILITY
                : SourceTier.MANAGED_NON_COMPATIBILITY;
    }

    public Optional<AutoBlendPolicy> strategyPolicy() {
        return compatibility.map(AutoBlendPolicy::fromCompatibility);
    }
}
