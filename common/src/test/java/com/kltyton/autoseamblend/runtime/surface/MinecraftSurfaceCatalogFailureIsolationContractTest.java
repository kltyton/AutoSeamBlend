package com.kltyton.autoseamblend.runtime.surface;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
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
        Map<BlockState, BlockStateModel> models = new LinkedHashMap<>();
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
                        "MODEL_PARTS_EMPTY:" + healthyState),
                first.diagnostics());

        MinecraftSurfaceCatalog.Snapshot second = assertDoesNotThrow(() ->
                MinecraftSurfaceCatalog.prepare(
                        models,
                        PreparedSurfaceMethods.Snapshot.empty(2L),
                        2L));
        assertEquals(first.diagnostics(), second.diagnostics());
    }

    private static final class StubModel implements BlockStateModel {
        private final boolean throwing;

        private StubModel(boolean throwing) {
            this.throwing = throwing;
        }

        @Override
        public void collectParts(
                RandomSource random,
                List<BlockStateModelPart> output) {
            if (throwing) {
                output.add(new ThrowingPart());
            }
        }

        @Override
        public Material.Baked particleMaterial() {
            return null;
        }

        @Override
        public int materialFlags() {
            return 0;
        }
    }

    private static final class ThrowingPart implements BlockStateModelPart {
        @Override
        public List<BakedQuad> getQuads(Direction cullFace) {
            throw new NullPointerException("copycat missing material model");
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public Material.Baked particleMaterial() {
            return null;
        }

        @Override
        public int materialFlags() {
            return 0;
        }
    }
}
