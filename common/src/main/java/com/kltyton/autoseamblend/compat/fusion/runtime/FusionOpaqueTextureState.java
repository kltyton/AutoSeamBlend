package com.kltyton.autoseamblend.compat.fusion.runtime;

import com.supermartijn642.fusion.api.model.custom.quad.MutableQuad;
import com.supermartijn642.fusion.api.texture.TextureType;
import com.supermartijn642.fusion.api.texture.custom.QuadProcessor;
import com.supermartijn642.fusion.api.texture.custom.SpriteInstance;
import com.supermartijn642.fusion.api.util.PropertyStore;
import java.util.Objects;

/** 中文：Fusion custom data 的精确版本句柄。 / English: Exact-version Fusion custom-data handle. */
final class FusionOpaqueTextureState {
    private final TextureType<Object, Object> nativeType;
    private final Object customData;
    private final FusionNativeRoute route;

    private FusionOpaqueTextureState(
            TextureType<Object, Object> nativeType,
            Object customData,
            FusionNativeRoute route) {
        this.nativeType = Objects.requireNonNull(nativeType, "nativeType");
        this.customData = customData;
        this.route = Objects.requireNonNull(route, "route");
    }

    @SuppressWarnings("unchecked")
    static <T, X> FusionOpaqueTextureState of(
            TextureType<T, X> nativeType,
            X customData,
            FusionNativeRoute route) {
        return new FusionOpaqueTextureState(
                (TextureType<Object, Object>) nativeType,
                customData,
                route);
    }

    QuadProcessor<?> initializeModelQuad(
            MutableQuad quad,
            SpriteInstance sprite,
            PropertyStore properties) {
        if (route.kind() != FusionNativeRoute.Kind.REPLACEMENT
                && route.kind() != FusionNativeRoute.Kind.OVERLAY_DONOR) {
            // 中文：null 是 Fusion 公开 API 对不安装处理器的原生表达。
            // English: null is Fusion's public-API expression for no processor.
            return null;
        }
        return nativeType.initializeModelQuad(
                quad, sprite, customData, properties);
    }
}
