package com.kltyton.autoseamblend.reload.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.kltyton.autoseamblend.runtime.surface.ModelGeometryInspector;
import com.kltyton.autoseamblend.runtime.surface.SurfaceSourceProvenance;
import com.kltyton.autoseamblend.runtime.surface.SurfaceSourceSnapshot;
import com.kltyton.autoseamblend.texture.atlas.InitialBlockAtlasResources;
import com.kltyton.autoseamblend.texture.atlas.InitialBlockAtlasResources.ReadEvidence;
import com.kltyton.autoseamblend.texture.atlas.InitialBlockAtlasResources.Snapshot;
import com.kltyton.autoseamblend.texture.atlas.InitialBlockAtlasResources.SourceRead;
import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：inspectGeometry reload-local 缓存合同：同一个 dependency 在冻结的
 * blockModels/atlas 边界内只执行一次真实几何检查；不同 dependency 各自独立；
 * 多个检查线程共享同一 dependency 时也只执行一次。
 *
 * English: inspectGeometry reload-local cache contract: one dependency is really
 * inspected once inside the frozen blockModels/atlas boundary; distinct
 * dependencies stay independent; concurrent inspection threads sharing one
 * dependency still perform exactly one real inspection.
 */
class InitialSurfacePreparationGeometryCacheContractTest {
    private static final ResourceLocation DEPENDENCY =
            new ResourceLocation("test", "cube_shared");
    private static final ResourceLocation OTHER_DEPENDENCY =
            new ResourceLocation("test", "cube_other");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("test", "tex");
    private static Snapshot atlas;

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
        SurfaceSourceSnapshot source = new SurfaceSourceSnapshot(
                TEXTURE.toString(),
                16,
                16,
                16,
                16,
                new int[16 * 16],
                false,
                true,
                false,
                SurfaceSourceProvenance.DIRECT_RESOURCE);
        SourceRead read = new SourceRead(
                Optional.of(source),
                ReadEvidence.DIRECT_PNG,
                "DIRECT_PNG");
        atlas = new Snapshot(Map.of(TEXTURE, read));
    }

    @Test
    void sameDependencyAcrossCallsInspectsOnce() {
        ConcurrentHashMap<ResourceLocation, ModelGeometryInspector.Result> cache =
                new ConcurrentHashMap<>();
        CountingModel model = countingModel(DEPENDENCY);

        InitialSurfacePreparation.inspectGeometry(
                DEPENDENCY,
                model,
                cache,
                atlas);
        InitialSurfacePreparation.inspectGeometry(
                DEPENDENCY,
                model,
                cache,
                atlas);

        assertEquals(
                1,
                model.inspections(),
                "same dependency across calls must be inspected exactly once");
        assertNotNull(
                cache.get(DEPENDENCY),
                "inspected dependency must be retained by the reload-local cache");
    }

    @Test
    void distinctDependenciesInspectSeparately() {
        ConcurrentHashMap<ResourceLocation, ModelGeometryInspector.Result> cache =
                new ConcurrentHashMap<>();
        CountingModel shared = countingModel(DEPENDENCY);
        CountingModel other = countingModel(OTHER_DEPENDENCY);

        InitialSurfacePreparation.inspectGeometry(
                DEPENDENCY,
                shared,
                cache,
                atlas);
        InitialSurfacePreparation.inspectGeometry(
                OTHER_DEPENDENCY,
                other,
                cache,
                atlas);
        InitialSurfacePreparation.inspectGeometry(
                DEPENDENCY,
                shared,
                cache,
                atlas);

        assertEquals(
                1,
                shared.inspections(),
                "each distinct dependency keeps its own cache entry");
        assertEquals(
                1,
                other.inspections(),
                "other dependency must not be served from the first dependency's entry");
    }

    @Test
    void eightThreadsSameDependencyInspectOnce() throws Exception {
        ConcurrentHashMap<ResourceLocation, ModelGeometryInspector.Result> cache =
                new ConcurrentHashMap<>();
        CountingModel model = countingModel(DEPENDENCY);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            ArrayList<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                futures.add(pool.submit(() -> {
                    InitialSurfacePreparation.inspectGeometry(
                            DEPENDENCY,
                            model,
                            cache,
                            atlas);
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
            assertEquals(
                    1,
                    model.inspections(),
                    "concurrent callers sharing one dependency must inspect it once");
        } finally {
            pool.shutdownNow();
        }
    }

    private static CountingModel countingModel(
            ResourceLocation dependency) {
        List<BlockElement> elements = List.of(new BlockElement(
                new Vector3f(0.0F, 0.0F, 0.0F),
                new Vector3f(1.0F, 1.0F, 1.0F),
                Map.of(Direction.NORTH, new BlockElementFace(
                        null,
                        -1,
                        "#all",
                        new BlockFaceUV(
                                new float[] {0.0F, 0.0F, 16.0F, 16.0F},
                                0))),
                null,
                true));
        Map<String, Either<Material, String>> textures = Map.of(
                "all",
                Either.left(new Material(
                        TextureAtlas.LOCATION_BLOCKS,
                        TEXTURE)));
        return new CountingModel(elements, textures);
    }

    /**
     * 中文：每次真实几何检查恰好调用两次 getElements()（fullBlock 流 + 面循环），
     * 因此调用数 / 2 就是 ModelGeometryInspector 真实执行次数。
     *
     * English: one real geometry inspection calls getElements() exactly twice (the
     * fullBlock stream plus the face loop), so call count / 2 is the number of real
     * ModelGeometryInspector executions.
     */
    private static final class CountingModel extends BlockModel {
        private final List<BlockElement> elements;
        private final AtomicInteger getElementsCalls = new AtomicInteger();

        private CountingModel(
                List<BlockElement> elements,
                Map<String, Either<Material, String>> textures) {
            super(
                    null,
                    elements,
                    textures,
                    true,
                    BlockModel.GuiLight.SIDE,
                    ItemTransforms.NO_TRANSFORMS,
                    List.<ItemOverride>of());
            this.elements = elements;
        }

        @Override
        public List<BlockElement> getElements() {
            getElementsCalls.incrementAndGet();
            return elements;
        }

        private int inspections() {
            return getElementsCalls.get() / 2;
        }
    }
}
