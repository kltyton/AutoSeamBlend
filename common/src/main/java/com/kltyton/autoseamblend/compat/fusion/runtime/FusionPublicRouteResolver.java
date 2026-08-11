package com.kltyton.autoseamblend.compat.fusion.runtime;

import com.google.gson.JsonParseException;
import com.kltyton.autoseamblend.authoring.format.fusion.FusionNativeDocument;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;

/** 中文：解析 Fusion 手动 replacement 路由。 / English: Resolves manual Fusion replacement routes. */
public final class FusionPublicRouteResolver {
    private FusionPublicRouteResolver() {}

    public static FusionNativeRoute resolveReplacement(FusionNativeDocument document) {
        ConnectionMethod method = Objects.requireNonNull(document, "document")
                .requestedMethod()
                .orElseThrow(() -> new JsonParseException(
                        "FUSION_METHOD_REQUIRED: replacement execution requires a method"));
        if (method == ConnectionMethod.AUTO) {
            throw new JsonParseException(
                    "FUSION_AUTO_REQUIRES_FROZEN_SURFACE: resolve auto before native parsing");
        }
        FusionNativeRoute route = FusionNativeRoute.resolve(method, method);
        if (route.kind() != FusionNativeRoute.Kind.REPLACEMENT) {
            throw new JsonParseException(
                    "FUSION_PUBLIC_REPLACEMENT_ONLY: overlay, top, fixed, and none are not "
                            + "replacement execution routes");
        }
        return route;
    }
}
