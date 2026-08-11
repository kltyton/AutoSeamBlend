package com.kltyton.autoseamblend.compat.continuity.document;

import com.kltyton.autoseamblend.authoring.format.mcpatcher.MCPatcherExecutionDocument;
import com.kltyton.autoseamblend.authoring.format.mcpatcher.MCPatcherExtensionContext;
import com.kltyton.autoseamblend.authoring.format.mcpatcher.MCPatcherMethodCodec;
import com.kltyton.autoseamblend.authoring.storage.ManagedPackIdentity;
import com.kltyton.autoseamblend.foundation.Constants;
import java.util.Objects;
import java.util.Properties;
import me.pepperbell.continuity.api.client.CtmLoader;
import me.pepperbell.continuity.api.client.CtmLoaderRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;

/**
 * 中文：集中执行 MCPatcher properties 的内存执行视图；Mixin target 和 Invoker 仍留在 Loader。
 * English: Centralizes the MCPatcher properties execution view while keeping Mixin targets and
 * invokers in each loader.
 */
public final class ContinuityPropertiesLoaderBridge {
    private ContinuityPropertiesLoaderBridge() {}

    /**
     * 中文：运行一次执行视图拦截；返回 true 表示原生 load 已被取消且应由调用方 cancel。
     * English: Runs one execution-view interception; true means native load was handled and the
     * caller should cancel its callback.
     */
    public static boolean apply(
            Properties properties,
            ResourceLocation resourceId,
            PackResources pack,
            int packPriority,
            NativeLoadInvoker invoker) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(pack, "pack");
        Objects.requireNonNull(invoker, "invoker");
        String method = properties.getProperty("method", "ctm").trim();
        if (!requiresExecutionView(properties, pack, method)) {
            return false;
        }

        MCPatcherExecutionDocument.Result prepared =
                MCPatcherExecutionDocument.prepare(
                        properties,
                        resourceId,
                        pack,
                        packPriority);
        if (prepared.rejection().isPresent()) {
            Constants.LOG.error(
                    "Rejected MCPatcher authoring document {} from {}: {}",
                    resourceId,
                    pack.packId(),
                    prepared.rejection().orElseThrow());
            return true;
        }
        Properties runtime = prepared.runtimeProperties().orElseThrow();
        String resolvedMethod = runtime.getProperty("method").trim();
        CtmLoader<?> loader = CtmLoaderRegistry.get().getLoader(resolvedMethod);
        if (loader == null) {
            Constants.LOG.error(
                    "Rejected MCPatcher execution view {}: native method {} is unavailable",
                    resourceId,
                    resolvedMethod);
            return true;
        }
        MCPatcherExtensionContext.call(
                prepared.extension().orElseThrow(),
                () -> {
                    invoker.invoke(
                            loader,
                            runtime,
                            resourceId,
                            pack,
                            packPriority,
                            resolvedMethod);
                    return null;
                });
        return true;
    }

    private static boolean requiresExecutionView(
            Properties properties,
            PackResources pack,
            String method) {
        return ManagedPackIdentity.matches(pack)
                || properties.containsKey("compatibility")
                || MCPatcherMethodCodec.requiresExecutionView(method);
    }

    /**
     * 中文：由 Loader Mixin 的精确 Invoker 提供原生 load 调用。
     * English: Supplies the native load call through the loader mixin's exact invoker.
     */
    @FunctionalInterface
    public interface NativeLoadInvoker {
        void invoke(
                CtmLoader<?> loader,
                Properties properties,
                ResourceLocation resourceId,
                PackResources pack,
                int packPriority,
                String method);
    }
}
