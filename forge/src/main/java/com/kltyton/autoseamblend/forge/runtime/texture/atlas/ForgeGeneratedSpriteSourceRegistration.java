package com.kltyton.autoseamblend.forge.runtime.texture.atlas;

import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSources;

/** 中文：仅负责把 common 生成精灵源接入 1.20.1 SpriteSources 调度表。 / English: Only connects the common generated-sprite source to the 1.20.1 SpriteSources dispatch table. */
public final class ForgeGeneratedSpriteSourceRegistration {
    private ForgeGeneratedSpriteSourceRegistration() {}

    public static void register() {
        SpriteSources.TYPES.put(
                GeneratedSpriteSource.TYPE_ID,
                GeneratedSpriteSource.TYPE);
    }
}
