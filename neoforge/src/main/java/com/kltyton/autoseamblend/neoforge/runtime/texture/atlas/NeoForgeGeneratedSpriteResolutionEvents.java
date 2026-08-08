package com.kltyton.autoseamblend.neoforge.runtime.texture.atlas;

import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteAtlasResolution;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSetCatalog;
import com.kltyton.autoseamblend.texture.atlas.ResolvedSpriteCatalog;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.neoforged.neoforge.client.event.TextureAtlasStitchedEvent;

/** 中文：仅把 NeoForge Atlas 缝合事件接到 common 生成精灵解析器。 / English: Only connects the NeoForge atlas-stitch event to the common generated-sprite resolver. */
public final class NeoForgeGeneratedSpriteResolutionEvents {
    private NeoForgeGeneratedSpriteResolutionEvents() {}

    public static void onTextureAtlasStitched(
            TextureAtlasStitchedEvent event) {
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
