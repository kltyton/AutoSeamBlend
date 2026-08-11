package com.kltyton.autoseamblend.texture.atlas;

import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSet.SourceMetadata;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import java.util.Objects;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** 中文：不经 PNG 编码，直接向方块 Atlas 提供生成的原生状态精灵。 / English: Supplies generated native state sprites directly to the block atlas without PNG encoding. */
public final class GeneratedSpriteSource implements SpriteSource {
    public static final ResourceLocation TYPE_ID =
            new ResourceLocation(
                    Constants.MOD_ID,
                    "generated_states");
    // 1.20.1 SpriteSourceType takes a Codec (MapCodec.codec() does not exist yet).
    public static final Codec<GeneratedSpriteSource> CODEC =
            Codec.unit(GeneratedSpriteSource::new);
    public static final SpriteSourceType TYPE =
            new SpriteSourceType(CODEC);

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
                                        () -> definition.load(
                                                tile,
                                                metadata));
                            });
                });
        GeneratedSpriteSetCatalog.markPrepared(snapshot.generation());
    }

    @Override
    public SpriteSourceType type() {
        return TYPE;
    }
}
