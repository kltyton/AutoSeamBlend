package com.kltyton.autoseamblend.texture.atlas;

import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSet.SourceMetadata;
import com.mojang.serialization.MapCodec;
import java.util.Objects;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/** 中文：不经 PNG 编码，直接向方块 Atlas 提供生成的原生状态精灵。 / English: Supplies generated native state sprites directly to the block atlas without PNG encoding. */
public final class GeneratedSpriteSource implements SpriteSource {
    public static final Identifier TYPE_ID =
            Identifier.fromNamespaceAndPath(
                    Constants.MOD_ID,
                    "generated_states");
    public static final MapCodec<GeneratedSpriteSource> MAP_CODEC =
            MapCodec.unit(GeneratedSpriteSource::new);

    @Override
    public void run(
            ResourceManager resources,
            Output output) {
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(output, "output");
        GeneratedSpriteSetCatalog.Snapshot snapshot =
                ReloadPublication.atlasCatalog();
        snapshot.definitions()
                .values()
                .forEach(definition -> {
                    SourceMetadata metadata =
                            definition.loadSourceMetadata(resources);
                    definition.tiles()
                            .forEach(tile -> {
                                output.add(
                                        tile.spriteId(),
                                        loader -> definition.load(
                                                tile,
                                                metadata));
                            });
                });
        GeneratedSpriteSetCatalog.markPrepared(snapshot.generation());
    }

    @Override
    public MapCodec<? extends SpriteSource> codec() {
        return MAP_CODEC;
    }
}
