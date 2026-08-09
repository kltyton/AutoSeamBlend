package com.kltyton.autoseamblend.texture.generation;

import com.kltyton.autoseamblend.texture.image.ArgbImage;
import com.kltyton.autoseamblend.texture.image.PremultipliedArgb;
import com.kltyton.autoseamblend.texture.mapping.CompactCtmMapper;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import com.kltyton.autoseamblend.texture.mapping.Overlay17Layout;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import java.util.Objects;

/** 中文：创作与 baked 导出实体化共享的唯一生产入口。 / English: Single production entry point shared by authoring and baked export materialization. */
public final class GeneratedTileMaterializer {
    private GeneratedTileMaterializer() {}

    public static int[] materializeStraightArgb(
            int width,
            int height,
            int[] straightArgb,
            GeneratedTileRecipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        ArgbImage source = ArgbImage.fromStraightArgb(width, height, straightArgb);
        return materializeStraightArgb(
                source,
                recipe,
                OverlayCutoutProfile.fromArgb(
                        source.width(),
                        source.height(),
                        source.copyPixels()));
    }

    /** 中文：使用重载期已冻结的表面轮廓实体化槽位，保证 Runtime、Preview、PNG 与 baked 选择同一拓扑。 / English: Materializes a slot with the reload-frozen surface profile so Runtime, Preview, PNG, and baked use one topology. */
    public static int[] materializeStraightArgb(
            int width,
            int height,
            int[] straightArgb,
            GeneratedTileRecipe recipe,
            OverlayCutoutProfile overlayProfile) {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(
                overlayProfile,
                "overlayProfile");
        return materializeStraightArgb(
                ArgbImage.fromStraightArgb(
                        width,
                        height,
                        straightArgb),
                recipe,
                overlayProfile);
    }

    private static int[] materializeStraightArgb(
            ArgbImage source,
            GeneratedTileRecipe recipe,
            OverlayCutoutProfile overlayProfile) {
        ArgbImage generated = switch (recipe) {
            case GeneratedTileRecipe.Source ignored -> source;
            case GeneratedTileRecipe.BorderConnections connections -> Ctm47TileGenerator.generate(
                    source,
                    connections.connections(),
                    borderWidth(
                            source.width(),
                            source.height()));
            case GeneratedTileRecipe.CompactConnections connections ->
                    compact(
                            source,
                            connections.connections());
            case GeneratedTileRecipe.BlendConnections connections -> overlayConnections(
                    source,
                    connections.connections(),
                    overlayProfile);
            case GeneratedTileRecipe.OverlayMask17 mask -> overlay(
                    source,
                    mask.slot(),
                    overlayProfile);
        };
        return straightPixels(generated);
    }

    private static NeighborConnections compactConnections(int slot) {
        return switch (slot) {
            case 0 -> NeighborConnections.none();
            case 1 -> NeighborConnections.fromBits(0xFF);
            case 2 -> NeighborConnections.fromBits(0x44);
            case 3 -> NeighborConnections.fromBits(0x11);
            case 4 -> NeighborConnections.fromBits(0x55);
            default -> throw new IllegalArgumentException("compact CTM slot must be in [0, 4]");
        };
    }

    private static int[] straightPixels(ArgbImage generated) {
        int[] premultiplied = generated.copyPixels();
        int[] straight = new int[premultiplied.length];
        for (int index = 0; index < premultiplied.length; index++) {
            straight[index] = PremultipliedArgb.toStraight(premultiplied[index]);
        }
        return straight;
    }

    private static ArgbImage overlay(
            ArgbImage source,
            int slot,
            OverlayCutoutProfile profile) {
        if (slot < 0 || slot >= 17) {
            throw new IllegalArgumentException("overlay slot must be in [0, 16]");
        }
        int[] sourcePixels = source.copyPixels();
        int[] pixels = sourcePixels.clone();
        for (int y = 0; y < source.height(); y++) {
            int maskY = y * 16 / source.height();
            int row = profile.rowBits(
                    1 << slot,
                    maskY);
            for (int x = 0; x < source.width(); x++) {
                int maskX = x * 16 / source.width();
                if ((row & 1 << maskX) == 0) {
                    pixels[y * source.width() + x] = 0;
                } else {
                    pixels[y * source.width() + x] =
                            profile.sampleArgb(
                                    source.width(),
                                    source.height(),
                                    sourcePixels,
                                    x,
                                    y);
                }
            }
        }
        return ArgbImage.wrapGenerated(source.width(), source.height(), pixels);
    }

    private static ArgbImage overlayConnections(
            ArgbImage source,
            NeighborConnections connections,
            OverlayCutoutProfile profile) {
        int tileMask = 0;
        for (int slot
                : Overlay17Layout.selectedSlots(
                        connections)) {
            tileMask |= 1 << slot;
        }
        if (tileMask == 0) {
            return ArgbImage.wrapGenerated(
                    source.width(),
                    source.height(),
                    new int[Math.multiplyExact(
                            source.width(),
                            source.height())]);
        }
        int[] sourcePixels = source.copyPixels();
        int[] pixels = sourcePixels.clone();
        for (int y = 0; y < source.height(); y++) {
            int maskY = y * 16 / source.height();
            int row = profile.rowBits(
                    tileMask,
                    maskY);
            for (int x = 0; x < source.width(); x++) {
                int maskX = x * 16 / source.width();
                if ((row & 1 << maskX) == 0) {
                    pixels[y * source.width() + x] = 0;
                } else {
                    pixels[y * source.width() + x] =
                            profile.sampleArgb(
                                    source.width(),
                                    source.height(),
                                    sourcePixels,
                                    x,
                                    y);
                }
            }
        }
        return ArgbImage.wrapGenerated(source.width(), source.height(), pixels);
    }

    private static ArgbImage compact(
            ArgbImage source,
            NeighborConnections connections) {
        ArgbImage[] tiles =
                new ArgbImage[CompactCtmMapper.TILE_COUNT];
        for (int slot = 0;
                slot < tiles.length;
                slot++) {
            tiles[slot] = Ctm47TileGenerator.generate(
                    source,
                    compactConnections(slot),
                    borderWidth(
                            source.width(),
                            source.height()));
        }
        CompactCtmMapper.CompactTiles selected =
                CompactCtmMapper.tiles(connections);
        int splitX = (source.width() + 1) / 2;
        int splitY = (source.height() + 1) / 2;
        int[] pixels = new int[Math.multiplyExact(
                source.width(),
                source.height())];
        for (int y = 0;
                y < source.height();
                y++) {
            for (int x = 0;
                    x < source.width();
                    x++) {
                int slot = y < splitY
                        ? x < splitX
                                ? selected.topLeft()
                                : selected.topRight()
                        : x < splitX
                                ? selected.bottomLeft()
                                : selected.bottomRight();
                pixels[y * source.width() + x] =
                        tiles[slot].pixelAt(x, y);
            }
        }
        return ArgbImage.wrapGenerated(
                source.width(),
                source.height(),
                pixels);
    }

    private static int borderWidth(int width, int height) {
        int minimum = Math.min(width, height);
        return Math.max(1, Math.min(3, minimum / 4));
    }
}
