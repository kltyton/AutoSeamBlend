package com.kltyton.autoseamblend.forge.frontend.uilib.render.preview;

import com.kltyton.autoseamblend.forge.testing.ForgeTestBootstrap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：预览场景收集合同 RED 测试。锁定 26.1.2 "收集全部 parts" 语义在 1.21.1 的等价：
 * 必须按 model.getRenderTypes 遍历所有广告 pass（含玻璃 translucent 与石 solid），保留
 * direction+null 桶，跨 pass 按 BakedQuad 对象身份去重；每次 getRenderTypes/getQuads
 * 都从固定 seed 新建 RandomSource，不得复用已消耗实例（复用会使后续桶返回空）。当前
 * 实现硬编码 RenderType.cutout() 且无 helper，本测试应失败。
 *
 * <p>English: RED contract test for preview scene collection. Locks the 1.21.1 equivalent
 * of 26.1.2's "collect all parts": all advertised passes from model.getRenderTypes must be
 * collected (translucent glass and solid stone alike), both direction and null buckets kept,
 * and quads deduplicated by BakedQuad object identity across passes; every
 * getRenderTypes/getQuads call must create a fresh RandomSource from a fixed seed instead of
 * reusing a consumed instance (reuse would empty later buckets). The current implementation
 * hardcodes RenderType.cutout() and exposes no helper, so this test is expected to fail.
 */
class ForgeBlockScenePortsSceneCollectContractTest {
    private static final long SEED = 0x5EEDL;

    @BeforeAll
    static void bootstrapRegistries() {
        // 中文：独立 JVM 测试需要游戏版本与注册表引导，否则 RenderType/ChunkRenderTypeSet
        // 静态初始化抛 ExceptionInInitializerError；与 CTM 政策测试同型，仅测试初始化。
        // English: Standalone JVM tests need a game version and registry bootstrap or
        // RenderType/ChunkRenderTypeSet static init throws ExceptionInInitializerError;
        // same shape as the CTM policy test, test-only initialization.
        ForgeTestBootstrap.bootStrap();
    }

    @Test
    void translucentModelQuadsAreCollectedWithFreshRandomPerCall() {
        RecordingModel model = new RecordingModel(
                ChunkRenderTypeSet.of(RenderType.translucent()),
                RenderType.translucent(),
                4,
                0);

        List<BakedQuad> quads =
                ForgeBlockScenePorts.collectSceneQuads(
                        model,
                        Blocks.GLASS.defaultBlockState(),
                        SEED,
                        ModelData.EMPTY);

        assertFalse(
                quads.isEmpty(),
                "translucent glass preview quads must not be empty");
        assertEquals(
                28,
                quads.size(),
                "six direction buckets plus the null bucket, four quads each");
    }

    @Test
    void solidModelQuadsAreCollectedWithFreshRandomPerCall() {
        RecordingModel model = new RecordingModel(
                ChunkRenderTypeSet.of(RenderType.solid()),
                RenderType.solid(),
                4,
                0);

        List<BakedQuad> quads =
                ForgeBlockScenePorts.collectSceneQuads(
                        model,
                        Blocks.STONE.defaultBlockState(),
                        SEED,
                        ModelData.EMPTY);

        assertFalse(
                quads.isEmpty(),
                "solid stone preview quads must not be empty");
        assertEquals(
                28,
                quads.size(),
                "six direction buckets plus the null bucket, four quads each");
    }

    @Test
    void advertisedCutoutOverlayPassIsIncludedWithoutBaseDuplication() {
        RecordingModel model = new RecordingModel(
                ChunkRenderTypeSet.of(
                        RenderType.solid(),
                        RenderType.cutout()),
                RenderType.solid(),
                4,
                2);

        List<BakedQuad> quads =
                ForgeBlockScenePorts.collectSceneQuads(
                        model,
                        Blocks.STONE.defaultBlockState(),
                        SEED,
                        ModelData.EMPTY);

        assertEquals(
                42,
                quads.size(),
                "base 28 plus overlay 14, no cross-pass duplication");
    }

    @Test
    void duplicateQuadsAcrossPassesAreDeduplicatedByIdentity() {
        RecordingModel model = new RecordingModel(
                ChunkRenderTypeSet.of(
                        RenderType.solid(),
                        RenderType.cutout()),
                RenderType.solid(),
                4,
                0,
                true);

        List<BakedQuad> quads =
                ForgeBlockScenePorts.collectSceneQuads(
                        model,
                        Blocks.STONE.defaultBlockState(),
                        SEED,
                        ModelData.EMPTY);

        assertEquals(
                28,
                quads.size(),
                "the same quad instances across passes must be counted once");
    }

    /**
     * 中文：记录型 BakedModel。getRenderTypes 固定返回给定集合；getQuads 在 nativeType 时
     * 返回每桶 baseQuads 个新 quad，在 overlayType 时返回每桶 overlayQuads 个新 quad。
     * 每个 RandomSource 实例只服务一次调用（同一实例再次调用返回空），以此锁定"每次
     * 调用必须新建 RandomSource"；sameInstancesAcrossPasses 时每个桶在两个 pass 返回
     * 同一批对象（桶间仍不同实例），用于锁定跨 pass 身份去重而非跨桶去重。
     *
     * <p>English: Recording BakedModel. getRenderTypes returns the fixed set; getQuads
     * returns baseQuads fresh quads per bucket for nativeType and overlayQuads fresh quads
     * per bucket for the overlay type. Each RandomSource instance serves exactly one call
     * (reuse returns empty), locking the fresh-random-per-call requirement; with
     * sameInstancesAcrossPasses both passes share one object batch to lock identity
     * deduplication.
     */
    private static final class RecordingModel implements BakedModel {
        private final ChunkRenderTypeSet renderTypes;
        private final RenderType nativeType;
        private final int baseQuads;
        private final int overlayQuads;
        private final boolean sameInstancesAcrossPasses;
        private final IdentityHashMap<RandomSource, Boolean> consumed =
                new IdentityHashMap<>();
        private final Map<Direction, List<BakedQuad>> sharedByBucket;

        private RecordingModel(
                ChunkRenderTypeSet renderTypes,
                RenderType nativeType,
                int baseQuads,
                int overlayQuads) {
            this(
                    renderTypes,
                    nativeType,
                    baseQuads,
                    overlayQuads,
                    false);
        }

        private RecordingModel(
                ChunkRenderTypeSet renderTypes,
                RenderType nativeType,
                int baseQuads,
                int overlayQuads,
                boolean sameInstancesAcrossPasses) {
            this.renderTypes = renderTypes;
            this.nativeType = nativeType;
            this.baseQuads = baseQuads;
            this.overlayQuads = overlayQuads;
            this.sameInstancesAcrossPasses =
                    sameInstancesAcrossPasses;
            IdentityHashMap<Direction, List<BakedQuad>> shared =
                    new IdentityHashMap<>();
            if (sameInstancesAcrossPasses) {
                for (Direction direction : Direction.values()) {
                    shared.put(direction, quads(baseQuads));
                }
                shared.put(null, quads(baseQuads));
            }
            this.sharedByBucket = shared;
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(
                BlockState state,
                RandomSource random,
                ModelData modelData) {
            consume(random);
            return renderTypes;
        }

        @Override
        public List<BakedQuad> getQuads(
                BlockState state,
                Direction direction,
                RandomSource random,
                ModelData modelData,
                RenderType renderType) {
            if (!consume(random)) {
                return List.of();
            }
            if (renderType == nativeType) {
                return sameInstancesAcrossPasses
                        ? sharedByBucket.get(direction)
                        : quads(baseQuads);
            }
            if (renderType == RenderType.cutout()
                    && overlayQuads > 0) {
                return sameInstancesAcrossPasses
                        ? sharedByBucket.get(direction)
                        : quads(overlayQuads);
            }
            return List.of();
        }

        @Override
        public List<BakedQuad> getQuads(
                BlockState state,
                Direction direction,
                RandomSource random) {
            return List.of();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return true;
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
            return TestSprite.INSTANCE;
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }

        private boolean consume(RandomSource random) {
            return consumed.putIfAbsent(
                            random,
                            Boolean.TRUE)
                    == null;
        }
    }

    private static List<BakedQuad> quads(int count) {
        VertexFormat format = DefaultVertexFormat.BLOCK;
        int stride = format.getVertexSize() / 4;
        java.util.ArrayList<BakedQuad> quads =
                new java.util.ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            quads.add(new BakedQuad(
                    new int[stride * 4],
                    -1,
                    Direction.NORTH,
                    TestSprite.INSTANCE,
                    false));
        }
        return List.copyOf(quads);
    }

    /** 中文：最小可用测试精灵；仅提供非 null 精灵引用。 / English: Minimal test sprite; only provides a non-null sprite reference. */
    private static final class TestSprite
            extends TextureAtlasSprite {
        private static final TestSprite INSTANCE =
                new TestSprite();

        private TestSprite() {
            super(
                    TextureAtlas.LOCATION_BLOCKS,
                    MissingTextureAtlasSprite.create(),
                    16,
                    16,
                    0,
                    0);
        }
    }
}
