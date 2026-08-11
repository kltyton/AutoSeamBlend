package com.kltyton.autoseamblend.forge.runtime.texture.atlas;

import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteAtlasResolution;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSetCatalog;
import com.kltyton.autoseamblend.texture.atlas.ResolvedSpriteCatalog;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraftforge.client.event.TextureStitchEvent;

/** 中文：仅把 Forge Atlas 缝合事件接到 common 生成精灵解析器。 / English: Only connects the Forge atlas-stitch event to the common generated-sprite resolver. */
public final class ForgeGeneratedSpriteResolutionEvents {
    private ForgeGeneratedSpriteResolutionEvents() {}

    public static void onTextureAtlasStitched(
            TextureStitchEvent.Post event) {
        TextureAtlas atlas = event.getAtlas();
        if (!TextureAtlas.LOCATION_BLOCKS.equals(atlas.location())) {
            return;
        }
        GeneratedSpriteSetCatalog.Snapshot catalog =
                ReloadPublication.atlasCatalog();
        ResolvedSpriteCatalog resolved =
                GeneratedSpriteAtlasResolution.resolve(
                        atlas,
                        catalog);
        ReloadPublication.stageResolvedSprites(resolved);
    }
}
