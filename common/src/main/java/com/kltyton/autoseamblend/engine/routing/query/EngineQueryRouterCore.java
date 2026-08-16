package com.kltyton.autoseamblend.engine.routing.query;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.plan.MethodPlanResolution;
import com.kltyton.autoseamblend.engine.query.ConnectionQuery;
import com.kltyton.autoseamblend.engine.query.EngineQueryContext;
import com.kltyton.autoseamblend.engine.query.ExactSurfaceIdentity;
import com.kltyton.autoseamblend.engine.query.QueryObservation;
import com.kltyton.autoseamblend.engine.query.SurfaceFace;
import com.kltyton.autoseamblend.engine.registry.EngineRegistryRuntimeState;
import com.kltyton.autoseamblend.inference.InferenceFacts;
import com.kltyton.autoseamblend.reload.rule.ManagedRule;
import com.kltyton.autoseamblend.runtime.publication.PublicationScopedCache;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.runtime.surface.PreparedSurfaceMethods;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.selection.query.SelectionIntent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：精确表面与摘要查询共享的五级来源、引擎选择、AUTO 和槽位补全核心。
 *
 * <p>English: Shared exact-surface and summary-query core for five-tier provenance, engine
 * selection, AUTO resolution, and slot completion.
 */
public final class EngineQueryRouterCore {
    private static final PublicationScopedCache<
                    ReloadPublication.Generation,
                    EngineSummaryKey,
                    Optional<EngineQuerySelection>>
            SUMMARY_CACHE = new PublicationScopedCache<>(ReloadPublication.current());
    private static final PublicationScopedCache<
                    ReloadPublication.Generation,
                    BlockState,
                    StateQueryContext>
            EXACT_CONTEXT_CACHE = new PublicationScopedCache<>(ReloadPublication.current());

    private EngineQueryRouterCore() {}

    public static void reset(ReloadPublication.Generation publication) {
        Objects.requireNonNull(publication, "publication");
        SUMMARY_CACHE.reset(publication);
        EXACT_CONTEXT_CACHE.reset(publication);
    }

    public static Optional<EngineQuerySelection> exact(
            EngineRegistryRuntimeState engines,
            ReloadPublication.Generation runtime,
            BlockState state,
            BlockAndTintGetter level,
            BlockPos pos,
            BakedQuad quad,
            TextureAtlasSprite sprite,
            NativeContextFactory contextFactory) {
        Objects.requireNonNull(engines, "engines");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(quad, "quad");
        Objects.requireNonNull(sprite, "sprite");
        Objects.requireNonNull(contextFactory, "contextFactory");
        if (engines.engineRequired()) {
            return Optional.empty();
        }
        Optional<FaceSurface> face = runtime.surfaces().face(state, quad.direction(), sprite);
        if (face.isEmpty() && !runtime.modelOwnership().owners(state).isEmpty()) {
            face = runtime.surfaces().preferredFace(state, quad.direction());
        }
        if (face.isEmpty()) {
            return summary(engines, runtime, state, false);
        }
        FaceSurface surface = face.orElseThrow();
        StateQueryContext queryContext =
                EXACT_CONTEXT_CACHE.entries(runtime)
                        .computeIfAbsent(
                                state,
                                key -> StateQueryContext.create(
                                        runtime,
                                        key));
        Direction direction = quad.direction();
        SurfaceQueryContext surfaceQuery = queryContext.surface(
                direction,
                sprite.contents().name());
        String blockId = queryContext.blockId();
        Optional<SelectionIntent> explicit = queryContext.explicit();
        Optional<SelectionIntent> implicit = queryContext.implicit();
        boolean excluded = queryContext.excluded();

        ConnectionMethod observationMethod = explicit
                .map(SelectionIntent::method)
                .or(() -> implicit.map(SelectionIntent::method))
                .orElse(ConnectionMethod.AUTO);
        ConnectionQuery observationQuery = new ConnectionQuery(
                blockId,
                ExactSurfaceIdentity.stateIdentity(state),
                SurfaceFace.valueOf(quad.direction().name()),
                sprite.contents().name().toString(),
                observationMethod);
        EngineQueryContext nativeContext = Objects.requireNonNull(
                contextFactory.create(level, pos, state, quad, sprite, surface, runtime),
                "nativeContext");
        List<QueryObservation> observations = EngineQueryRouting.observations(
                observationQuery,
                nativeContext,
                engines.readyAdapters());
        Optional<EngineQueryRouting.ExecutionSelection> executing =
                EngineQueryRouting.selectExecution(new EngineQueryRouting.ExecutionInput(
                        engines.registry(),
                        observations,
                        explicit,
                        implicit));
        if (executing.isEmpty()) {
            return Optional.empty();
        }
        EngineQueryRouting.ExecutionSelection execution = executing.orElseThrow();
        ConnectionMethod requested = execution.requestedMethod();
        PreparedSurfaceMethods.Snapshot prepared = runtime.preparedMethods();
        Optional<MethodPlanResolution> preparedMethod = requested == ConnectionMethod.AUTO
                ? surfaceQuery.prepared(prepared, execution.engine().engineId())
                : Optional.empty();
        if (requested == ConnectionMethod.AUTO && preparedMethod.isEmpty()) {
            return Optional.empty();
        }
        ConnectionQuery query = new ConnectionQuery(
                blockId,
                ExactSurfaceIdentity.stateIdentity(state),
                SurfaceFace.valueOf(quad.direction().name()),
                sprite.contents().name().toString(),
                requested);
        // 中文：AUTO 已在预缝合阶段解析一次；精确查询必须沿用同一组事实。
        // English: AUTO is resolved once before stitching; the exact query reuses those facts.
        InferenceFacts queryFacts = preparedMethod
                .map(MethodPlanResolution::facts)
                .orElse(surface.facts());
        EngineRouteSelection route = EngineQueryRouting.resolveExact(
                new EngineQueryRouting.ExactInput(
                        prepared.generation(),
                        prepared.reloadToken(),
                        engines.registry(),
                        execution,
                        query,
                        explicit,
                        implicit,
                        excluded,
                        queryFacts,
                        preparedMethod));
        Optional<ManagedRule> managedRule = route.provenance()
                .documentSource()
                .filter(value -> route.provenance().source()
                                == EngineRouteSource.MANAGED_COMPATIBILITY
                        || route.provenance().source()
                                == EngineRouteSource.MANAGED_NON_COMPATIBILITY)
                .flatMap(value -> exactManagedRule(
                        runtime,
                        route.engine().family(),
                        blockId,
                        value.resourceId()));
        return Optional.of(new EngineQuerySelection(route, managedRule, prepared));
    }

    public static Optional<EngineQuerySelection> fallback(
            EngineRegistryRuntimeState engines,
            ReloadPublication.Generation runtime) {
        Objects.requireNonNull(engines, "engines");
        Objects.requireNonNull(runtime, "runtime");
        return EngineQueryRouting.fallback(engines.registry())
                .map(route -> new EngineQuerySelection(
                        route,
                        Optional.empty(),
                        runtime.preparedMethods()));
    }

    public static Optional<EngineQuerySelection> summary(
            EngineRegistryRuntimeState engines,
            ReloadPublication.Generation runtime,
            BlockState state,
            boolean continuityNativeExact) {
        Objects.requireNonNull(engines, "engines");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(state, "state");
        return SUMMARY_CACHE.entries(runtime).computeIfAbsent(
                new EngineSummaryKey(state, continuityNativeExact),
                key -> computeSummary(
                        engines,
                        runtime,
                        key.state(),
                        key.continuityNativeExact()));
    }

    private static Optional<EngineQuerySelection> computeSummary(
            EngineRegistryRuntimeState engines,
            ReloadPublication.Generation runtime,
            BlockState state,
            boolean continuityNativeExact) {
        String blockId = ExactSurfaceIdentity.blockId(state);
        RuleRuntime.Snapshot ruleSnapshot = runtime.selectors();
        Optional<ConnectionRuleSet.CompiledSelector<Block>> configured =
                ruleSnapshot.rules().configuredSelector(state.getBlock());
        boolean excluded = configured
                .map(selector -> ruleSnapshot.rules().isExcluded(
                        state.getBlock(),
                        selector.method(),
                        selector.mode()))
                .orElseGet(() -> ruleSnapshot.rules().isExcluded(
                        state.getBlock(),
                        ConnectionMethod.AUTO,
                        ConnectionRuleSet.ResourcePackMode.COMPATIBILITY));
        Set<EngineFamily> owners =
                new java.util.LinkedHashSet<>(runtime.modelOwnership().owners(state));
        if (continuityNativeExact) {
            owners.add(EngineFamily.MCPATCHER);
        }
        LinkedHashMap<String, String> unknownDiagnostics = new LinkedHashMap<>();
        for (String engineId : engines.readyEngineIds()) {
            if (!owners.contains(engines.family(engineId))) {
                runtime.captureHealth()
                        .unknownDiagnostic(engineId, state)
                        .ifPresent(diagnostic -> unknownDiagnostics.put(engineId, diagnostic));
            }
        }
        Optional<SelectionIntent> configuredIntent =
                configured.map(EngineQueryRouting::explicitIntent);
        return EngineQueryRouting.summary(new EngineQueryRouting.SummaryInput(
                        engines.registry(),
                        blockId,
                        owners,
                        unknownDiagnostics,
                        configuredIntent,
                        excluded,
                        ruleSnapshot.automaticDiscovery()))
                .map(route -> new EngineQuerySelection(
                        route,
                        Optional.empty(),
                        runtime.preparedMethods()));
    }

    private static Optional<ManagedRule> exactManagedRule(
            ReloadPublication.Generation runtime,
            EngineFamily family,
            String blockId,
            String resourceId) {
        return runtime.managedRules().rules(family, blockId).stream()
                .filter(rule -> rule.resourceId().equals(resourceId))
                .findFirst();
    }

    /**
     * 中文：代次内不可变方块状态事实与有界并发查询模板缓存。原生观察保持逐查询采集，
     * 因为它们依赖 level/position/邻居，绝不能缓存。
     *
     * <p>English: Generation-local immutable state facts plus bounded concurrent
     * query-template caches. Native observations remain uncached because they depend on
     * level, position, and neighboring blocks.
     */
    private static final class StateQueryContext {
        private final BlockState state;
        private final String blockId;
        private final Map<String, String> stateIdentity;
        private final Optional<SelectionIntent> explicit;
        private final Optional<SelectionIntent> implicit;
        private final boolean excluded;
        private final List<ConcurrentHashMap<Identifier, SurfaceQueryContext>> surfaces;

        private StateQueryContext(
                BlockState state,
                String blockId,
                Map<String, String> stateIdentity,
                Optional<SelectionIntent> explicit,
                Optional<SelectionIntent> implicit,
                boolean excluded) {
            this.state = Objects.requireNonNull(state, "state");
            this.blockId = Objects.requireNonNull(blockId, "blockId");
            this.stateIdentity = Objects.requireNonNull(stateIdentity, "stateIdentity");
            this.explicit = Objects.requireNonNull(explicit, "explicit");
            this.implicit = Objects.requireNonNull(implicit, "implicit");
            this.excluded = excluded;
            ArrayList<ConcurrentHashMap<Identifier, SurfaceQueryContext>> maps =
                    new java.util.ArrayList<>(Direction.values().length);
            for (int index = 0; index < Direction.values().length; index++) {
                maps.add(new ConcurrentHashMap<>());
            }
            this.surfaces = List.copyOf(maps);
        }

        private static StateQueryContext create(
                ReloadPublication.Generation runtime,
                BlockState state) {
            RuleRuntime.Snapshot rules = runtime.selectors();
            Optional<ConnectionRuleSet.CompiledSelector<Block>> configured =
                    rules.rules().configuredSelector(state.getBlock());
            boolean excluded = configured
                    .map(selector -> rules.rules().isExcluded(
                            state.getBlock(), selector.method(), selector.mode()))
                    .orElseGet(() -> rules.rules().isExcluded(
                            state.getBlock(),
                            ConnectionMethod.AUTO,
                            ConnectionRuleSet.ResourcePackMode.COMPATIBILITY));
            String blockId = ExactSurfaceIdentity.blockId(state);
            Optional<SelectionIntent> explicit = configured.map(EngineQueryRouting::explicitIntent);
            Optional<SelectionIntent> implicit =
                    rules.automaticDiscovery() && configured.isEmpty() && !excluded
                            ? Optional.of(EngineQueryRouting.implicitIntent(blockId))
                            : Optional.empty();
            return new StateQueryContext(
                    state,
                    blockId,
                    ExactSurfaceIdentity.stateIdentity(state),
                    explicit,
                    implicit,
                    excluded);
        }

        private SurfaceQueryContext surface(
                Direction direction,
                Identifier spriteId) {
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(spriteId, "spriteId");
            return surfaces.get(direction.ordinal()).computeIfAbsent(
                    spriteId,
                    key -> new SurfaceQueryContext(
                            state,
                            blockId,
                            stateIdentity,
                            direction,
                            key));
        }

        private String blockId() {
            return blockId;
        }

        private Optional<SelectionIntent> explicit() {
            return explicit;
        }

        private Optional<SelectionIntent> implicit() {
            return implicit;
        }

        private boolean excluded() {
            return excluded;
        }
    }

    private static final class SurfaceQueryContext {
        private final BlockState state;
        private final Direction direction;
        private final Identifier spriteId;
        private final String preparedIdentity;
        private final String blockId;
        private final Map<String, String> stateIdentity;
        private final SurfaceFace face;
        private final String serializedSpriteId;
        private final ConcurrentHashMap<ConnectionMethod, ConnectionQuery> queries =
                new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Optional<MethodPlanResolution>> prepared =
                new ConcurrentHashMap<>();

        private SurfaceQueryContext(
                BlockState state,
                String blockId,
                Map<String, String> stateIdentity,
                Direction direction,
                Identifier spriteId) {
            this.state = Objects.requireNonNull(state, "state");
            this.blockId = Objects.requireNonNull(blockId, "blockId");
            this.stateIdentity = Objects.requireNonNull(stateIdentity, "stateIdentity");
            this.direction = Objects.requireNonNull(direction, "direction");
            this.spriteId = Objects.requireNonNull(spriteId, "spriteId");
            this.face = SurfaceFace.valueOf(direction.name());
            this.serializedSpriteId = spriteId.toString();
            this.preparedIdentity = "prepared:"
                    + blockId
                    + ':'
                    + direction.name()
                    + ':'
                    + spriteId;
        }

        private ConnectionQuery query(ConnectionMethod method) {
            return queries.computeIfAbsent(
                    Objects.requireNonNull(method, "method"),
                    key -> new ConnectionQuery(
                            blockId,
                            stateIdentity,
                            face,
                            serializedSpriteId,
                            key));
        }

        private Optional<MethodPlanResolution> prepared(
                PreparedSurfaceMethods.Snapshot snapshot,
                String engineId) {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(engineId, "engineId");
            return prepared.computeIfAbsent(
                    engineId,
                    key -> snapshot.prepared(state, direction, spriteId)
                            .map(value -> value.resolution(
                                    snapshot.generation(),
                                    snapshot.reloadToken(),
                                    key,
                                    preparedIdentity)));
        }
    }

    @FunctionalInterface
    public interface NativeContextFactory {
        EngineQueryContext create(
                BlockAndTintGetter level,
                BlockPos pos,
                BlockState state,
                BakedQuad quad,
                TextureAtlasSprite sprite,
                FaceSurface surface,
                ReloadPublication.Generation runtime);
    }
}
