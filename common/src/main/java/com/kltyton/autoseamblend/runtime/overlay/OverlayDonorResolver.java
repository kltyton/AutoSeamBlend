package com.kltyton.autoseamblend.runtime.overlay;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import com.kltyton.autoseamblend.engine.routing.query.EngineRouteSelection;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：共享 overlay 供体发现、来源仲裁和快照缓存；Loader 只提供引擎路由查询。
 *
 * English: Shared overlay-donor discovery, provenance arbitration, and snapshot caching; loaders
 * provide only engine-route lookup.
 */
public final class OverlayDonorResolver {
    private static final int DIRECTION_COUNT = Direction.values().length;
    private static final int ENGINE_FAMILY_COUNT = EngineFamily.values().length;
    private static final Comparator<Candidate> CANDIDATE_ORDER =
            OverlayCandidateArbitration.orderBy(Candidate::priority);

    private final RouteLookup routeLookup;
    private final AtomicReference<CandidateCache> candidateCache =
            new AtomicReference<>();

    public OverlayDonorResolver(RouteLookup routeLookup) {
        this.routeLookup = Objects.requireNonNull(routeLookup, "routeLookup");
    }

    /**
     * 中文：返回与目标面正交的四个稳定方向。
     * English: Returns the four stable directions orthogonal to a target face.
     */
    public static List<Direction> planarDirections(Direction face) {
        return PlanarOverlayNeighborhood.planarDirections(face);
    }

    /**
     * 中文：按原生绘制顺序返回可覆盖接收体的供体。
     * English: Returns donors that can cover the receiver in native painter order.
     */
    public List<Donor> resolveAll(
            BlockAndTintGetter level,
            BlockPos pos,
            Direction face,
            BlockState receiver,
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces,
            EngineFamily family,
            List<Direction> candidateDirections) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(surfaces, "surfaces");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(candidateDirections, "candidateDirections");

        Optional<Candidate> receiverCandidate = candidate(
                receiver,
                face,
                rules,
                surfaces,
                family)
                .filter(value -> value.method().overlayCapable());
        ArrayList<BlockState> neighborStates = new ArrayList<>(8);
        for (PlanarOverlayNeighborhood.NeighborOffset offset
                : PlanarOverlayNeighborhood.neighbors(candidateDirections)) {
            addUnique(
                    neighborStates,
                    level.getBlockState(offset.positionFrom(pos)));
        }
        ArrayList<Candidate> candidates = null;
        for (BlockState neighbor : neighborStates) {
            if (neighbor.getBlock() == receiver.getBlock()
                    || rules.connects(receiver.getBlock(), neighbor.getBlock())) {
                continue;
            }
            Optional<Candidate> resolved = candidate(
                    neighbor,
                    face,
                    rules,
                    surfaces,
                    family);
            if (resolved.isEmpty()) {
                continue;
            }
            Candidate value = resolved.orElseThrow();
            if (!value.method().overlayCapable()
                    || !OverlayCandidateArbitration.winsOver(
                            value,
                            receiverCandidate,
                            CANDIDATE_ORDER)) {
                continue;
            }
            if (candidates == null) {
                candidates = new ArrayList<>();
            }
            candidates.add(value);
        }
        if (candidates == null) {
            return List.of();
        }
        OverlayCandidateArbitration.sortInPlace(candidates, CANDIDATE_ORDER);
        ArrayList<Donor> donors = new ArrayList<>(candidates.size());
        for (Candidate value : candidates) {
            donors.add(new Donor(
                    value.state(),
                    value.surface(),
                    value.method()));
        }
        return List.copyOf(donors);
    }

    public ConnectionMethod resolveMethod(
            EngineFamily family,
            BlockState state,
            FaceSurface surface,
            ConnectionRuleSet<Block> rules) {
        return resolution(
                Objects.requireNonNull(family, "family"),
                Objects.requireNonNull(state, "state"),
                Objects.requireNonNull(surface, "surface"),
                Objects.requireNonNull(rules, "rules"))
                .map(Resolution::method)
                .orElse(ConnectionMethod.NONE);
    }

    /**
     * 中文：通过逻辑面的首选表面解析 AUTO；同一逻辑面所有渲染层共享该结果。
     * English: Resolves AUTO through the preferred logical-face surface so every rendered layer
     * shares one inference result.
     */
    public ConnectionMethod resolveMethod(
            EngineFamily family,
            BlockState state,
            FaceSurface surface,
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        Objects.requireNonNull(surfaces, "surfaces");
        FaceSurface inferenceSurface = surfaces
                .preferredFace(state, surface.direction())
                .orElse(surface);
        return resolveMethod(
                family,
                state,
                inferenceSurface,
                rules);
    }

    public boolean receivesOverlayFrom(
            EngineFamily family,
            Donor donor,
            BlockState receiver,
            Direction face,
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(donor, "donor");
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(surfaces, "surfaces");

        Block donorBlock = donor.state().getBlock();
        Block receiverBlock = receiver.getBlock();
        if (receiverBlock == donorBlock
                || rules.connects(receiverBlock, donorBlock)) {
            return false;
        }
        Optional<Candidate> receiverCandidate = candidate(
                receiver,
                face,
                rules,
                surfaces,
                family)
                .filter(value -> value.method().overlayCapable());
        return candidate(
                donor.state(),
                face,
                rules,
                surfaces,
                family)
                .filter(value -> value.method().overlayCapable())
                .filter(value -> OverlayCandidateArbitration.winsOver(
                        value,
                        receiverCandidate,
                        CANDIDATE_ORDER))
                .isPresent();
    }

    private Optional<Candidate> candidate(
            BlockState state,
            Direction face,
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces,
            EngineFamily family) {
        return candidateCache(rules, surfaces)
                .resolve(
                        state,
                        family.ordinal(),
                        face.ordinal(),
                        value -> computeCandidate(
                                value,
                                face,
                                rules,
                                surfaces,
                                family));
    }

    private Optional<Candidate> computeCandidate(
            BlockState state,
            Direction face,
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces,
            EngineFamily family) {
        return surfaces.preferredFace(state, face)
                .flatMap(surface ->
                        resolution(
                                family,
                                state,
                                surface,
                                rules)
                                .map(resolution ->
                                        new Candidate(
                                                state,
                                                surface,
                                                resolution.method(),
                                                resolution.sourceTier(),
                                                resolution.packPriority(),
                                                resolution.order())));
    }

    private Optional<Resolution> resolution(
            EngineFamily family,
            BlockState state,
            FaceSurface surface,
            ConnectionRuleSet<Block> rules) {
        Optional<EngineRouteSelection> routed = routeLookup
                .route(family, state)
                .filter(selection -> selection.engine().family() == family);
        if (routed.isEmpty()) {
            return Optional.empty();
        }
        EngineRouteSelection selection = routed.orElseThrow();
        ConnectionMethod resolved = resolveAuto(
                selection.method(),
                surface);
        var provenance = selection.provenance();
        if (provenance.source().configOrFallback()
                && !rules.excludedModes(
                        state.getBlock(),
                        resolved)
                        .isEmpty()) {
            resolved = ConnectionMethod.NONE;
        }
        return Optional.of(new Resolution(
                resolved,
                provenance.sourceTier(),
                provenance.packPriority(),
                provenance.order()));
    }

    private static ConnectionMethod resolveAuto(
            ConnectionMethod requested,
            FaceSurface surface) {
        return requested == ConnectionMethod.AUTO
                ? surface.inferredMethod()
                : requested;
    }

    private CandidateCache candidateCache(
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        CandidateCache current = candidateCache.get();
        if (current != null && current.matches(rules, surfaces)) {
            return current;
        }
        synchronized (candidateCache) {
            current = candidateCache.get();
            if (current == null || !current.matches(rules, surfaces)) {
                current = new CandidateCache(rules, surfaces);
                candidateCache.set(current);
            }
            return current;
        }
    }

    private static void addUnique(
            List<BlockState> states,
            BlockState candidate) {
        if (!states.contains(candidate)) {
            states.add(candidate);
        }
    }

    public record Donor(
            BlockState state,
            FaceSurface surface,
            ConnectionMethod method) {
        public Donor {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(surface, "surface");
            Objects.requireNonNull(method, "method");
            if (!method.overlayCapable()) {
                throw new IllegalArgumentException(
                        "donor method must be overlay-capable");
            }
        }
    }

    @FunctionalInterface
    public interface RouteLookup {
        Optional<EngineRouteSelection> route(
                EngineFamily family,
                BlockState state);
    }

    private record Candidate(
            BlockState state,
            FaceSurface surface,
            ConnectionMethod method,
            SourceTier sourceTier,
            int packPriority,
            int order) {
        private OverlayCandidatePriority priority() {
            return new OverlayCandidatePriority(
                    sourceTier,
                    packPriority,
                    order,
                    surface.overlayProfile().dominance(),
                    surface.overlayProfile().visualSignature());
        }
    }

    private record Resolution(
            ConnectionMethod method,
            SourceTier sourceTier,
            int packPriority,
            int order) {}

    /**
     * 中文：一个规则/表面快照拥有一组并发候选表；快照变化时整组原子轮换。
     * English: One rule/surface snapshot owns a concurrent candidate table; the whole table rotates
     * atomically when either snapshot changes.
     */
    private static final class CandidateCache {
        private final ConnectionRuleSet<Block> rules;
        private final MinecraftSurfaceCatalog.Snapshot surfaces;
        private final OverlayCandidateSlotCache<BlockState, Candidate> slots;

        private CandidateCache(
                ConnectionRuleSet<Block> rules,
                MinecraftSurfaceCatalog.Snapshot surfaces) {
            this.rules = Objects.requireNonNull(rules, "rules");
            this.surfaces = Objects.requireNonNull(surfaces, "surfaces");
            slots = new OverlayCandidateSlotCache<>(
                    ENGINE_FAMILY_COUNT,
                    DIRECTION_COUNT);
        }

        private boolean matches(
                ConnectionRuleSet<Block> candidateRules,
                MinecraftSurfaceCatalog.Snapshot candidateSurfaces) {
            return rules == candidateRules
                    && surfaces == candidateSurfaces;
        }

        private Optional<Candidate> resolve(
                BlockState state,
                int familyOrdinal,
                int directionOrdinal,
                java.util.function.Function<
                                ? super BlockState,
                                Optional<Candidate>> resolver) {
            return slots.resolve(
                    state,
                    familyOrdinal,
                    directionOrdinal,
                    resolver);
        }
    }
}
