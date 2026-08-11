package com.kltyton.autoseamblend.forge.compat.ctm_mod.runtime;

import com.kltyton.autoseamblend.forge.testing.ForgeTestBootstrap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.texture.geometry.TextureBasis;
import com.kltyton.autoseamblend.texture.geometry.TextureCorner;
import com.kltyton.autoseamblend.texture.geometry.TextureEdge;
import com.kltyton.autoseamblend.texture.geometry.WorldDirection;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：CTM 邻接采样网格契约回归测试。以 origin 为世界中心，在 UP 面（水平面）构造
 * 3x3 同种玻璃布局，用“同 ID 自连接”的 ConnectionRuleSet，验证
 * {@link CtmModNativeConnectionSampler#sampleStandard} 对全邻接、直边、对角得到的
 * 原始八邻域位；并锁定“单一 SOUTH 直边邻居不得折叠为 slot0”。
 *
 * <p>English: CTM adjacency-sampling grid-contract regression test. Centers the world on
 * origin, lays out a 3x3 same-block glass grid on the UP face (horizontal plane), uses a
 * same-id self-connecting ConnectionRuleSet, and verifies raw eight-neighbor bits from
 * {@link CtmModNativeConnectionSampler#sampleStandard} for full adjacency, edges, and
 * diagonals; it also locks that a single SOUTH edge neighbor must never collapse to slot0.
 */
class CtmModNativeConnectionSamplerGridContractTest {

    @BeforeAll
    static void bootstrapRegistries() {
        // 中文：独立 JVM 测试需要游戏版本与注册表引导，否则 Blocks/FeatureFlags 静态
        // 初始化抛 ExceptionInInitializerError；与既有 delegate 测试同型，仅测试初始化。
        // English: Standalone JVM tests need a game version and registry bootstrap or
        // Blocks/FeatureFlags static init throws ExceptionInInitializerError; same shape
        // as the existing delegate test, test-only initialization.
        ForgeTestBootstrap.bootStrap();
    }

    @Test
    void full3x3UpFaceIsAllEightBits() {
        // 中文：origin 周围 3x3（同 Y 平面）全玻璃时，UP 面八邻域全部连接，raw bits
        // 必须为 0xFF。
        // English: With a full 3x3 glass grid around origin on the same Y plane, all
        // eight UP-face neighbors connect and raw bits must be 0xFF.
        GridLevel level = new GridLevel();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.set(
                        BlockPos.ZERO.offset(dx, 0, dz),
                        Blocks.GLASS.defaultBlockState());
            }
        }
        assertEquals(
                0xFF,
                sampleBits(level),
                "full 3x3 glass on the UP face must connect all eight neighbors");
    }

    @Test
    void singleSouthNeighborKeepsDownBitAndNeverCollapsesToSlot0() {
        // 中文：只有 SOUTH 直边邻居时，UP 面必须置 TextureEdge.DOWN 位，且 normalized
        // 位不得为 0（不得折叠成 slot0 孤立 tile）。
        // English: With only the SOUTH edge neighbor present, the UP face must set the
        // TextureEdge.DOWN bit and the normalized bits must not be zero (never collapsed
        // into the isolated slot0 tile).
        GridLevel level = new GridLevel();
        level.set(
                BlockPos.ZERO.offset(0, 0, 1),
                Blocks.GLASS.defaultBlockState());
        NeighborConnections connections = sample(level);
        assertTrue(
                connections.connected(TextureEdge.DOWN),
                "SOUTH edge neighbor must set TextureEdge.DOWN");
        assertNotEquals(
                0,
                connections.normalizedCtmBits(),
                "single SOUTH edge neighbor must not collapse to slot0");
    }

    @Test
    void southAndWestEdgesSetDownLeftAndCorner() {
        // 中文：SOUTH+WEST 两条直边邻居（含 SOUTH-WEST 对角）时，UP 面得到
        // DOWN+LEFT+BL；BL 因两条相邻直边都在而保留。
        // English: With SOUTH and WEST edge neighbors (and the SOUTH-WEST diagonal),
        // the UP face yields DOWN+LEFT+BL; BL survives because both adjacent edges exist.
        GridLevel level = new GridLevel();
        level.set(BlockPos.ZERO.offset(0, 0, 1), Blocks.GLASS.defaultBlockState());
        level.set(BlockPos.ZERO.offset(-1, 0, 0), Blocks.GLASS.defaultBlockState());
        level.set(BlockPos.ZERO.offset(-1, 0, 1), Blocks.GLASS.defaultBlockState());
        NeighborConnections connections = sample(level);
        int expected = (1 << TextureEdge.DOWN.connectionBit())
                | (1 << TextureEdge.LEFT.connectionBit())
                | (1 << TextureCorner.BOTTOM_LEFT.connectionBit());
        assertEquals(
                expected,
                connections.bits(),
                "SOUTH+WEST edges plus diagonal must set DOWN, LEFT and BL");
    }

    @Test
    void isolatedDiagonalKeepsRawCornerBitButNormalizesAway() {
        // 中文：仅 SOUTH-EAST 对角（无直边）时，raw bits 只有 BR 角位；normalized 因
        // 两条相邻直边缺失而被清除（与 26.1.2 的角折叠语义一致，最终为 slot0）。
        // English: With only the SOUTH-EAST diagonal (no edges), raw bits contain only
        // the BR corner bit; normalized clears it because both adjacent edges are absent
        // (consistent with 26.1.2 corner folding semantics, ultimately slot0).
        GridLevel level = new GridLevel();
        level.set(
                BlockPos.ZERO.offset(1, 0, 1),
                Blocks.GLASS.defaultBlockState());
        NeighborConnections connections = sample(level);
        assertEquals(
                1 << TextureCorner.BOTTOM_RIGHT.connectionBit(),
                connections.bits(),
                "isolated diagonal must keep only its raw corner bit");
        assertEquals(
                0,
                connections.normalizedCtmBits(),
                "isolated diagonal must normalize away (slot0 behavior)");
    }

    @Test
    void sideFaceUpNeighborSetsUpBit() {
        // 中文：侧面（NORTH）上方邻居（世界 +Y）必须置 TextureEdge.UP 位；CTM 1.21
        // Dir 在 NORTH 面的 TOP 偏移为 (0,1,0)（世界 UP），应被项目 basis 映射到 UP 位。
        // English: On the NORTH side face, the world +Y neighbor must set the
        // TextureEdge.UP bit; CTM 1.21 Dir keeps TOP as world UP on NORTH, which the
        // project basis must map to the UP bit.
        GridLevel level = new GridLevel();
        level.set(
                BlockPos.ZERO.offset(0, 1, 0),
                Blocks.GLASS.defaultBlockState());
        assertTrue(
                sampleOnFace(level, Direction.NORTH)
                        .connected(TextureEdge.UP),
                "world +Y neighbor on the NORTH face must set TextureEdge.UP");
    }

    @Test
    void sideFaceDownNeighborSetsDownBit() {
        // 中文：侧面（NORTH）下方邻居（世界 -Y）必须置 TextureEdge.DOWN 位。
        // English: On the NORTH side face, the world -Y neighbor must set the
        // TextureEdge.DOWN bit.
        GridLevel level = new GridLevel();
        level.set(
                BlockPos.ZERO.offset(0, -1, 0),
                Blocks.GLASS.defaultBlockState());
        assertTrue(
                sampleOnFace(level, Direction.NORTH)
                        .connected(TextureEdge.DOWN),
                "world -Y neighbor on the NORTH face must set TextureEdge.DOWN");
    }

    @Test
    void sideFaceVerticalStackSetsUpAndDown() {
        // 中文：侧面（NORTH）上下同时有邻居时，UP 与 DOWN 位都必须置位（竖直堆叠）。
        // English: With both world +Y and -Y neighbors on the NORTH face, both the
        // UP and DOWN bits must be set (vertical stack).
        GridLevel level = new GridLevel();
        level.set(
                BlockPos.ZERO.offset(0, 1, 0),
                Blocks.GLASS.defaultBlockState());
        level.set(
                BlockPos.ZERO.offset(0, -1, 0),
                Blocks.GLASS.defaultBlockState());
        NeighborConnections connections =
                sampleOnFace(level, Direction.NORTH);
        assertTrue(
                connections.connected(TextureEdge.UP)
                        && connections.connected(TextureEdge.DOWN),
                "vertical stack must set both UP and DOWN");
    }

    private static NeighborConnections sampleOnFace(
            GridLevel level,
            Direction face) {
        return sampler().sampleStandard(
                level,
                BlockPos.ZERO,
                Blocks.GLASS.defaultBlockState(),
                face,
                TextureBasis.canonical(
                        WorldDirection.valueOf(
                                face.name())),
                RandomSource.create(0));
    }

    private static int sampleBits(GridLevel level) {
        return sample(level).bits();
    }

    private static NeighborConnections sample(GridLevel level) {
        return sampler().sampleStandard(
                level,
                BlockPos.ZERO,
                Blocks.GLASS.defaultBlockState(),
                Direction.UP,
                TextureBasis.canonical(WorldDirection.UP),
                RandomSource.create(0));
    }

    private static CtmModNativeConnectionSampler sampler() {
        return new CtmModNativeConnectionSampler(
                TestSprite.INSTANCE,
                Blocks.GLASS,
                selfConnectRules(),
                false,
                (level,
                        pos,
                        face,
                        state,
                        otherState,
                        otherPos) -> state);
    }

    /**
     * 中文：同 ID 自连接规则：auto 桶、non-compatibility 模式，仅注册 minecraft:glass。
     *
     * <p>English: Same-id self-connecting rules: auto bucket, non-compatibility mode,
     * registering only minecraft:glass.
     */
    private static ConnectionRuleSet<Block> selfConnectRules() {
        return ConnectionRuleSet.compile(
                        Map.of(
                                "auto",
                                Map.of(
                                        "non-compatibility",
                                        List.of("minecraft:glass"))),
                        Map.of(),
                        new ConnectionRuleSet.Resolver<Block>() {
                            @Override
                            public boolean isValidId(String id) {
                                return true;
                            }

                            @Override
                            public Optional<Block> block(String id) {
                                return Optional.ofNullable(
                                        BuiltInRegistries.BLOCK.get(
                                                new ResourceLocation(id)));
                            }

                            @Override
                            public Set<Block> tag(String id) {
                                return Set.of();
                            }

                            @Override
                            public String id(Block value) {
                                return BuiltInRegistries.BLOCK.getKey(value)
                                        .toString();
                            }
                        })
                .rules();
    }

    /**
     * 中文：最小可用的 16x16 测试精灵；UV 归一化为 0..1，不依赖 Atlas 或 GL。
     *
     * <p>English: Minimal usable 16x16 test sprite with normalized 0..1 UVs; no Atlas
     * or GL dependency.
     */
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

    /**
     * 中文：网格 BlockAndTintGetter 测试替身；缺省为 AIR，可按世界偏移放置方块状态。
     *
     * <p>English: Grid BlockAndTintGetter test double; defaults to AIR and accepts
     * block states placed by world offset.
     */
    private static final class GridLevel
            implements BlockAndTintGetter {
        private final Map<BlockPos, BlockState> states =
                new HashMap<>();

        private void set(BlockPos pos, BlockState state) {
            states.put(pos.immutable(), state);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return states.getOrDefault(
                    pos.immutable(),
                    Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return 1;
        }

        @Override
        public int getMinBuildHeight() {
            return 0;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return null;
        }

        @Override
        public float getShade(
                Direction direction,
                boolean shade) {
            return 1.0F;
        }

        @Override
        public int getBlockTint(
                BlockPos pos,
                ColorResolver resolver) {
            return -1;
        }
    }
}
