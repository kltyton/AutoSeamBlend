package com.kltyton.autoseamblend.engine.routing.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.kltyton.autoseamblend.engine.EngineAdapter;
import com.kltyton.autoseamblend.engine.EngineDescriptor;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.capability.CapabilityMatrix;
import com.kltyton.autoseamblend.engine.plan.NativeMethodMapping;
import com.kltyton.autoseamblend.engine.query.ConnectionQuery;
import com.kltyton.autoseamblend.engine.query.EngineQueryContext;
import com.kltyton.autoseamblend.engine.query.QueryObservation;
import com.kltyton.autoseamblend.engine.registry.EngineRegistration;
import com.kltyton.autoseamblend.engine.registry.EngineRegistryRuntimeState;
import com.kltyton.autoseamblend.engine.registry.EngineRegistrySnapshot;
import com.kltyton.autoseamblend.engine.registry.EngineSelection;
import com.kltyton.autoseamblend.engine.registry.EngineStatus;
import com.kltyton.autoseamblend.engine.routing.ModelOwnershipRuntime;
import com.kltyton.autoseamblend.engine.routing.NativeCaptureHealth;
import com.kltyton.autoseamblend.inference.ConnectionAxis;
import com.kltyton.autoseamblend.inference.InferenceDecision;
import com.kltyton.autoseamblend.inference.InferenceFacts;
import com.kltyton.autoseamblend.inference.InferenceFacts.FactState;
import com.kltyton.autoseamblend.reload.rule.ManagedRuleSnapshot;
import com.kltyton.autoseamblend.reload.rule.NativeRuleSnapshot;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.PreparedSurfaceMethods;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSetCatalog;
import com.kltyton.autoseamblend.texture.atlas.ResolvedSpriteCatalog;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import com.kltyton.autoseamblend.texture.mask.TextureFrameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：从 1.20.1 移植的 exact-context 缓存合同：同一发布代次内，完全相同的精确查询
 * （同 state/direction/sprite/engine）只准备一次不可变上下文（StateQueryContext/
 * SurfaceQueryContext.prepared 模式：prepared 方法解析按 engine 缓存），而动态原生观察
 * 仍逐查询采集（观察依赖 level/position/邻居，绝不可缓存）。26.1.2 端口丢失了该缓存，
 * 每次 exact() 都重新构建 prepared 方法解析，因此第一个断言为 RED。
 *
 * <p>English: Exact-context cache contract ported from 1.20.1: inside one publication
 * generation, repeated exact queries with identical state/direction/sprite/engine must
 * prepare the immutable context once (the 1.20.1 StateQueryContext/SurfaceQueryContext
 * pattern caches the prepared method resolution per engine), while dynamic native
 * observations stay per query (they depend on level/position/neighbors and must never be
 * cached). The 26.1.2 port dropped that cache and rebuilds the prepared method resolution
 * on every exact() call, so the first assertion is RED.
 */
class EngineQueryRouterCoreExactContextCacheContractTest {
    private static final long GENERATION = 1L;
    private static final String RELOAD_TOKEN =
            "exact-context-cache-red-test";
    private static final String ENGINE_ID =
            "red_test_engine";
    private static final Identifier SPRITE_ID =
            Identifier.parse("minecraft:block/glass");

    private static BlockState state;
    private static TextureAtlasSprite sprite;
    private static BakedQuad quad;
    private static InferenceFacts facts;
    private static ReloadPublication.Generation runtime;
    private static EngineQueryRouterCore.NativeContextFactory contextFactory;

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
        state = Blocks.STONE.defaultBlockState();
        sprite = sprite(SPRITE_ID);
        quad = representativeQuad(sprite, -1);
        facts = facts(true);
        PreparedSurfaceMethods.Snapshot preparedMethods =
                new PreparedSurfaceMethods.Snapshot(
                        GENERATION,
                        RELOAD_TOKEN,
                        Map.of(
                                new PreparedSurfaceMethods.Key(
                                        state,
                                        Direction.NORTH,
                                        SPRITE_ID),
                                new PreparedSurfaceMethods.PreparedMethod(
                                        facts,
                                        new InferenceDecision(
                                                ConnectionMethod.AUTO,
                                                Optional.of(
                                                        ConnectionMethod.CTM),
                                                false,
                                                InferenceDecision.Confidence.CERTAIN,
                                                List.of(
                                                        "exact_context_cache_contract_test"),
                                                List.of()))));
        RuleRuntime.Snapshot selectors = new RuleRuntime.Snapshot(
                GENERATION,
                ConnectionRuleSet.compile(
                                Map.of(),
                                resolver())
                        .rules(),
                true,
                0,
                "exact-context-cache-contract-test",
                List.of());
        MinecraftSurfaceCatalog.FaceSurface face =
                new MinecraftSurfaceCatalog.FaceSurface(
                        Direction.NORTH,
                        sprite,
                        -1,
                        true,
                        false,
                        representativeQuad(sprite, -1),
                        facts,
                        ConnectionMethod.CTM,
                        OverlayCutoutProfile.thinUniform(),
                        new TextureFrameProfile(
                                0.0F,
                                0.0F,
                                0.0F,
                                0.0F));
        MinecraftSurfaceCatalog.Snapshot surfaces =
                new MinecraftSurfaceCatalog.Snapshot(
                        GENERATION,
                        Map.of(
                                state,
                                new MinecraftSurfaceCatalog.StateSurface(
                                        state,
                                        Map.of(
                                                Direction.NORTH,
                                                List.of(face)))),
                        List.of());
        runtime = new ReloadPublication.Generation(
                GENERATION,
                NativeRuleSnapshot.empty(GENERATION),
                ManagedRuleSnapshot.empty(GENERATION),
                selectors,
                preparedMethods,
                GeneratedSpriteSetCatalog.Snapshot.empty(GENERATION),
                surfaces,
                ModelOwnershipRuntime.Snapshot.empty(GENERATION),
                NativeCaptureHealth.Snapshot.empty(GENERATION),
                ResolvedSpriteCatalog.empty(GENERATION));
        contextFactory =
                (level, pos, ignoredState, ignoredQuad, ignoredSprite,
                        surface, ignoredRuntime) ->
                        new EngineQueryContext() {};
    }

    @Test
    void sameExactQueryPreparesImmutableContextOnce() {
        EngineRegistryRuntimeState engines =
                engines(new CountingAdapter());

        EngineQuerySelection first = query(engines);
        EngineQuerySelection second = query(engines);

        assertSame(
                first.resolution()
                        .orElseThrow()
                        .methodResolution(),
                second.resolution()
                        .orElseThrow()
                        .methodResolution(),
                "immutable exact-context preparation must be computed once across "
                        + "repeated exact queries");
    }

    @Test
    void dynamicObservationsRemainPerQuery() {
        CountingAdapter adapter = new CountingAdapter();
        EngineRegistryRuntimeState engines = engines(adapter);

        EngineQuerySelection first = query(engines);
        EngineQuerySelection second = query(engines);

        assertEquals(
                2,
                adapter.observations(),
                "native observations must be recollected for every exact query");
        assertEquals(
                first.resolution()
                        .orElseThrow()
                        .observations(),
                second.resolution()
                        .orElseThrow()
                        .observations(),
                "an adapter without native claims keeps empty per-query observation lists");
    }

    private static EngineQuerySelection query(
            EngineRegistryRuntimeState engines) {
        return EngineQueryRouterCore.exact(
                        engines,
                        runtime,
                        state,
                        BlockAndTintGetter.EMPTY,
                        BlockPos.ZERO,
                        quad,
                        sprite,
                        contextFactory)
                .orElseThrow();
    }

    private static EngineRegistryRuntimeState engines(
            EngineAdapter adapter) {
        EngineRegistration registration = new EngineRegistration(
                adapter.descriptor(),
                Optional.of("4.7.3"),
                CapabilityMatrix.complete(),
                new EngineStatus(
                        EngineStatus.State.READY,
                        List.of()),
                Optional.of(adapter));
        EngineRegistrySnapshot registry =
                new EngineRegistrySnapshot(
                        List.of(registration),
                        List.of());
        return new EngineRegistryRuntimeState(
                registry,
                new EngineSelection(
                        EngineStatus.State.SELECTED,
                        Optional.empty(),
                        "test-selection",
                        List.of()));
    }

    private static ConnectionRuleSet.Resolver<Block> resolver() {
        return new ConnectionRuleSet.Resolver<>() {
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

    private static InferenceFacts facts(boolean opaque) {
        return new InferenceFacts(
                FactState.TRUE,
                FactState.TRUE,
                FactState.TRUE,
                FactState.TRUE,
                FactState.TRUE,
                FactState.of(opaque),
                FactState.FALSE,
                FactState.FALSE,
                FactState.FALSE,
                FactState.TRUE,
                FactState.FALSE,
                FactState.FALSE,
                FactState.FALSE,
                FactState.TRUE,
                EnumSet.of(
                        ConnectionAxis.HORIZONTAL,
                        ConnectionAxis.VERTICAL));
    }

    private static BakedQuad representativeQuad(
            TextureAtlasSprite sprite,
            int tintIndex) {
        return new BakedQuad(
                new Vector3f(0.0F, 0.0F, 0.0F),
                new Vector3f(1.0F, 0.0F, 0.0F),
                new Vector3f(1.0F, 1.0F, 0.0F),
                new Vector3f(0.0F, 1.0F, 0.0F),
                0L,
                0L,
                0L,
                0L,
                Direction.NORTH,
                new BakedQuad.MaterialInfo(
                        sprite,
                        ChunkSectionLayer.CUTOUT,
                        null,
                        tintIndex,
                        true,
                        0));
    }

    /** 中文：位于假定 2048x2048 Atlas 原点的 16x16 测试精灵。 / English: 16x16 test sprite at the assumed 2048x2048 atlas origin. */
    private static TextureAtlasSprite sprite(Identifier name) {
        NativeImage image =
                new NativeImage(16, 16, false);
        SpriteContents contents = new SpriteContents(
                name,
                new FrameSize(16, 16),
                image);
        return new TestSprite(
                TextureAtlas.LOCATION_BLOCKS,
                contents);
    }

    private static final class TestSprite
            extends TextureAtlasSprite {
        private TestSprite(
                Identifier atlasLocation,
                SpriteContents contents) {
            super(
                    atlasLocation,
                    contents,
                    2048,
                    2048,
                    0,
                    0,
                    0);
        }
    }

    private static final class CountingAdapter
            implements EngineAdapter {
        private final AtomicInteger observeCalls =
                new AtomicInteger();
        private final EngineDescriptor descriptor =
                new EngineDescriptor(
                        ENGINE_ID,
                        EngineFamily.ATHENA,
                        "athena",
                        "athena",
                        "4.7.3",
                        "athena-ctm-hook");

        @Override
        public EngineDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public CapabilityMatrix capabilities() {
            return CapabilityMatrix.complete();
        }

        @Override
        public QueryObservation observe(
                ConnectionQuery query,
                EngineQueryContext nativeContext) {
            observeCalls.incrementAndGet();
            return QueryObservation.empty();
        }

        @Override
        public NativeMethodMapping mapping(
                ConnectionMethod method) {
            return NativeMethodMapping.standard(
                    method,
                    value -> "native:"
                            + value.serializedName());
        }

        private int observations() {
            return observeCalls.get();
        }
    }
}
