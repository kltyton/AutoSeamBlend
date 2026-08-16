package com.kltyton.autoseamblend.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.kltyton.autoseamblend.compat.athena.generation.AthenaGeneratedSpritePlan;
import com.kltyton.autoseamblend.compat.athena.runtime.texture.AthenaGeneratedStateSprites;
import com.kltyton.autoseamblend.compat.continuity.runtime.texture.ContinuityGeneratedStateSprites;
import com.kltyton.autoseamblend.compat.ctm_mod.runtime.texture.CtmModGeneratedSpritePlan;
import com.kltyton.autoseamblend.compat.ctm_mod.runtime.texture.CtmModGeneratedStateSprites;
import com.kltyton.autoseamblend.compat.fusion.runtime.texture.FusionGeneratedStateSprites;
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
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.engine.routing.ModelOwnershipRuntime;
import com.kltyton.autoseamblend.engine.routing.NativeCaptureHealth;
import com.kltyton.autoseamblend.inference.InferenceDecision;
import com.kltyton.autoseamblend.inference.InferenceFacts;
import com.kltyton.autoseamblend.reload.rule.ManagedRuleSnapshot;
import com.kltyton.autoseamblend.reload.rule.NativeRuleSnapshot;
import com.kltyton.autoseamblend.reload.surface.InitialSurfacePreparation;
import com.kltyton.autoseamblend.reload.surface.InitialSurfacePreparation.CandidateStatus;
import com.kltyton.autoseamblend.reload.surface.InitialSurfacePreparation.StateCandidate;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.PreparedSurfaceMethods;
import com.kltyton.autoseamblend.runtime.surface.SurfaceSourceProvenance;
import com.kltyton.autoseamblend.runtime.surface.SurfaceSourceSnapshot;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSet;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSetCatalog;
import com.kltyton.autoseamblend.texture.atlas.InitialBlockAtlasResources;
import com.kltyton.autoseamblend.texture.atlas.ResolvedSpriteCatalog;
import com.kltyton.autoseamblend.texture.generation.GeneratedSpriteTransform;
import com.kltyton.autoseamblend.texture.generation.fusion.FusionGeneratedTextureIdentity;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import com.kltyton.autoseamblend.texture.profile.InitialTextureProfileFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：验证四个 generated-sprite planner 的资格过滤前置与 reload-local profile
 * 记忆化。同一 source 的多 surface 必须共享同一个 {@link OverlayCutoutProfile} 实例，
 * 且入口键/顺序与逐 surface 参考计算完全一致。运行在 fabric 测试源集以同时覆盖
 * continuity/athena/fusion 的运行时 ABI 依赖。
 *
 * <p>English: Verifies filter-before-profile ordering and reload-local profile memoization in
 * the four generated-sprite planners. Surfaces sharing one source must share one
 * {@link OverlayCutoutProfile} instance, while entry keys and order stay identical to a
 * per-surface reference computation. Lives in the fabric test source set so the
 * continuity/athena/fusion runtime ABIs are on the classpath.
 */
class GeneratedSpritePlannerMemoizationContractTest {
    private static final long GENERATION = 42L;
    private static final Identifier STONE_SPRITE =
            Identifier.parse("minecraft:block/stone");
    private static final Identifier DIRT_SPRITE =
            Identifier.parse("minecraft:block/dirt");

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void continuitySharedSourceProfileIsMemoizedAndEntriesAreEquivalent() {
        assertProfileMemoization(
                EngineFamily.MCPATCHER,
                ConnectionMethod.OVERLAY,
                ContinuityGeneratedStateSprites::planInitial,
                (source, profile) -> com.kltyton.autoseamblend.texture.generation.ContinuityGeneratedSpritePlan
                        .key(source, ConnectionMethod.CTM, profile),
                (source, profile) -> com.kltyton.autoseamblend.texture.generation.ContinuityGeneratedSpritePlan
                        .key(source, ConnectionMethod.OVERLAY, profile));
    }

    @Test
    void athenaSharedSourceProfileIsMemoizedAndEntriesAreEquivalent() {
        assertProfileMemoization(
                EngineFamily.ATHENA,
                ConnectionMethod.CTM_COMPACT,
                AthenaGeneratedStateSprites::planInitial,
                (source, profile) -> AthenaGeneratedSpritePlan
                        .physicalKey(source, ConnectionMethod.CTM, profile),
                (source, profile) -> AthenaGeneratedSpritePlan
                        .physicalKey(source, ConnectionMethod.CTM_COMPACT, profile));
    }

    @Test
    void ctmModSharedSourceProfileIsMemoizedAndEntriesAreEquivalent() {
        assertProfileMemoization(
                EngineFamily.CTM_MOD,
                ConnectionMethod.OVERLAY,
                CtmModGeneratedStateSprites::planInitial,
                (source, profile) -> CtmModGeneratedSpritePlan
                        .key(source, ConnectionMethod.CTM, profile),
                (source, profile) -> CtmModGeneratedSpritePlan
                        .key(source, ConnectionMethod.OVERLAY, profile));
    }

    @Test
    void fusionSharedSourceProfileIsMemoizedAndEntriesAreEquivalent() {
        assertProfileMemoization(
                EngineFamily.FUSION,
                ConnectionMethod.OVERLAY,
                FusionGeneratedStateSprites::planInitial,
                (source, profile) -> FusionGeneratedTextureIdentity
                        .physicalKey(source, ConnectionMethod.CTM, profile),
                (source, profile) -> FusionGeneratedTextureIdentity
                        .physicalKey(source, ConnectionMethod.OVERLAY, profile));
    }

    private static void assertProfileMemoization(
            EngineFamily family,
            ConnectionMethod secondMethod,
            Planner planner,
            KeyFactory firstKeyFactory,
            KeyFactory secondKeyFactory) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        SurfaceSourceSnapshot stoneSource = source(STONE_SPRITE.toString());
        SurfaceSourceSnapshot dirtSource = source(DIRT_SPRITE.toString());
        OverlayCutoutProfile referenceProfile =
                InitialTextureProfileFactory.from(stoneSource)
                        .overlay(false);

        EngineQueryRouter.installFallback(
                () -> registryState(family));
        RuleRuntime.Snapshot rules = rules();
        PreparedSurfaceMethods.Snapshot preparedMethods =
                preparedMethods(
                        stone,
                        dirt,
                        secondMethod);
        ReloadPublication.Generation planningView =
                generation(rules, preparedMethods);
        InitialSurfacePreparation.Result prepared =
                result(stone, dirt, stoneSource, dirtSource);

        List<GeneratedSpriteSet> sets = planner.plan(
                prepared,
                rules,
                planningView);

        assertEquals(3, sets.size());
        assertEquals(
                firstKeyFactory.apply(
                        STONE_SPRITE,
                        referenceProfile),
                sets.get(0).key());
        assertEquals(
                secondKeyFactory.apply(
                        STONE_SPRITE,
                        referenceProfile),
                sets.get(1).key());
        assertEquals(
                firstKeyFactory.apply(
                        DIRT_SPRITE,
                        referenceProfile),
                sets.get(2).key());
        assertSame(
                overlayProfile(sets.get(0)),
                overlayProfile(sets.get(1)),
                "surfaces sharing one source must reuse one reload-local profile");
    }

    private static OverlayCutoutProfile overlayProfile(
            GeneratedSpriteSet set) {
        GeneratedSpriteTransform transform = set.tiles()
                .get(0)
                .transform();
        return ((GeneratedSpriteTransform.TileRecipe) transform)
                .overlayProfile()
                .orElseThrow();
    }

    private static InitialSurfacePreparation.Result result(
            BlockState stone,
            BlockState dirt,
            SurfaceSourceSnapshot stoneSource,
            SurfaceSourceSnapshot dirtSource) {
        InitialSurfacePreparation.Surface stoneCtm =
                new InitialSurfacePreparation.Surface(
                        stone,
                        Direction.NORTH,
                        stoneSource,
                        InferenceFacts.unknown());
        InitialSurfacePreparation.Surface stoneSecond =
                new InitialSurfacePreparation.Surface(
                        stone,
                        Direction.UP,
                        stoneSource,
                        InferenceFacts.unknown());
        InitialSurfacePreparation.Surface dirtCtm =
                new InitialSurfacePreparation.Surface(
                        dirt,
                        Direction.NORTH,
                        dirtSource,
                        InferenceFacts.unknown());
        return new InitialSurfacePreparation.Result(
                List.of(
                        stoneCtm,
                        stoneSecond,
                        dirtCtm),
                List.of(
                        candidate(stone),
                        candidate(dirt)),
                new InitialBlockAtlasResources.Snapshot(
                        Map.of()),
                List.of());
    }

    private static StateCandidate candidate(
            BlockState state) {
        return new StateCandidate(
                state,
                CandidateStatus.PREPARED,
                List.of("test-candidate"));
    }

    private static SurfaceSourceSnapshot source(
            String spriteId) {
        int[] pixels = new int[16 * 16];
        java.util.Arrays.fill(
                pixels,
                0xFF808080);
        return new SurfaceSourceSnapshot(
                spriteId,
                16,
                16,
                16,
                16,
                pixels,
                false,
                true,
                false,
                SurfaceSourceProvenance.DIRECT_RESOURCE);
    }

    private static PreparedSurfaceMethods.Snapshot preparedMethods(
            BlockState stone,
            BlockState dirt,
            ConnectionMethod secondMethod) {
        LinkedHashMap<
                        PreparedSurfaceMethods.Key,
                        PreparedSurfaceMethods.PreparedMethod>
                methods = new LinkedHashMap<>();
        methods.put(
                new PreparedSurfaceMethods.Key(
                        stone,
                        Direction.NORTH,
                        STONE_SPRITE),
                preparedMethod(ConnectionMethod.CTM));
        methods.put(
                new PreparedSurfaceMethods.Key(
                        stone,
                        Direction.UP,
                        STONE_SPRITE),
                preparedMethod(secondMethod));
        methods.put(
                new PreparedSurfaceMethods.Key(
                        dirt,
                        Direction.NORTH,
                        DIRT_SPRITE),
                preparedMethod(ConnectionMethod.CTM));
        return new PreparedSurfaceMethods.Snapshot(
                GENERATION,
                "test",
                methods);
    }

    private static PreparedSurfaceMethods.PreparedMethod preparedMethod(
            ConnectionMethod method) {
        return new PreparedSurfaceMethods.PreparedMethod(
                InferenceFacts.unknown(),
                new InferenceDecision(
                        ConnectionMethod.AUTO,
                        Optional.of(method),
                        false,
                        InferenceDecision.Confidence.CERTAIN,
                        List.of("test"),
                        List.of()));
    }

    private static RuleRuntime.Snapshot rules() {
        ConnectionRuleSet<Block> emptyRules = ConnectionRuleSet
                .compile(
                        Map.of(),
                        Map.of(),
                        new ConnectionRuleSet.Resolver<Block>() {
                            @Override
                            public boolean isValidId(
                                    String id) {
                                return false;
                            }

                            @Override
                            public Optional<Block> block(
                                    String id) {
                                return Optional.empty();
                            }

                            @Override
                            public Set<Block> tag(
                                    String id) {
                                return Set.of();
                            }

                            @Override
                            public String id(
                                    Block value) {
                                return "";
                            }
                        })
                .rules();
        return new RuleRuntime.Snapshot(
                GENERATION,
                emptyRules,
                true,
                0,
                "test",
                List.of());
    }

    private static ReloadPublication.Generation generation(
            RuleRuntime.Snapshot rules,
            PreparedSurfaceMethods.Snapshot preparedMethods) {
        return new ReloadPublication.Generation(
                GENERATION,
                NativeRuleSnapshot.empty(GENERATION),
                ManagedRuleSnapshot.empty(GENERATION),
                rules,
                preparedMethods,
                GeneratedSpriteSetCatalog.Snapshot.empty(
                        GENERATION),
                MinecraftSurfaceCatalog.Snapshot.empty(
                        GENERATION),
                ModelOwnershipRuntime.Snapshot.empty(
                        GENERATION),
                NativeCaptureHealth.Snapshot.empty(
                        GENERATION),
                ResolvedSpriteCatalog.empty(
                        GENERATION));
    }

    private static EngineRegistryRuntimeState registryState(
            EngineFamily family) {
        EngineDescriptor descriptor = new EngineDescriptor(
                family.formatId(),
                family,
                family.formatId(),
                "test",
                "1.0",
                "test-contract");
        EngineAdapter adapter = new EngineAdapter() {
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
                throw new UnsupportedOperationException(
                        "test adapter never observes");
            }

            @Override
            public NativeMethodMapping mapping(
                    ConnectionMethod method) {
                throw new UnsupportedOperationException(
                        "test adapter never maps");
            }
        };
        EngineRegistration registration =
                new EngineRegistration(
                        descriptor,
                        Optional.of("1.0"),
                        CapabilityMatrix.complete(),
                        new EngineStatus(
                                EngineStatus.State.READY,
                                List.of()),
                        Optional.of(adapter));
        return new EngineRegistryRuntimeState(
                new EngineRegistrySnapshot(
                        List.of(registration),
                        List.of()),
                new EngineSelection(
                        EngineStatus.State.ENGINE_REQUIRED,
                        Optional.empty(),
                        "test",
                        List.of()));
    }

    @FunctionalInterface
    private interface Planner {
        List<GeneratedSpriteSet> plan(
                InitialSurfacePreparation.Result prepared,
                RuleRuntime.Snapshot rules,
                ReloadPublication.Generation planningView);
    }

    @FunctionalInterface
    private interface KeyFactory {
        String apply(
                Identifier source,
                OverlayCutoutProfile profile);
    }
}
