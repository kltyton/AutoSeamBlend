package com.kltyton.autoseamblend.texture.generation.fusion;

import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import com.kltyton.autoseamblend.texture.generation.GeneratedOverlayProfileIdentity;
import java.util.Objects;

import net.minecraft.resources.Identifier;

/**
 * 中文：统一 Fusion 生成精灵的项目自有身份、去重键和遮罩路径；不持有 Fusion 类型。
 * <p>
 * English: Common project-owned identity, deduplication keys, and mask paths for generated Fusion
 * sprites without retaining Fusion types.
 */
public final class FusionGeneratedTextureIdentity {
    private FusionGeneratedTextureIdentity() {
    }

    /**
     * 中文：生成稳定的 Atlas 精灵 ID；源精灵命名空间和路径保持原样编码在路径中。
     * <p>
     * English: Builds the stable Atlas sprite ID while preserving the source namespace and path
     * in the generated path.
     */
    public static Identifier generatedId(
            Identifier source,
            ConnectionMethod method,
            OverlayCutoutProfile overlayProfile,
            int slot) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(method, "method");
        return Identifier.fromNamespaceAndPath(
                Constants.MOD_ID,
                "generated/fusion/"
                        + method.serializedName()
                        + '/'
                        + source.getNamespace()
                        + '/'
                        + source.getPath()
                        + GeneratedOverlayProfileIdentity.pathSuffix(method, overlayProfile)
                        + '/'
                        + slot);
    }

    /**
     * 中文：同一源精灵、方法和遮罩拓扑共享一组物理生成槽。
     * <p>
     * English: Keys one physical generated-slot set by source sprite, method, and mask topology.
     */
    public static String physicalKey(
            Identifier source,
            ConnectionMethod method,
            OverlayCutoutProfile overlayProfile) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(method, "method");
        return method.serializedName()
                + '|'
                + source
                + GeneratedOverlayProfileIdentity.keySuffix(method, overlayProfile);
    }
}
