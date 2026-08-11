package com.kltyton.autoseamblend.compat.fusion.runtime;

import com.supermartijn642.fusion.api.texture.DefaultTextureTypes;
import com.supermartijn642.fusion.api.texture.TextureType;
import com.supermartijn642.fusion.api.texture.types.base.BaseTextureData;
import com.supermartijn642.fusion.api.texture.types.connecting.ConnectingTextureData;
import com.supermartijn642.fusion.texture.types.connecting.StitchedConnectingTextureData;

/**
 * 中文：集中锁定 Fusion 原生纹理类型的公共 ABI 泛型视图。
 * English: Centralizes the common ABI generic views of Fusion's native texture types.
 */
public final class FusionNativeTextureTypes {
    private FusionNativeTextureTypes() {}

    /**
     * 中文：读取连接纹理类型并保留已验证的拼接数据泛型。
     * English: Reads the connecting texture type with the verified stitched-data generic.
     */
    @SuppressWarnings("unchecked")
    public static TextureType<ConnectingTextureData, StitchedConnectingTextureData>
            connectingType() {
        return (TextureType<ConnectingTextureData, StitchedConnectingTextureData>)
                (TextureType<?, ?>) DefaultTextureTypes.CONNECTING;
    }

    /**
     * 中文：读取基础纹理类型并保留已验证的基础数据泛型。
     * English: Reads the base texture type with the verified base-data generic.
     */
    @SuppressWarnings("unchecked")
    public static TextureType<BaseTextureData, BaseTextureData> baseType() {
        return (TextureType<BaseTextureData, BaseTextureData>)
                (TextureType<?, ?>) DefaultTextureTypes.BASE;
    }
}
