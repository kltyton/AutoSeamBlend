package com.kltyton.autoseamblend.authoring.format.mcpatcher;

import com.kltyton.autoseamblend.engine.ownership.AutoBlendPolicy;
import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：MCPatcher 作者文档进入任一原生引擎执行副本时保留的 Loader 中立来源。
 * English: Loader-neutral provenance retained while an MCPatcher author document enters any
 * native-engine execution copy.
 *
 * @param packId 中文：来源资源包 ID。 / English: Source pack id.
 * @param resourceId 中文：来源资源位置。 / English: Source resource location.
 * @param packPriority 中文：资源包优先级。 / English: Pack priority.
 * @param managed 中文：是否属于 Managed 内容。 / English: Whether the content is Managed.
 * @param requestedMethod 中文：作者请求的连接方法。 / English: Author-requested connection method.
 * @param resolvedMethod 中文：已解析的具体方法。 / English: Resolved concrete method.
 * @param exactSurfaceResolutionRequired 中文：是否要求精确表面解析。 / English: Whether exact surface resolution is required.
 * @param compatibility 中文：Managed 兼容策略布尔值（可选）。 / English: Optional Managed compatibility policy boolean.
 */
public record MCPatcherAuthorExtension(
        String packId,
        ResourceLocation resourceId,
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
