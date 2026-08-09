package com.kltyton.autoseamblend.neoforge.compat.athena.runtime.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import earth.terrarium.athena.api.client.models.AthenaQuad;
import earth.terrarium.athena.api.client.utils.AppearanceAndTintGetter;
import earth.terrarium.athena.api.client.utils.AthenaUtils;
import earth.terrarium.athena.api.client.utils.CtmState;
import earth.terrarium.athena.api.client.utils.CtmUtils;
import earth.terrarium.athena.impl.client.models.PaneConnectedBlockModel;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：持久回归合同——Athena 4.0.6 玻璃板单侧连接必须把原生 full-height sprite-0 旧
 * 条带替换为两个角色半 quad（4.7.3 connectCorners=true 语义），且控制流与 4.7.3 true
 * 精确对齐：先构造 CtmState，allTrue 必须走原生 CENTER/super，双侧/无侧走 super，只有
 * 非 allTrue 且恰一侧连接才早返回替换；禁止在 super 结果后追加。
 *
 * <p>English: Persistent regression contract. Athena 4.0.6 single-side pane connections
 * must replace the native full-height sprite-0 legacy strip with two role half quads
 * (4.7.3 connectCorners=true semantics), with control flow aligned to 4.7.3 true: build
 * CtmState first, allTrue must take the native CENTER/super path, both-sides and no-side
 * take super, and only a non-allTrue exactly-one-side connection takes the early-return
 * replacement; appending after super is forbidden.
 */
class AthenaPaneSingleSideCornerContractTest {
    private static final Int2ObjectMap<Material> MATERIALS =
            new Int2ObjectArrayMap<>();

    @BeforeAll
    static void bootstrapRegistries() {
        // 中文：独立 JVM 测试需要游戏版本与注册表引导，否则 Blocks/IronBarsBlock 静态
        // 初始化抛 ExceptionInInitializerError；仅测试初始化。
        // English: Standalone JVM tests need a game version and registry bootstrap or
        // Blocks/IronBarsBlock static init throws ExceptionInInitializerError;
        // test-only initialization.
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        LoadingModList.of(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of());
        Bootstrap.bootStrap();
    }

    @Test
    void singleCwSideReplacesFullHeightStripWithTwoRoleQuadrants() {
        // 中文：SOUTH=true 其余臂关，face=EAST → cw=SOUTH 连、ccw=NORTH 断；getter 使
        // 世界 UP 邻居为 air，保证 ctmState 非 allTrue（不误入 CENTER）。
        // English: SOUTH=true with the other arms off and face=EAST makes cw=SOUTH
        // connected and ccw=NORTH disconnected; the getter makes the world-UP neighbor air
        // so the CtmState is not allTrue.
        BlockState state = Blocks.GLASS_PANE.defaultBlockState()
                .setValue(PipeBlock.SOUTH, true);
        AppearanceAndTintGetter getter = fakeGetter(
                state,
                Set.of(BlockPos.ZERO.relative(Direction.UP)));
        AthenaGeneratedPaneModelFactory.RuleAwarePaneModel model =
                new AthenaGeneratedPaneModelFactory.RuleAwarePaneModel(
                        MATERIALS,
                        emptyRules());

        List<AthenaQuad> quads = model.getQuads(
                getter,
                state,
                BlockPos.ZERO,
                Direction.EAST);

        assertEquals(
                2,
                quads.size(),
                "single CW side must emit exactly two role half quads");
        assertNoLegacyStrip(quads);
        float arm = arm(state, Direction.EAST);
        assertLeftPair(quads.get(0), quads.get(1), arm);
        assertCwTruthTableSprites(quads, getter, state);
    }

    @Test
    void singleCcwSideReplacesFullHeightStripWithTwoRoleQuadrants() {
        // 中文：NORTH=true 其余臂关，face=EAST → ccw=NORTH 连、cw=SOUTH 断；UP 邻居 air
        // 保证非 allTrue。
        // English: NORTH=true with the other arms off and face=EAST makes ccw=NORTH
        // connected and cw=SOUTH disconnected; the UP neighbor is air so the CtmState is
        // not allTrue.
        BlockState state = Blocks.GLASS_PANE.defaultBlockState()
                .setValue(PipeBlock.NORTH, true);
        AppearanceAndTintGetter getter = fakeGetter(
                state,
                Set.of(BlockPos.ZERO.relative(Direction.UP)));
        AthenaGeneratedPaneModelFactory.RuleAwarePaneModel model =
                new AthenaGeneratedPaneModelFactory.RuleAwarePaneModel(
                        MATERIALS,
                        emptyRules());

        List<AthenaQuad> quads = model.getQuads(
                getter,
                state,
                BlockPos.ZERO,
                Direction.EAST);

        assertEquals(
                2,
                quads.size(),
                "single CCW side must emit exactly two role half quads");
        assertNoLegacyStrip(quads);
        float arm = arm(state, Direction.EAST);
        assertRightPair(quads.get(0), quads.get(1), arm);
        assertCcwTruthTableSprites(quads, getter, state);
    }

    @Test
    void allTrueWithSingleArmPropertyKeepsNativeCenterSemantics() {
        // 中文：属性只有单臂（SOUTH=true），但 getter 全位置返回同 pane → ctmState
        // allTrue → 4.7.3 true 必须走原生 CENTER（4.0.6 字节码：单 quad sprite=1、
        // left=0、right=1、top=1、bottom=0、depth=0.4375），绝不能被单侧分支改写成
        // 2 个半 quad。注意不能拿 base 4.0.6 模型当基线：它是 connectCorners=false，
        // 且无本项目 identity 连接 fallback，allTrue 下返回的是 false 语义单臂。
        // English: The property has a single arm (SOUTH=true) but the getter returns the
        // pane everywhere, making CtmState allTrue; 4.7.3 true must take the native CENTER
        // path (4.0.6 bytecode: a single quad sprite=1, left=0, right=1, top=1, bottom=0,
        // depth=0.4375) and never be rewritten into two half quads by the single-side
        // branch. The raw 4.0.6 base model is not the baseline here: it is
        // connectCorners=false and lacks this project's identity-connection fallback, so
        // under allTrue it returns the false-semantics single arm.
        BlockState state = Blocks.GLASS_PANE.defaultBlockState()
                .setValue(PipeBlock.SOUTH, true);
        AppearanceAndTintGetter getter = fakeGetter(state, Set.of());
        AthenaGeneratedPaneModelFactory.RuleAwarePaneModel model =
                new AthenaGeneratedPaneModelFactory.RuleAwarePaneModel(
                        MATERIALS,
                        emptyRules());

        List<AthenaQuad> quads = model.getQuads(
                getter,
                state,
                BlockPos.ZERO,
                Direction.EAST);

        assertEquals(
                List.of(new AthenaQuad(
                        1,
                        0.0F,
                        1.0F,
                        1.0F,
                        0.0F,
                        Rotation.NONE,
                        0.4375F)),
                quads,
                "allTrue must keep the native CENTER/super semantics");
        assertFalse(
                quads.size() == 2
                        && !quads.get(0).equals(quads.get(1)),
                "allTrue must not be rewritten into a single-side replacement pair");
    }

    @Test
    void bothSidesAndNoSideMatchDirectSuperBaseline() {
        // 中文：双侧与无侧都必须与直接 super 基线逐值等价（4.7.3 true 也走同一原生分支）。
        // English: Both-sides and no-side must equal the direct super baseline value for
        // value (4.7.3 true takes the same native branches).
        assertMatchesBaseline(
                Blocks.GLASS_PANE.defaultBlockState()
                        .setValue(PipeBlock.NORTH, true)
                        .setValue(PipeBlock.SOUTH, true),
                allNeighborPositions(),
                "both-sides");
        assertMatchesBaseline(
                Blocks.GLASS_PANE.defaultBlockState(),
                Set.of(
                        BlockPos.ZERO.relative(Direction.UP),
                        BlockPos.ZERO.relative(Direction.DOWN),
                        BlockPos.ZERO.relative(Direction.NORTH),
                        BlockPos.ZERO.relative(Direction.SOUTH),
                        BlockPos.ZERO.relative(Direction.EAST),
                        BlockPos.ZERO.relative(Direction.WEST)),
                "no-side");
    }

    private static void assertMatchesBaseline(
            BlockState state,
            Set<BlockPos> airPositions,
            String label) {
        AppearanceAndTintGetter getter =
                fakeGetter(state, airPositions);
        AthenaGeneratedPaneModelFactory.RuleAwarePaneModel model =
                new AthenaGeneratedPaneModelFactory.RuleAwarePaneModel(
                        MATERIALS,
                        emptyRules());
        PaneConnectedBlockModel baseline =
                new PaneConnectedBlockModel(MATERIALS);
        assertEquals(
                baseline.getQuads(
                        getter,
                        state,
                        BlockPos.ZERO,
                        Direction.EAST),
                model.getQuads(
                        getter,
                        state,
                        BlockPos.ZERO,
                        Direction.EAST),
                label + " must match the direct super baseline");
        if ("both-sides".equals(label)) {
            assertEquals(
                    4,
                    model.getQuads(
                                    getter,
                                    state,
                                    BlockPos.ZERO,
                                    Direction.EAST)
                            .size(),
                    "both-sides must hit the native four-quadrant double-arm path");
        }
    }

    /** 中文：CW 单侧的两个 quad 槽位必须等于 CtmUtils.getTexture 的 (up,left,upLeft) 与 (down,left,downLeft) 真值表，防参数对调。 / English: The CW pair's slots must equal the CtmUtils.getTexture (up,left,upLeft)/(down,left,downLeft) truth table, guarding against argument swaps. */
    private static void assertCwTruthTableSprites(
            List<AthenaQuad> quads,
            AppearanceAndTintGetter getter,
            BlockState state) {
        CtmState expected = CtmState.from(
                getter,
                state,
                BlockPos.ZERO,
                Direction.EAST,
                (pos, neighborState, appearance) ->
                        appearance.getBlock() == state.getBlock());
        assertEquals(
                CtmUtils.getTexture(
                        expected.up(),
                        expected.left(),
                        expected.upLeft()),
                quads.get(0).sprite(),
                "CW top-half sprite must follow the (up,left,upLeft) truth table");
        assertEquals(
                CtmUtils.getTexture(
                        expected.down(),
                        expected.left(),
                        expected.downLeft()),
                quads.get(1).sprite(),
                "CW bottom-half sprite must follow the (down,left,downLeft) truth table");
    }

    /** 中文：CCW 单侧槽位等于 (up,right,upRight)/(down,right,downRight) 真值表。 / English: The CCW pair's slots must equal the (up,right,upRight)/(down,right,downRight) truth table. */
    private static void assertCcwTruthTableSprites(
            List<AthenaQuad> quads,
            AppearanceAndTintGetter getter,
            BlockState state) {
        CtmState expected = CtmState.from(
                getter,
                state,
                BlockPos.ZERO,
                Direction.EAST,
                (pos, neighborState, appearance) ->
                        appearance.getBlock() == state.getBlock());
        assertEquals(
                CtmUtils.getTexture(
                        expected.up(),
                        expected.right(),
                        expected.upRight()),
                quads.get(0).sprite(),
                "CCW top-half sprite must follow the (up,right,upRight) truth table");
        assertEquals(
                CtmUtils.getTexture(
                        expected.down(),
                        expected.right(),
                        expected.downRight()),
                quads.get(1).sprite(),
                "CCW bottom-half sprite must follow the (down,right,downRight) truth table");
    }

    /** 中文：ZERO 的六个正交与四个对角邻居位置。 / English: The six cardinal and four diagonal neighbor positions of ZERO. */
    private static Set<BlockPos> allNeighborPositions() {
        Set<BlockPos> positions = new java.util.HashSet<>();
        for (Direction direction : Direction.values()) {
            positions.add(BlockPos.ZERO.relative(direction));
            if (direction.getAxis().isHorizontal()) {
                for (Direction vertical : new Direction[] {
                        Direction.UP, Direction.DOWN}) {
                    positions.add(BlockPos.ZERO.relative(vertical)
                            .relative(direction));
                }
            }
        }
        return positions;
    }

    private static void assertNoLegacyStrip(
            List<AthenaQuad> quads) {
        assertFalse(
                quads.stream().anyMatch(quad ->
                        quad.sprite() == 0
                                && quad.top() == 1.0F
                                && quad.bottom() == 0.0F),
                "the legacy full-height sprite-0 strip must be replaced");
    }

    /** 中文：CW 侧左列上下两半；几何按 4.7.3 true 分支字节码逐值锁定。 / English: CW-side left-column top/bottom halves; geometry locked from the 4.7.3 true-branch bytecode. */
    private static void assertLeftPair(
            AthenaQuad top,
            AthenaQuad bottom,
            float arm) {
        assertEquals(0.0F, top.left(), 1.0e-6F);
        assertEquals(1.0F - arm, top.right(), 1.0e-6F);
        assertEquals(1.0F, top.top(), 1.0e-6F);
        assertEquals(0.5F, top.bottom(), 1.0e-6F);
        assertEquals(0.0F, bottom.left(), 1.0e-6F);
        assertEquals(1.0F - arm, bottom.right(), 1.0e-6F);
        assertEquals(0.5F, bottom.top(), 1.0e-6F);
        assertEquals(0.0F, bottom.bottom(), 1.0e-6F);
        assertHalfPairCommon(top, bottom);
    }

    /** 中文：CCW 侧右列上下两半。 / English: CCW-side right-column top/bottom halves. */
    private static void assertRightPair(
            AthenaQuad top,
            AthenaQuad bottom,
            float arm) {
        assertEquals(arm, top.left(), 1.0e-6F);
        assertEquals(1.0F, top.right(), 1.0e-6F);
        assertEquals(1.0F, top.top(), 1.0e-6F);
        assertEquals(0.5F, top.bottom(), 1.0e-6F);
        assertEquals(arm, bottom.left(), 1.0e-6F);
        assertEquals(1.0F, bottom.right(), 1.0e-6F);
        assertEquals(0.5F, bottom.top(), 1.0e-6F);
        assertEquals(0.0F, bottom.bottom(), 1.0e-6F);
        assertHalfPairCommon(top, bottom);
    }

    private static void assertHalfPairCommon(
            AthenaQuad top,
            AthenaQuad bottom) {
        assertEquals(0.4375F, top.depth(), 1.0e-6F);
        assertEquals(0.4375F, bottom.depth(), 1.0e-6F);
        assertEquals(top.bottom(), bottom.top(), 1.0e-6F);
    }

    private static float arm(BlockState state, Direction face) {
        return AthenaUtils.getFromDir(state, face)
                ? 0.5625F
                : 0.4375F;
    }

    private static ConnectionRuleSet<Block> emptyRules() {
        return ConnectionRuleSet.compile(
                        Map.of(),
                        Map.of(),
                        trivialResolver())
                .rules();
    }

    private static ConnectionRuleSet.Resolver<Block> trivialResolver() {
        return new ConnectionRuleSet.Resolver<Block>() {
            @Override
            public boolean isValidId(String id) {
                return false;
            }

            @Override
            public Optional<Block> block(String id) {
                return Optional.empty();
            }

            @Override
            public Set<Block> tag(String id) {
                return Set.of();
            }

            @Override
            public String id(Block value) {
                return "";
            }
        };
    }

    /** 中文：精确邻居 getter——指定位置返回 AIR，其余返回 pane 状态。 / English: Precise-neighbor getter returning AIR at the given positions and the pane state elsewhere. */
    private static AppearanceAndTintGetter fakeGetter(
            BlockState pane,
            Set<BlockPos> airPositions) {
        return new AppearanceAndTintGetter() {
            private BlockState at(BlockPos pos) {
                return airPositions.contains(pos)
                        ? Blocks.AIR.defaultBlockState()
                        : pane;
            }

            @Override
            public BlockState getAppearance(
                    BlockState source,
                    BlockPos pos,
                    Direction face,
                    BlockState otherState,
                    BlockPos otherPos) {
                return at(pos);
            }

            @Override
            public BlockState getAppearance(
                    BlockPos pos,
                    Direction face) {
                return at(pos);
            }

            @Override
            public BlockState getAppearance(
                    BlockPos pos,
                    Direction face,
                    BlockState source,
                    BlockPos otherPos) {
                return at(pos);
            }

            @Override
            public Query query(
                    BlockPos pos,
                    Direction face,
                    BlockState source,
                    BlockPos otherPos) {
                BlockState appearance = at(pos);
                return new Query(appearance, appearance);
            }

            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                return at(pos);
            }

            @Override
            public FluidState getFluidState(BlockPos pos) {
                return Fluids.EMPTY.defaultFluidState();
            }

            @Override
            public int getHeight() {
                return 0;
            }

            @Override
            public int getMinBuildHeight() {
                return 0;
            }

            @Override
            public float getShade(
                    Direction direction,
                    boolean shade) {
                return 1.0F;
            }

            @Override
            public LevelLightEngine getLightEngine() {
                return null;
            }

            @Override
            public int getBlockTint(
                    BlockPos pos,
                    ColorResolver resolver) {
                return -1;
            }
        };
    }
}
