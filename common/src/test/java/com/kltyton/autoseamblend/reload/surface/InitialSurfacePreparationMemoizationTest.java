package com.kltyton.autoseamblend.reload.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kltyton.autoseamblend.runtime.surface.ModelGeometryInspector;
import com.kltyton.autoseamblend.runtime.surface.SurfaceSourceProvenance;
import com.kltyton.autoseamblend.runtime.surface.SurfaceSourceSnapshot;
import com.kltyton.autoseamblend.texture.atlas.InitialBlockAtlasResources.ReadEvidence;
import com.kltyton.autoseamblend.texture.atlas.InitialBlockAtlasResources.Snapshot;
import com.kltyton.autoseamblend.texture.atlas.InitialBlockAtlasResources.SourceRead;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：F1 memoization 合同：单次 reload 内同一模型依赖只被 ModelGeometryInspector 检查
 * 一次，且所有引用它的 state/批次共享同一不可变 Result（输出逐位等价）。
 *
 * <p>English: F1 memoization contract: inside one reload the same model dependency is
 * inspected by ModelGeometryInspector exactly once, and every state/batch referencing it
 * shares one immutable Result (bit-for-bit equivalent output).
 */
class InitialSurfacePreparationMemoizationTest {
    private static final ResourceLocation SHARED_MODEL_ID =
            ResourceLocation.parse("minecraft:block/cube_all");
    private static final ResourceLocation OTHER_MODEL_ID =
            ResourceLocation.parse("minecraft:block/cube_bottom_top");

    private static final String SHARED_MODEL_JSON = """
            {
              "textures": {
                "all": "minecraft:block/stone"
              },
              "elements": [
                {
                  "from": [0, 0, 0],
                  "to": [16, 16, 16],
                  "faces": {
                    "up": {"texture": "#all", "uv": [0, 0, 16, 16]},
                    "down": {"texture": "#all", "uv": [0, 0, 16, 16]}
                  }
                }
              ]
            }
            """;
    private static final String OTHER_MODEL_JSON = """
            {
              "textures": {
                "top": "minecraft:block/stone",
                "bottom": "minecraft:block/stone",
                "side": "minecraft:block/stone"
              },
              "elements": [
                {
                  "from": [0, 0, 0],
                  "to": [16, 16, 16],
                  "faces": {
                    "up": {"texture": "#top", "uv": [0, 0, 16, 16]},
                    "down": {"texture": "#bottom", "uv": [0, 0, 16, 16]},
                    "north": {"texture": "#side", "uv": [0, 0, 16, 16]}
                  }
                }
              ]
            }
            """;

    private static BlockModel sharedModel;
    private static BlockModel otherModel;
    private static Snapshot atlas;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
        sharedModel = BlockModel.fromString(
                SHARED_MODEL_JSON);
        otherModel = BlockModel.fromString(
                OTHER_MODEL_JSON);
        atlas = atlasSnapshot();
    }

    @Test
    void sameDependencyInspectsExactlyOnceAcrossCalls() {
        CountingResults results = new CountingResults();

        InitialSurfacePreparation.inspectGeometry(
                SHARED_MODEL_ID,
                sharedModel,
                atlas,
                results);
        InitialSurfacePreparation.inspectGeometry(
                SHARED_MODEL_ID,
                sharedModel,
                atlas,
                results);

        assertEquals(
                1,
                results.inspectionCount(),
                "same dependency must be inspected exactly once");
        assertEquals(
                1,
                results.produced().size(),
                "both calls must share one cached result");
    }

    @Test
    void differentDependenciesAreInspectedSeparately() {
        CountingResults results = new CountingResults();

        InitialSurfacePreparation.inspectGeometry(
                SHARED_MODEL_ID,
                sharedModel,
                atlas,
                results);
        InitialSurfacePreparation.inspectGeometry(
                OTHER_MODEL_ID,
                otherModel,
                atlas,
                results);

        assertEquals(
                2,
                results.inspectionCount(),
                "different dependencies keep independent inspections");
        assertEquals(
                2,
                results.produced().size(),
                "each dependency produces its own result");
    }

    @Test
    void concurrentSameDependencyInspectsExactlyOnce()
            throws Exception {
        CountingResults results = new CountingResults();
        int threads = 8;
        ExecutorService executor =
                Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures =
                    new ArrayList<>(threads);
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    InitialSurfacePreparation
                            .inspectGeometry(
                                    SHARED_MODEL_ID,
                                    sharedModel,
                                    atlas,
                                    results);
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(
                1,
                results.inspectionCount(),
                "concurrent same-dependency calls must inspect once");
        assertEquals(
                1,
                results.produced().size(),
                "concurrent calls must share one cached result");
    }

    private static Snapshot atlasSnapshot() {
        ResourceLocation spriteId = ResourceLocation.parse(
                "minecraft:block/stone");
        int[] pixels = new int[16 * 16];
        Arrays.fill(pixels, 0xFF000000);
        SurfaceSourceSnapshot image =
                new SurfaceSourceSnapshot(
                        spriteId.toString(),
                        16,
                        16,
                        16,
                        16,
                        pixels,
                        false,
                        true,
                        false,
                        SurfaceSourceProvenance.DIRECT_RESOURCE);
        return new Snapshot(Map.of(
                spriteId,
                new SourceRead(
                        Optional.of(image),
                        ReadEvidence.DIRECT_PNG,
                        "DIRECT_PNG")));
    }

    /**
     * 中文：统计真实检查次数的 ConcurrentHashMap 子类：每次实际执行 mapping 函数
     * （即 ModelGeometryInspector.inspect）都计数并记录产物。
     *
     * <p>English: ConcurrentHashMap subclass that counts real inspections: every actual
     * mapping-function execution (i.e. ModelGeometryInspector.inspect) is counted and its
     * produced result recorded.
     */
    private static final class CountingResults
            extends ConcurrentHashMap<
                    ResourceLocation,
                    ModelGeometryInspector.Result> {
        private final AtomicInteger inspected =
                new AtomicInteger();
        private final List<ModelGeometryInspector.Result>
                produced = Collections.synchronizedList(
                        new ArrayList<>());

        @Override
        public ModelGeometryInspector.Result
                computeIfAbsent(
                        ResourceLocation key,
                        Function<? super ResourceLocation,
                                ? extends ModelGeometryInspector.Result>
                                mappingFunction) {
            return super.computeIfAbsent(
                    key,
                    resolved -> {
                        inspected.incrementAndGet();
                        ModelGeometryInspector.Result value =
                                mappingFunction.apply(resolved);
                        produced.add(value);
                        return value;
                    });
        }

        int inspectionCount() {
            return inspected.get();
        }

        List<ModelGeometryInspector.Result> produced() {
            return List.copyOf(produced);
        }
    }
}
