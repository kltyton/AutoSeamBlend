package com.kltyton.autoseamblend.texture.generation;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import java.util.Objects;

/** 中文：生成纹理身份中 overlay 拓扑的统一编码。 / English: Uniform encoding of overlay topology in generated-texture identities. */
public final class GeneratedOverlayProfileIdentity {
    private GeneratedOverlayProfileIdentity() {
    }

    public static String keySuffix(
            ConnectionMethod method,
            OverlayCutoutProfile profile) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(profile, "profile");
        return method.overlayCapable() ? "|overlay=" + profile.topologyId() : "";
    }

    public static String pathSuffix(
            ConnectionMethod method,
            OverlayCutoutProfile profile) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(profile, "profile");
        return method.overlayCapable() ? "/overlay_" + profile.topologyId() : "";
    }
}
