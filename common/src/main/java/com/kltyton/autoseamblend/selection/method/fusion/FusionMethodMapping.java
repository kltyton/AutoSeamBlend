package com.kltyton.autoseamblend.selection.method.fusion;

import com.kltyton.autoseamblend.engine.plan.NativeMethodMapping;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * 中文：Fusion 的 13 个公开方法到项目自有原生路由描述的唯一映射表。
 *
 * English: Single mapping table from Fusion's 13 public methods to project-owned native-route
 * descriptions.
 *
 * <p>中文：这些字符串是项目自有路由标识，不复制 Fusion 内部实现或槽位表。
 * English: These strings are project-owned route identifiers; no Fusion implementation or slot
 * table is copied.
 */
public final class FusionMethodMapping {
    private static final Map<ConnectionMethod, NativeMethodMapping> ROUTES = createRoutes();

    private FusionMethodMapping() {}

    public static NativeMethodMapping nativeMapping(ConnectionMethod method) {
        return ROUTES.get(Objects.requireNonNull(method, "method"));
    }

    public static Map<ConnectionMethod, NativeMethodMapping> routes() {
        return ROUTES;
    }

    private static Map<ConnectionMethod, NativeMethodMapping> createRoutes() {
        EnumMap<ConnectionMethod, NativeMethodMapping> routes =
                new EnumMap<>(ConnectionMethod.class);
        put(routes, ConnectionMethod.AUTO,
                "fusion:resolve-auto-first", 0,
                NativeMethodMapping.Behavior.RESOLVE_AUTO_FIRST);
        put(routes, ConnectionMethod.RUNTIME_BLEND,
                "fusion:connecting/layout=overlay;autoseamblend:logical-slots=17,route=runtime-blend",
                17, NativeMethodMapping.Behavior.NATIVE);
        put(routes, ConnectionMethod.CTM,
                "fusion:connecting/layout=full;autoseamblend:logical-slots=47,route=ctm",
                47, NativeMethodMapping.Behavior.NATIVE);
        put(routes, ConnectionMethod.CTM_COMPACT,
                "fusion:connecting/layout=full;autoseamblend:logical-slots=47,route=ctm-compact",
                47, NativeMethodMapping.Behavior.NATIVE);
        put(routes, ConnectionMethod.HORIZONTAL,
                "fusion:connecting/layout=horizontal;autoseamblend:logical-slots=4,route=horizontal",
                4, NativeMethodMapping.Behavior.NATIVE);
        put(routes, ConnectionMethod.VERTICAL,
                "fusion:connecting/layout=vertical;autoseamblend:logical-slots=4,route=vertical",
                4, NativeMethodMapping.Behavior.NATIVE);
        put(routes, ConnectionMethod.HORIZONTAL_VERTICAL,
                "fusion:connecting/layout=full;autoseamblend:logical-slots=7,route=horizontal-then-vertical",
                7, NativeMethodMapping.Behavior.NATIVE);
        put(routes, ConnectionMethod.VERTICAL_HORIZONTAL,
                "fusion:connecting/layout=full;autoseamblend:logical-slots=7,route=vertical-then-horizontal",
                7, NativeMethodMapping.Behavior.NATIVE);
        put(routes, ConnectionMethod.TOP,
                "fusion:connecting/layout=full;autoseamblend:logical-slots=1,predicate=top,route=top",
                1, NativeMethodMapping.Behavior.NATIVE);
        put(routes, ConnectionMethod.OVERLAY,
                "fusion:connecting/layout=overlay;autoseamblend:logical-slots=17,route=overlay",
                17, NativeMethodMapping.Behavior.NATIVE);
        put(routes, ConnectionMethod.OVERLAY_CTM,
                "fusion:connecting/layout=full;autoseamblend:logical-slots=47,route=overlay-ctm",
                47, NativeMethodMapping.Behavior.NATIVE);
        put(routes, ConnectionMethod.FIXED,
                "fusion:base;autoseamblend:logical-slots=1,route=fixed",
                1, NativeMethodMapping.Behavior.NATIVE);
        put(routes, ConnectionMethod.NONE,
                "fusion:passthrough;autoseamblend:logical-slots=0,route=none",
                0, NativeMethodMapping.Behavior.PASSTHROUGH);
        if (routes.size() != ConnectionMethod.values().length) {
            throw new IllegalStateException("Fusion mapping must cover all public connection methods");
        }
        return Map.copyOf(routes);
    }

    private static void put(
            EnumMap<ConnectionMethod, NativeMethodMapping> routes,
            ConnectionMethod method,
            String nativeMethodId,
            int slotCount,
            NativeMethodMapping.Behavior behavior) {
        if (routes.put(method, new NativeMethodMapping(
                method, nativeMethodId, slotCount, behavior)) != null) {
            throw new IllegalStateException("duplicate Fusion mapping for " + method);
        }
    }
}
