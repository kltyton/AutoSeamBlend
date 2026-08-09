package com.kltyton.autoseamblend.fabric.runtime.texture.atlas;

import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSource;
import net.fabricmc.fabric.api.client.rendering.v1.AtlasSourceTypeRegistry;

/**
 * 中文：把 common 生成精灵源 codec 注册进 Fabric 的精灵源注册表；实际来源条目由
 * assets/minecraft/atlases/blocks.json 在资源包中声明。
 *
 * English: Registers the common generated-sprite source codec with Fabric's
 * sprite-source registry; the actual source entry is declared by
 * assets/minecraft/atlases/blocks.json in the resource pack.
 */
public final class FabricSpriteSourceRegistration {
    private static boolean registered;

    private FabricSpriteSourceRegistration() {}

    public static synchronized void register() {
        if (registered) {
            return;
        }
        AtlasSourceTypeRegistry.register(
                GeneratedSpriteSource.TYPE_ID,
                GeneratedSpriteSource.TYPE);
        registered = true;
    }
}
