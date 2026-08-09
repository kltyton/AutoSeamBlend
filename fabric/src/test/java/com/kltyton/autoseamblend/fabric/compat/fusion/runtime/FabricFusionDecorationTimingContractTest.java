package com.kltyton.autoseamblend.fabric.compat.fusion.runtime;

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
import java.util.function.Function;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
 * {@link FabricFusionModelLifecycle#wrap} 仍必须为拥有 pending 预缝合方法的方块安装
 * {@link FabricFusionConnectedBlockStateModel}，否则首会话没有任何方块被 Fusion 包装，
 * 与 Fabric Athena 2026-08-08 零连接纹理缺陷同型。测试隔离 ReloadPublication 全局状态：
 * 每个用例 stage 后在 finally 清理 pending。
 *
 * <p>English: RED contract -- during the first resource-reload bake (pending prepared but
 * modelFacts not yet staged), ReloadPublication.modelDecorationSurfaces() falls back to the
 * empty bootstrap surfaces; FabricFusionModelLifecycle.wrap must still install
 * FabricFusionConnectedBlockStateModel for states that own a pending prepared method, or no
 * block is ever wrapped in the first session (same shape as the 2026-08-08 Fabric Athena
 * zero-connected-texture defect). This test isolates ReloadPublication global state: each
 * case clears pending in finally.
 */
class FabricFusionDecorationTimingContractTest {
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
                prepared(glass));

        // 中文：锁定 RED 前置条件：pending 存在但 modelFacts 未 stage 时，装饰 surfaces 必须为空。
        // English: Locks the RED precondition: with pending prepared but modelFacts unstaged,
        // the decoration surfaces must still be empty (bootstrap generation).
        assertTrue(
                ReloadPublication.modelDecorationSurfaces()
                        .states()
                        .isEmpty(),
                "first bake must see empty decoration surfaces "
                        + "(modelFacts are staged only after MODELS)");

        BakedModel result = FabricFusionModelLifecycle.wrap(
                new RecordingDelegate(),
                new StubAfterBakeContext(glass));

        // 中文：即使 surfaces 为空，pending 预缝合方法存在时仍必须安装 Fusion 包装器；
        // 返回原 delegate 正是首会话零连接纹理缺陷。
        // English: Even with empty surfaces, a pending prepared method must still install the
        // Fusion wrapper; returning the delegate here reproduces the first-session
        // zero-connected-texture defect.
        assertInstanceOf(
                FabricFusionConnectedBlockStateModel.class,
                result,
                "pending prepared method must install FabricFusionConnectedBlockStateModel "
                        + "during the first bake before modelFacts are staged");
    }

    @Test
    void keepsDelegateForBlockWithoutPendingCandidate() {
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        ReloadPublication.stagePreparedGeneration(
                prepared(glass));

        BakedModel delegate = new RecordingDelegate();
        BakedModel result = FabricFusionModelLifecycle.wrap(
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
    void keepsDelegateWhenTopLevelIdIsUnavailable() {
        BakedModel delegate = new RecordingDelegate();
        BakedModel result = FabricFusionModelLifecycle.wrap(
                delegate,
                new StubAfterBakeContext(
                        null,
                        true));

        // 中文：missing-model bake 时 Fabric 允许 AfterBake.Context.topLevelId() 为 null；
        // wrap 必须保留原 delegate 且不抛异常，不得对 STATE_BY_MODEL 执行 get(null)
        //（不可变 Map.copyOf 的 get(null) 抛 NullPointerException）。
        // English: Fabric allows a null AfterBake.Context.topLevelId() during missing-model
        // bakes; wrap must keep the original delegate and never throw, so STATE_BY_MODEL is
        // never queried with null (the immutable Map.copyOf throws NullPointerException on
        // get(null)).
        assertSame(
                delegate,
                result,
                "missing-model bake with null topLevelId must pass through unchanged");
    }

    private static ReloadPublication.Generation prepared(
            BlockState state) {
        InferenceDecision decision = new InferenceDecision(
                ConnectionMethod.AUTO,
                Optional.of(ConnectionMethod.CTM),
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
                        ResourceLocation.fromNamespaceAndPath(
                                "minecraft",
                                "block/" + spritePath));
        PreparedSurfaceMethods.Snapshot preparedMethods =
                new PreparedSurfaceMethods.Snapshot(
                        GENERATION,
                        "test-reload",
                        Map.of(key, method));
        RuleRuntime.Snapshot bootstrap =
                RuleRuntime.bootstrapSnapshot();
        RuleRuntime.Snapshot selectors =
                new RuleRuntime.Snapshot(
                        GENERATION,
                        bootstrap.rules(),
                        bootstrap.automaticDiscovery(),
                        bootstrap.selectorCount(),
                        "test-reload",
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

    /** 中文：最小 BakedModel 委托；测试只验证包装决策，不触发任何重载或渲染。 / English: Minimal BakedModel delegate; only the wrap decision is under test. */
    private static final class RecordingDelegate
            implements BakedModel {
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

    /** 中文：仅实现 topLevelId 的 AfterBake.Context stub，其余访问器测试不使用。 / English: AfterBake.Context stub exposing only topLevelId; other accessors are unused. */
    private static final class StubAfterBakeContext
            implements ModelModifier.AfterBake.Context {
        private final BlockState state;
        private final boolean nullTopLevelId;

        private StubAfterBakeContext(BlockState state) {
            this(state, false);
        }

        private StubAfterBakeContext(
                BlockState state,
                boolean nullTopLevelId) {
            this.state = state;
            this.nullTopLevelId = nullTopLevelId;
        }

        @Override
        public ModelResourceLocation topLevelId() {
            if (nullTopLevelId) {
                return null;
            }
            return BlockModelShaper.stateToModelLocation(
                    state);
        }

        @Override
        public ResourceLocation resourceId() {
            return null;
        }

        @Override
        public UnbakedModel sourceModel() {
            return null;
        }

        @Override
        public Function<Material, TextureAtlasSprite>
                textureGetter() {
            return null;
        }

        @Override
        public ModelState settings() {
            return null;
        }

        @Override
        public ModelBaker baker() {
            return null;
        }

        @Override
        public ModelBakery loader() {
            return null;
        }
    }
}
