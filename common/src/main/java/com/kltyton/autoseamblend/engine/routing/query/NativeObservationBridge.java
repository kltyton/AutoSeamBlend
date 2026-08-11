package com.kltyton.autoseamblend.engine.routing.query;

import com.kltyton.autoseamblend.engine.EngineDescriptor;
import com.kltyton.autoseamblend.engine.query.ConnectionQuery;
import com.kltyton.autoseamblend.engine.query.EngineQueryContext;
import com.kltyton.autoseamblend.engine.query.NativeQueryObservation;
import com.kltyton.autoseamblend.engine.query.QueryObservation;
import com.kltyton.autoseamblend.engine.routing.NativeQueryOwnershipRegistry;
import com.kltyton.autoseamblend.engine.routing.NativeQueryOwnershipProvider;
import java.util.Objects;

/**
 * 中文：可选引擎隔离的观察桥；注册、调用与原生文档映射都停留在不含第三方类型的边界内。
 *
 * English:
 * Optional-engine-neutral observation bridge. Registration, invocation, and
 * accepted-document mapping remain behind a boundary containing no third-party
 * engine type.
 */
public final class NativeObservationBridge {
    private static final NativeQueryOwnershipRegistry PROVIDERS =
            new NativeQueryOwnershipRegistry();

    private NativeObservationBridge() {}

    public static void register(
            NativeQueryOwnershipProvider provider) {
        PROVIDERS.register(provider);
    }

    public static boolean registered(String engineId) {
        return PROVIDERS.registered(
                Objects.requireNonNull(engineId, "engineId"));
    }

    public static QueryObservation observe(
            EngineDescriptor descriptor,
            ConnectionQuery query,
            EngineQueryContext nativeContext) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(query, "query");
        if (!(Objects.requireNonNull(
                        nativeContext,
                        "nativeContext")
                instanceof MinecraftEngineQueryContext context)) {
            throw new IllegalArgumentException(
                    "native adapters require MinecraftEngineQueryContext");
        }
        context.requireMatches(query);
        NativeQueryOwnershipProvider provider = PROVIDERS.require(descriptor);
        NativeQueryObservation observed =
                context.reloadGeneration()
                        .captureHealth()
                        .unknownDiagnostic(
                                provider.engineId(),
                                context.state())
                        .map(NativeQueryObservation::unknown)
                        .orElseGet(() -> Objects.requireNonNull(
                                provider.observe(
                                        context.reloadGeneration()
                                                .generation(),
                                        context.level(),
                                        context.pos(),
                                        context.state(),
                                        context.quad(),
                                        context.sprite()),
                                "native query observation"));
        return map(
                descriptor,
                context,
                observed);
    }

    private static QueryObservation map(
            EngineDescriptor descriptor,
            MinecraftEngineQueryContext context,
            NativeQueryObservation observed) {
        return AcceptedDocumentOwnershipMapper.map(
                new AcceptedDocumentOwnershipMapper.Input(
                        descriptor,
                        context.blockId(),
                        context.surface().inferredMethod(),
                        observed,
                        context.reloadGeneration()
                                .nativeRules()
                                .rules(descriptor.family(), context.blockId()),
                        context.reloadGeneration()
                                .managedRules()
                                .rules(descriptor.family(), context.blockId()),
                        context.reloadGeneration()
                                .managedRules()
                                .packPriority()));
    }
}
