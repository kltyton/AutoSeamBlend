package com.kltyton.autoseamblend.texture.generation;

import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import java.util.Objects;

/** 中文：一个生成纹理块所需像素的不可变、引擎无关描述。 / English: Immutable, engine-neutral description of pixels required for one generated tile. */
public sealed interface GeneratedTileRecipe
        permits GeneratedTileRecipe.Source,
                GeneratedTileRecipe.BorderConnections,
                GeneratedTileRecipe.CompactConnections,
                GeneratedTileRecipe.BlendConnections,
                GeneratedTileRecipe.OverlayMask17 {

    enum Source implements GeneratedTileRecipe {
        INSTANCE
    }

    record BorderConnections(NeighborConnections connections) implements GeneratedTileRecipe {
        public BorderConnections {
            Objects.requireNonNull(connections, "connections");
        }
    }

    record CompactConnections(NeighborConnections connections)
            implements GeneratedTileRecipe {
        public CompactConnections {
            Objects.requireNonNull(
                    connections,
                    "connections");
        }
    }

    record BlendConnections(NeighborConnections connections) implements GeneratedTileRecipe {
        public BlendConnections {
            Objects.requireNonNull(connections, "connections");
        }
    }

    record OverlayMask17(int slot) implements GeneratedTileRecipe {
        public OverlayMask17 {
            if (slot < 0 || slot >= 17) {
                throw new IllegalArgumentException("Overlay mask slot must be in [0, 16]");
            }
        }
    }
}
