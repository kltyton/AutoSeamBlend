package com.kltyton.autoseamblend.compat.fusion.runtime;

import com.google.gson.JsonObject;
import com.kltyton.autoseamblend.authoring.format.fusion.FusionNativeDocument;
import com.supermartijn642.fusion.api.texture.DefaultTextureTypes;
import com.supermartijn642.fusion.api.texture.RawTextureInstance;
import com.supermartijn642.fusion.api.texture.types.base.BaseTextureData;
import com.supermartijn642.fusion.api.texture.types.connecting.ConnectingTextureData;
import com.supermartijn642.fusion.api.texture.types.connecting.predicates.ConnectionPredicate;
import java.util.Objects;
import java.util.Optional;

/** 中文：由同一 generation 解析的 Fusion 公共 API 纹理输入。 / English: Fusion texture input resolved by one generation. */
public final class FusionNativeTextureData {
    private final FusionNativeRoute route;
    private final boolean compatibility;
    private final Object nativeData;

    private FusionNativeTextureData(
            FusionNativeRoute route,
            boolean compatibility,
            Object nativeData) {
        this.route = Objects.requireNonNull(route, "route");
        this.compatibility = compatibility;
        this.nativeData = Objects.requireNonNull(nativeData, "nativeData");
    }

    public static FusionNativeTextureData parse(
            FusionNativeDocument document,
            FusionNativeRoute route) {
        return parse(document, route, Optional.empty());
    }

    /**
     * 中文：Managed/config 执行视图可传入同 generation 冻结规则编译出的 Fusion 原生谓词。
     * English: Managed/config execution views may pass a Fusion-native predicate compiled from
     * the same frozen generation.
     */
    public static FusionNativeTextureData parseWithExecutionPredicate(
            FusionNativeDocument document,
            FusionNativeRoute route,
            ConnectionPredicate executionPredicate) {
        return parse(
                document,
                route,
                Optional.of(Objects.requireNonNull(executionPredicate, "executionPredicate")));
    }

    private static FusionNativeTextureData parse(
            FusionNativeDocument document,
            FusionNativeRoute route,
            Optional<ConnectionPredicate> executionPredicate) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(route, "route");
        JsonObject nativeJson = document.nativeExecutionJson();
        boolean compatibility = document.compatibility().orElse(false);
        if (route.layout() != null) {
            ConnectingTextureData parsed = DefaultTextureTypes.CONNECTING.deserialize(nativeJson);
            return new FusionNativeTextureData(
                    route,
                    compatibility,
                    rebuildConnecting(
                            parsed,
                            route,
                            executionPredicate.orElseGet(parsed::getConnectionPredicate)));
        }
        BaseTextureData parsed = DefaultTextureTypes.BASE.deserialize(nativeJson);
        return new FusionNativeTextureData(route, compatibility, parsed);
    }

    public FusionNativeRoute route() {
        return route;
    }

    public boolean compatibility() {
        return compatibility;
    }

    JsonObject serializeNative() {
        if (nativeData instanceof ConnectingTextureData connecting) {
            return DefaultTextureTypes.CONNECTING.serialize(connecting);
        }
        return DefaultTextureTypes.BASE.serialize((BaseTextureData) nativeData);
    }

    void createTexture(
            FusionDelegatingTextureType.ForwardTarget output,
            com.supermartijn642.fusion.api.texture.custom.TextureCreationContext context)
            throws com.supermartijn642.fusion.api.util.UserErrorException {
        if (nativeData instanceof ConnectingTextureData connecting) {
            output.create(DefaultTextureTypes.CONNECTING, connecting, route, context);
        } else {
            output.create(DefaultTextureTypes.BASE, (BaseTextureData) nativeData, route, context);
        }
    }

    private static ConnectingTextureData rebuildConnecting(
            ConnectingTextureData source,
            FusionNativeRoute route,
            ConnectionPredicate predicate) {
        ConnectingTextureData.Builder builder = ConnectingTextureData.builder()
                .layout(route.layout())
                .connectionPredicate(predicate)
                .perTileAnimation(source.perTileAnimation())
                .renderType(source.getRenderType())
                .emissive(source.isEmissive())
                .tinting(source.getTinting());
        RawTextureInstance<?, ?> subTexture = source.subTexture();
        if (subTexture != null) {
            builder.subTexture(subTexture);
        }
        return builder.build();
    }
}
