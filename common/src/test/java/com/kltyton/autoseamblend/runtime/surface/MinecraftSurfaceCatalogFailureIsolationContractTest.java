package com.kltyton.autoseamblend.runtime.surface;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MinecraftSurfaceCatalogFailureIsolationContractTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void rejectsOnlyTheThrowingStateAndKeepsScanning() {
        BlockState failingState = Blocks.STONE.defaultBlockState();
        BlockState healthyState = Blocks.DIRT.defaultBlockState();
        Map<BlockState, BakedModel> models = new LinkedHashMap<>();
        models.put(failingState, new StubModel(true));
        models.put(healthyState, new StubModel(false));

        MinecraftSurfaceCatalog.Snapshot first = assertDoesNotThrow(() ->
                MinecraftSurfaceCatalog.prepare(
                        models,
                        PreparedSurfaceMethods.Snapshot.empty(1L),
                        1L));

        assertEquals(0, first.states().size());
        assertEquals(
                List.of(
                        "MODEL_QUADS_REJECTED:" + failingState + ":NullPointerException",
                        "MODEL_QUADS_EMPTY:" + healthyState),
                first.diagnostics());

        MinecraftSurfaceCatalog.Snapshot second = assertDoesNotThrow(() ->
                MinecraftSurfaceCatalog.prepare(
                        models,
                        PreparedSurfaceMethods.Snapshot.empty(2L),
                        2L));
        assertEquals(first.diagnostics(), second.diagnostics());
    }

    private static final class StubModel implements BakedModel {
        private final boolean throwing;

        private StubModel(boolean throwing) {
            this.throwing = throwing;
        }

        @Override
        public List<BakedQuad> getQuads(
                BlockState state,
                Direction direction,
                RandomSource random) {
            if (throwing) {
                throw new NullPointerException("copycat missing material model");
            }
            return List.of();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public boolean isGui3d() {
            return false;
        }

        @Override
        public boolean usesBlockLight() {
            return false;
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return null;
        }

        @Override
        public ItemTransforms getTransforms() {
            return ItemTransforms.NO_TRANSFORMS;
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }
}
