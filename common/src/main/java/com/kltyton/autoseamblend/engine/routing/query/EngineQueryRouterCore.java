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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
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

    private EngineQueryRouterCore() {}

    public static void reset(ReloadPublication.Generation publication) {
        SUMMARY_CACHE.reset(Objects.requireNonNull(publication, "publication"));
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
        Optional<FaceSurface> face = runtime.surfaces().face(
                state,
                quad.getDirection(),
                quad.getDirection(),
                sprite);
        if (face.isEmpty() && !runtime.modelOwnership().owners(state).isEmpty()) {
            face = runtime.surfaces().preferredFace(state, quad.getDirection());
        }
        if (face.isEmpty()) {
            return summary(engines, runtime, state, false);
        }
        FaceSurface surface = face.orElseThrow();
        String blockId = ExactSurfaceIdentity.blockId(state);
        RuleRuntime.Snapshot rules = runtime.selectors();
        Optional<ConnectionRuleSet.CompiledSelector<Block>> configured =
                rules.rules().configuredSelector(state.getBlock());
        boolean excluded = configured
                .map(selector -> rules.rules().isExcluded(
                        state.getBlock(),
                        selector.method(),
                        selector.mode()))
                .orElseGet(() -> rules.rules().isExcluded(
                        state.getBlock(),
                        ConnectionMethod.AUTO,
                        ConnectionRuleSet.ResourcePackMode.COMPATIBILITY));
        Optional<SelectionIntent> explicit = configured.map(EngineQueryRouting::explicitIntent);
        Optional<SelectionIntent> implicit =
                rules.automaticDiscovery() && configured.isEmpty() && !excluded
                        ? Optional.of(EngineQueryRouting.implicitIntent(blockId))
                        : Optional.empty();

        ConnectionMethod observationMethod = explicit
                .map(SelectionIntent::method)
                .or(() -> implicit.map(SelectionIntent::method))
                .orElse(ConnectionMethod.AUTO);
        ConnectionQuery observationQuery = new ConnectionQuery(
                blockId,
                ExactSurfaceIdentity.stateIdentity(state),
                SurfaceFace.valueOf(quad.getDirection().name()),
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
                ? prepared.prepared(state, quad.getDirection(), sprite.contents().name())
                        .map(value -> value.resolution(
                                prepared.generation(),
                                prepared.reloadToken(),
                                execution.engine().engineId(),
                                "prepared:"
                                        + blockId
                                        + ':'
                                        + quad.getDirection().name()
                                        + ':'
                                        + sprite.contents().name()))
                : Optional.empty();
        if (requested == ConnectionMethod.AUTO && preparedMethod.isEmpty()) {
            return Optional.empty();
        }
        ConnectionQuery query = new ConnectionQuery(
                blockId,
                ExactSurfaceIdentity.stateIdentity(state),
                SurfaceFace.valueOf(quad.getDirection().name()),
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
