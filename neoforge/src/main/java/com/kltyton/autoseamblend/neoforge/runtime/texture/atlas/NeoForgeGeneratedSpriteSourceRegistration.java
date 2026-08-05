package com.kltyton.autoseamblend.neoforge.runtime.texture.atlas;

import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSource;
import net.neoforged.neoforge.client.event.RegisterSpriteSourcesEvent;

/** 中文：仅负责把 common 生成精灵源接入 NeoForge 注册事件。 / English: Only connects the common generated-sprite source to the NeoForge registration event. */
public final class NeoForgeGeneratedSpriteSourceRegistration {
    private NeoForgeGeneratedSpriteSourceRegistration() {}

    public static void register(RegisterSpriteSourcesEvent event) {
        event.register(
                GeneratedSpriteSource.TYPE_ID,
                GeneratedSpriteSource.MAP_CODEC);
    }
}
