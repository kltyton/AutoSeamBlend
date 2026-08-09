package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.inference.InferenceDecision;
import com.kltyton.autoseamblend.inference.InferenceFacts;
import com.kltyton.autoseamblend.reload.rule.ManagedRuleSnapshot;
import com.kltyton.autoseamblend.reload.rule.NativeRuleSnapshot;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.PreparedSurfaceMethods;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSetCatalog;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.FabricMaterialBaker;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 合同——首次资源重载烘焙期间（pending 已 prepare、modelFacts 尚未 stage），
 * {@link ReloadPublication#modelDecorationSurfaces()} 回退到 bootstrap 空 surfaces；此时
 * {@link FabricAthenaModelLifecycle#wrap} 仍必须为拥有 pending 预缝合方法的方块安装
 * {@link FabricAthenaConnectedBlockStateModel}，否则全部方块保持原版逐方块边框（26.1.2
 * Fabric Athena 连接纹理完全未生效缺陷）。候选门控同时锁定：无 pending 候选、NONE 方法
 * 候选与完全无 pending 时绝不包装，禁止无条件包装所有方块。
 *
 * <p>English: RED contract -- during the first resource-reload bake (pending prepared but
 * modelFacts not yet staged), ReloadPublication.modelDecorationSurfaces() falls back to the
 * empty bootstrap surfaces; FabricAthenaModelLifecycle.wrap must still install
 * FabricAthenaConnectedBlockStateModel for blocks that own a pending prepared method,
 * otherwise every block keeps its vanilla per-block borders (the 26.1.2 Fabric Athena
 * completely-non-functional defect). The candidate gate also locks that blocks without a
 * pending candidate, with a NONE-resolved candidate, or with no pending generation are
 * never wrapped, so wrapping is never unconditional.
 */
class FabricAthenaDecorationTimingContractTest {
    private static final long GENERATION = 1L;

    @BeforeAll
    static void bootstrapRegistries() {
        // 中文：独立 JVM 测试需要游戏版本与注册表引导，否则 Blocks/BuiltInRegistries
        // 静态初始化抛 ExceptionInInitializerError；纯 vanilla 引导即可。
        // English: Standalone JVM tests need a game version and registry bootstrap or
        // Blocks/BuiltInRegistries static initializers throw; plain vanilla bootstrapping
        // suffices on the Fabric test classpath.
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @AfterEach
    void clearPending() {
        // 中文：清理本用例 stage 的 pending 代次，避免污染其他测试的 ReloadPublication 状态。
        // English: Clears the pending generation staged by this case so other tests never
        // observe leaked ReloadPublication state.
        ReloadPublication.discardPending(GENERATION);
    }

    @Test
    void wrapsGlassFromPendingWhenModelFactsAreNotStaged() {
        BlockState glass = Blocks.GLASS.defaultBlockState();
        ReloadPublication.stagePreparedGeneration(
                prepared(
                        glass,
                        ConnectionMethod.CTM,
                        "test-reload-ctm"));

        // 中文：锁定 RED 前置条件：pending 存在但 modelFacts 未 stage 时，装饰 surfaces 必须为空。
        // English: Locks the RED precondition: with pending prepared but modelFacts unstaged,
        // the decoration surfaces must still be empty (bootstrap generation).
        assertTrue(
                ReloadPublication.modelDecorationSurfaces()
                        .states()
                        .isEmpty(),
                "first bake must see empty decoration surfaces "
                        + "(modelFacts are staged only after MODELS)");

        BlockStateModel result = FabricAthenaModelLifecycle.wrap(
                new RecordingDelegate(),
                new StubAfterBakeContext(glass));

        // 中文：即使 surfaces 为空，pending 预缝合方法存在时仍必须安装 Athena 包装器；
        // 返回原 delegate 正是 26.1.2 Fabric Athena 零连接纹理缺陷。
        // English: Even with empty surfaces, a pending prepared method must still install the
        // Athena wrapper; returning the delegate here reproduces the 26.1.2 Fabric Athena
        // zero-connected-texture defect.
        assertInstanceOf(
                FabricAthenaConnectedBlockStateModel.class,
                result,
                "pending prepared method must install FabricAthenaConnectedBlockStateModel "
                        + "during the first bake before modelFacts are staged");
    }

    @Test
    void keepsDelegateForBlockWithoutPendingCandidate() {
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        ReloadPublication.stagePreparedGeneration(
                prepared(
                        glass,
                        ConnectionMethod.CTM,
                        "test-reload-ctm"));

        BlockStateModel delegate = new RecordingDelegate();
        BlockStateModel result = FabricAthenaModelLifecycle.wrap(
                delegate,
                new StubAfterBakeContext(stone));

        // 中文：候选门控不得无条件包装所有方块；无 pending 方法的 state 必须保留原 delegate。
        // English: The candidate gate must never wrap every block unconditionally; a state
        // without a pending prepared method must keep the original delegate.
        assertSame(
                delegate,
                result,
                "non-candidate state must pass through unchanged");
    }

    @Test
    void keepsDelegateForNoneResolvedPendingCandidate() {
        BlockState glass = Blocks.GLASS.defaultBlockState();
        ReloadPublication.stagePreparedGeneration(
                prepared(
                        glass,
                        ConnectionMethod.NONE,
                        "test-reload-none"));

        BlockStateModel delegate = new RecordingDelegate();
        BlockStateModel result = FabricAthenaModelLifecycle.wrap(
                delegate,
                new StubAfterBakeContext(glass));

        // 中文：shouldDecorate 只认 active surfaces 或同代 pending 的 non-NONE 方法候选；
        // NONE 明确透传的 state 不能被包装。
        // English: shouldDecorate accepts only active surfaces or same-generation pending
        // non-NONE method candidates; a state whose decision is NONE must not be wrapped.
        assertSame(
                delegate,
                result,
                "NONE-resolved candidate must pass through unchanged");
    }

    @Test
    void shouldDecorateIsFalseWithoutAnyPendingGeneration() {
        BlockState glass = Blocks.GLASS.defaultBlockState();
        // 中文：未 stage 任何 pending（active surfaces 也为 bootstrap 空表）时，任何 state
        // 都不得被判定为候选。
        // English: Without any staged pending (and an empty bootstrap active surface table),
        // no state may be judged a candidate.
        assertFalse(
                FabricAthenaModelLifecycle.shouldDecorate(
                        glass),
                "no pending generation must never decorate");
    }

    private static ReloadPublication.Generation prepared(
            BlockState state,
            ConnectionMethod resolved,
            String reloadToken) {
        InferenceDecision decision = new InferenceDecision(
                ConnectionMethod.AUTO,
                Optional.of(resolved),
                false,
                InferenceDecision.Confidence.CERTAIN,
                List.of(),
                List.of());
        PreparedSurfaceMethods.PreparedMethod method =
                new PreparedSurfaceMethods.PreparedMethod(
                        InferenceFacts.unknown(),
                        decision);
        String spritePath = BuiltInRegistries.BLOCK
                .getKey(state.getBlock())
                .getPath();
        PreparedSurfaceMethods.Key key =
                new PreparedSurfaceMethods.Key(
                        state,
                        Direction.UP,
                        Identifier.fromNamespaceAndPath(
                                "minecraft",
                                "block/" + spritePath));
        PreparedSurfaceMethods.Snapshot preparedMethods =
                new PreparedSurfaceMethods.Snapshot(
                        GENERATION,
                        reloadToken,
                        Map.of(key, method));
        RuleRuntime.Snapshot bootstrap =
                RuleRuntime.bootstrapSnapshot();
        RuleRuntime.Snapshot selectors =
                new RuleRuntime.Snapshot(
                        GENERATION,
                        bootstrap.rules(),
                        bootstrap.automaticDiscovery(),
                        bootstrap.selectorCount(),
                        reloadToken,
                        List.of());
        return ReloadPublication.preparedGeneration(
                GENERATION,
                NativeRuleSnapshot.empty(GENERATION),
                ManagedRuleSnapshot.empty(GENERATION),
                selectors,
                preparedMethods,
                GeneratedSpriteSetCatalog.Snapshot.empty(
                        GENERATION));
    }

    /** 中文：最小 BlockStateModel 委托；测试只验证包装决策，不触发任何重载或渲染。 / English: Minimal BlockStateModel delegate; only the wrap decision is under test. */
    private static final class RecordingDelegate
            implements BlockStateModel {
        @Override
        public void collectParts(
                RandomSource random,
                List<BlockStateModelPart> output) {}

        @Override
        public Material.Baked particleMaterial() {
            return null;
        }

        @Override
        public int materialFlags() {
            return 0;
        }
    }

    /** 中文：仅实现 AfterBakeBlock.Context 的 state/sourceModel/baker，其余访问器测试不使用。 / English: AfterBakeBlock.Context stub exposing only state/sourceModel/baker; other accessors are unused. */
    private static final class StubAfterBakeContext
            implements ModelModifier.AfterBakeBlock.Context {
        private final BlockState state;

        private StubAfterBakeContext(BlockState state) {
            this.state = state;
        }

        @Override
        public BlockState state() {
            return state;
        }

        @Override
        public BlockStateModel.UnbakedRoot sourceModel() {
            return null;
        }

        @Override
        public net.minecraft.client.resources.model.ModelBaker baker() {
            return UnusedModelBaker.INSTANCE;
        }
    }

    /**
     * 中文：测试专用 ModelBaker 桩：materials() 返回的 MaterialBaker 在真实使用时明确失败；
     * 玻璃候选在工厂 IronBars 门控前不会触碰材质烘焙。
     *
     * English: Test-only ModelBaker stub: its MaterialBaker fails loudly on real use, but
     * glass candidates never touch material baking before the factory's IronBars gate.
     */
    private static final class UnusedModelBaker
            implements net.minecraft.client.resources.model.ModelBaker {
        private static final UnusedModelBaker INSTANCE =
                new UnusedModelBaker();

        private final MaterialBaker materials =
                new UnusedMaterialBaker();

        @Override
        public ResolvedModel getModel(Identifier id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BlockStateModelPart missingBlockModelPart() {
            throw new UnsupportedOperationException();
        }

        @Override
        public MaterialBaker materials() {
            return materials;
        }

        @Override
        public Interner interner() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T compute(
                SharedOperationKey<T> key) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * 中文：测试专用 MaterialBaker 桩；任何真实烘焙调用都明确失败，防止测试误入材质路径。
     *
     * English: Test-only MaterialBaker stub; every real bake call fails loudly so tests never
     * slip into material baking.
     */
    private static final class UnusedMaterialBaker
            implements MaterialBaker,
                    FabricMaterialBaker {
        @Override
        public Material.Baked get(
                Material material,
                ModelDebugName debugName) {
            throw new UnsupportedOperationException(
                    "test MaterialBaker must not bake");
        }

        @Override
        public Material.Baked reportMissingReference(
                String reference,
                ModelDebugName debugName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SpriteFinder spriteFinder(
                Identifier atlasId) {
            throw new UnsupportedOperationException();
        }
    }
}
