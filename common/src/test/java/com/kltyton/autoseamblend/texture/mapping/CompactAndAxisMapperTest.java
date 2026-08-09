package com.kltyton.autoseamblend.texture.mapping;

import com.kltyton.autoseamblend.texture.geometry.TextureCorner;
import com.kltyton.autoseamblend.texture.geometry.TextureEdge;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class CompactAndAxisMapperTest {
    @Test
    void compactTilesRepresentDisconnectedEdgesAndCorners() {
        assertEquals(new CompactCtmMapper.CompactTiles(0, 0, 0, 0), CompactCtmMapper.tiles(none()));
        assertEquals(
                new CompactCtmMapper.CompactTiles(4, 4, 4, 4),
                CompactCtmMapper.tiles(edges(TextureEdge.values())));
        assertEquals(
                new CompactCtmMapper.CompactTiles(1, 1, 1, 1),
                CompactCtmMapper.tiles(NeighborConnections.fromBits(0xFF)));
        assertEquals(
                new CompactCtmMapper.CompactTiles(3, 0, 0, 3),
                CompactCtmMapper.tiles(edges(TextureEdge.LEFT)));
        assertEquals(
                new CompactCtmMapper.CompactTiles(2, 2, 0, 0),
                CompactCtmMapper.tiles(edges(TextureEdge.UP)));
    }

    @Test
    void singleAxisMethodsUseTheDocumentedFourTileOrdering() {
        assertEquals(3, AxisTileMapper.horizontal(none()));
        assertEquals(2, AxisTileMapper.horizontal(edges(TextureEdge.LEFT)));
        assertEquals(0, AxisTileMapper.horizontal(edges(TextureEdge.RIGHT)));
        assertEquals(1, AxisTileMapper.horizontal(edges(TextureEdge.LEFT, TextureEdge.RIGHT)));

        assertEquals(3, AxisTileMapper.vertical(none()));
        assertEquals(2, AxisTileMapper.vertical(edges(TextureEdge.DOWN)));
        assertEquals(0, AxisTileMapper.vertical(edges(TextureEdge.UP)));
        assertEquals(1, AxisTileMapper.vertical(edges(TextureEdge.DOWN, TextureEdge.UP)));
    }

    @Test
    void horizontalVerticalPrioritizesHorizontalThenUsesSevenTileSecondaryCases() {
        assertEquals(3, AxisTileMapper.horizontalVertical(none()));
        assertEquals(6, AxisTileMapper.horizontalVertical(edges(TextureEdge.DOWN)));
        assertEquals(4, AxisTileMapper.horizontalVertical(edges(TextureEdge.UP)));
        assertEquals(5, AxisTileMapper.horizontalVertical(edges(TextureEdge.DOWN, TextureEdge.UP)));
        assertEquals(3, AxisTileMapper.horizontalVertical(NeighborConnections.fromBits(0x06)));
        assertEquals(2, AxisTileMapper.horizontalVertical(edges(TextureEdge.LEFT, TextureEdge.UP)));
    }

    @Test
    void verticalHorizontalPrioritizesVerticalThenUsesSevenTileSecondaryCases() {
        assertEquals(3, AxisTileMapper.verticalHorizontal(none()));
        assertEquals(6, AxisTileMapper.verticalHorizontal(edges(TextureEdge.LEFT)));
        assertEquals(4, AxisTileMapper.verticalHorizontal(edges(TextureEdge.RIGHT)));
        assertEquals(5, AxisTileMapper.verticalHorizontal(edges(TextureEdge.LEFT, TextureEdge.RIGHT)));
        int leftAndTopLeft = 1 << TextureEdge.LEFT.connectionBit()
                | 1 << TextureCorner.TOP_LEFT.connectionBit();
        assertEquals(3, AxisTileMapper.verticalHorizontal(NeighborConnections.fromBits(leftAndTopLeft)));
        assertEquals(2, AxisTileMapper.verticalHorizontal(edges(TextureEdge.DOWN, TextureEdge.LEFT)));
    }

    private static NeighborConnections none() {
        return NeighborConnections.none();
    }

    private static NeighborConnections edges(TextureEdge... edges) {
        EnumSet<TextureEdge> set = EnumSet.noneOf(TextureEdge.class);
        for (TextureEdge edge : edges) {
            set.add(edge);
        }
        return NeighborConnections.of(set, EnumSet.noneOf(TextureCorner.class));
    }
}
