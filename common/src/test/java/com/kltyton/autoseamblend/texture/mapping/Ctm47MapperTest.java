package com.kltyton.autoseamblend.texture.mapping;

import com.kltyton.autoseamblend.texture.geometry.TextureCorner;
import com.kltyton.autoseamblend.texture.geometry.TextureEdge;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class Ctm47MapperTest {
    @Test
    void mapsEveryValidNeighborhoodBijectivelyToAllFortySevenTiles() {
        Set<Integer> validMasks = new HashSet<>();
        Set<Integer> tileIndexes = new HashSet<>();
        for (int bits = 0; bits <= 0xFF; bits++) {
            NeighborConnections connections = NeighborConnections.fromBits(bits);
            if (connections.normalizedCtmBits() == bits) {
                validMasks.add(bits);
                tileIndexes.add(Ctm47Mapper.tileIndex(connections));
            }
        }

        assertEquals(47, validMasks.size());
        assertEquals(
                IntStream.range(0, Ctm47Mapper.TILE_COUNT).boxed().collect(java.util.stream.Collectors.toSet()),
                tileIndexes);
        for (int tile = 0; tile < Ctm47Mapper.TILE_COUNT; tile++) {
            assertEquals(tile, Ctm47Mapper.tileIndex(Ctm47Mapper.connectionsForTile(tile)));
        }
    }

    @Test
    void followsKnownOptifineContinuityCases() {
        assertEquals(0, tile(0));
        assertEquals(3, tile(1));
        assertEquals(12, tile(1 << TextureEdge.DOWN.connectionBit()));
        assertEquals(1, tile(1 << TextureEdge.RIGHT.connectionBit()));
        assertEquals(36, tile(1 << TextureEdge.UP.connectionBit()));
        assertEquals(5, tile(0x05));
        assertEquals(15, tile(0x07));
        assertEquals(46, tile(0x55));
        assertEquals(26, tile(0xFF));
    }

    @Test
    void ignoresOrphanedDiagonalConnections() {
        assertEquals(tile(0), tile(1 << TextureCorner.BOTTOM_LEFT.connectionBit()));
        assertEquals(tile(1), tile(1 | 1 << TextureCorner.BOTTOM_LEFT.connectionBit()));
    }

    private static int tile(int bits) {
        return Ctm47Mapper.tileIndex(NeighborConnections.fromBits(bits));
    }
}
