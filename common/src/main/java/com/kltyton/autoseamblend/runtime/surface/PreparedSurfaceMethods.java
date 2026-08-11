package com.kltyton.autoseamblend.runtime.surface;

import com.kltyton.autoseamblend.engine.plan.MethodPlanResolution;
import com.kltyton.autoseamblend.engine.plan.PlanIdentity;
import com.kltyton.autoseamblend.engine.query.SurfaceFace;
import com.kltyton.autoseamblend.inference.InferenceDecision;
import com.kltyton.autoseamblend.inference.InferenceFacts;
import com.kltyton.autoseamblend.inference.OppositeSurfaceCompletion;
import com.kltyton.autoseamblend.inference.SurfaceMethodDecisionPolicy;
import com.kltyton.autoseamblend.inference.TransparentSelfConnectionInference;
import com.kltyton.autoseamblend.reload.surface.InitialSurfacePreparation;
import com.kltyton.autoseamblend.selection.method.ConfiguredMethodPlan;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** 中文：Atlas 规划和烘焙模型消费者共享的同次重载自动方法决策。 / English: Same-reload automatic method decisions shared by atlas planning and baked-model consumers. */
public final class PreparedSurfaceMethods {
    private PreparedSurfaceMethods() {}

    /** 中文：把预缝合输入解析为一个不可变方法候选，不立即发布。 / English: Resolves pre-stitch input into one immutable method candidate without immediate publication. */
    public static Snapshot prepare(
            InitialSurfacePreparation.Result prepared,
            long generation,
            String reloadToken) {
        Objects.requireNonNull(prepared, "prepared");
        if (generation <= 0) {
            throw new IllegalArgumentException(
                    "generation must be positive");
        }
        LinkedHashMap<Key, PreparedMethod> methods =
                new LinkedHashMap<>();
        IdentityHashMap<BlockState, Boolean> equalStateBoundaries =
                new IdentityHashMap<>();
        for (InitialSurfacePreparation.Surface surface
                : prepared.surfaces()) {
            InferenceDecision decision = SurfaceMethodDecisionPolicy.decide(
                    ConnectionMethod.AUTO,
                    surface.inferenceFacts(),
                    equalStateBoundaries.computeIfAbsent(
                            surface.state(),
                            TransparentSelfConnectionInference
                                    ::observesEqualStateBoundary));
            PreparedMethod method = new PreparedMethod(
                    surface.inferenceFacts(),
                    decision);
                    Key key = new Key(
                            surface.state(),
                            surface.direction(),
                            ResourceLocation.parse(surface.source().spriteId()));
            methods.merge(
                    key,
                    method,
                    (left, right) ->
                            left.equals(right)
                                    ? left
                                    : PreparedMethod.rejected(
                                            surface.inferenceFacts()));
        }
        completeMissingOppositeMethods(
                methods,
                equalStateBoundaries);
        Snapshot next = new Snapshot(
                generation,
                Objects.requireNonNull(
                        reloadToken,
                        "reloadToken"),
                methods,
                Snapshot.discoveriesByBlock(prepared));
        return next;
    }

    /**
     * 中文：原始模型旋转可能只暴露透明自剔除表面的一侧；仅用已确定的 CTM 决策补齐缺失的反向键。
     *
     * English:
     * Raw model rotations may expose only one side of a transparent self-culling surface; complete
     * only missing opposite keys from a certain CTM decision.
     */
    private static void completeMissingOppositeMethods(
            LinkedHashMap<Key, PreparedMethod> methods,
            IdentityHashMap<BlockState, Boolean>
                    equalStateBoundaries) {
        List.copyOf(methods.entrySet()).forEach(entry -> {
            Key key = entry.getKey();
            PreparedMethod method = entry.getValue();
            Optional<SurfaceFace> opposite =
                    OppositeSurfaceCompletion.oppositeFace(
                            SurfaceFace.valueOf(key.direction().name()),
                            method.decision().resolvedMethod().orElse(ConnectionMethod.NONE),
                            method.facts(),
                            equalStateBoundaries.computeIfAbsent(
                                    key.state(),
                                    TransparentSelfConnectionInference
                                            ::observesEqualStateBoundary));
            if (opposite.isEmpty()) {
                return;
            }
            methods.putIfAbsent(
                    new Key(
                            key.state(),
                            Direction.valueOf(opposite.orElseThrow().name()),
                            key.spriteId()),
                    method);
        });
    }

    public record Snapshot(
            long generation,
            String reloadToken,
            Map<Key, PreparedMethod> methods,
            Map<Block, Map<Direction, Map<ResourceLocation, PreparedMethod>>>
                    siblingMethods,
            Map<Block, PreparedAutoMethod>
                    autoMethods,
            Map<Block, PreparedDiscovery>
                    discoveries) {
        public Snapshot(
                long generation,
                String reloadToken,
                Map<Key, PreparedMethod> methods,
                Map<Block, PreparedAutoMethod> autoMethods,
                Map<Block, PreparedDiscovery> discoveries) {
            this(
                    generation,
                    reloadToken,
                    methods,
                    siblingMethodsByBlock(methods),
                    autoMethods,
                    discoveries);
        }

        public Snapshot(
                long generation,
                String reloadToken,
                Map<Key, PreparedMethod> methods,
                Map<Block, PreparedDiscovery> discoveries) {
            this(
                    generation,
                    reloadToken,
                    methods,
                    autoMethodsByBlock(methods),
                    discoveries);
        }

        public Snapshot(
                long generation,
                String reloadToken,
                Map<Key, PreparedMethod> methods) {
            this(
                    generation,
                    reloadToken,
                    methods,
                    autoMethodsByBlock(methods),
                    Map.of());
        }

        public Snapshot {
            if (generation < 0) {
                throw new IllegalArgumentException(
                        "generation must be non-negative");
            }
            if (reloadToken == null
                    || reloadToken.isBlank()) {
                throw new IllegalArgumentException(
                        "reloadToken must not be blank");
            }
            methods = Collections.unmodifiableMap(
                    new LinkedHashMap<>(
                            Objects.requireNonNull(
                                    methods,
                                    "methods")));
            siblingMethods = immutableSiblingMethods(
                    Objects.requireNonNull(
                            siblingMethods,
                            "siblingMethods"));
            java.util.IdentityHashMap<
                            Block,
                            PreparedAutoMethod>
                    representatives =
                            new java.util.IdentityHashMap<>();
            Objects.requireNonNull(
                            autoMethods,
                            "autoMethods")
                    .forEach(representatives::put);
            autoMethods = Collections.unmodifiableMap(
                    representatives);
            java.util.IdentityHashMap<
                            Block,
                            PreparedDiscovery>
                    stableDiscoveries =
                            new java.util.IdentityHashMap<>();
            Objects.requireNonNull(
                            discoveries,
                            "discoveries")
                    .forEach(stableDiscoveries::put);
            discoveries = Collections.unmodifiableMap(
                    stableDiscoveries);
        }

        public static Snapshot empty() {
            return empty("bootstrap");
        }

        public static Snapshot empty(
                String reloadToken) {
            return new Snapshot(
                    0,
                    Objects.requireNonNull(reloadToken, "reloadToken"),
                    Map.of());
        }

        public static Snapshot empty(
                long generation) {
            return empty(
                    generation,
                    "unpublished-" + generation);
        }

        public static Snapshot empty(
                long generation,
                String reloadToken) {
            return new Snapshot(
                    generation,
                    Objects.requireNonNull(reloadToken, "reloadToken"),
                    Map.of());
        }

        public Optional<ConnectionMethod> method(
                BlockState state,
                Direction direction,
                ResourceLocation spriteId) {
            return prepared(
                            state,
                            direction,
                            spriteId)
                    .flatMap(value ->
                            value.decision()
                                    .resolvedMethod());
        }

        public Optional<PreparedMethod> prepared(
                BlockState state,
                Direction direction,
                ResourceLocation spriteId) {
            PreparedMethod exact = methods.get(new Key(
                    state,
                    direction,
                    spriteId));
            if (exact != null) {
                return Optional.of(exact);
            }
            Map<Direction, Map<ResourceLocation, PreparedMethod>> byDirection =
                    siblingMethods.get(state.getBlock());
            if (byDirection == null) {
                return Optional.empty();
            }
            Map<ResourceLocation, PreparedMethod> bySprite = byDirection.get(direction);
            return bySprite == null
                    ? Optional.empty()
                    : Optional.ofNullable(bySprite.get(spriteId));
        }

        /**
         * 中文：1.21.1 multipart 目录可能没有运行时最终状态的精确键；在重载时为同 block、
         * 同方向、同 sprite 且 PreparedMethod 完全一致的状态预建回退。任何冲突都删除该键，
         * 热路径只做三次 O(1) 查表，不遍历全量 surface。
         *
         * <p>English: The 1.21.1 multipart catalog may miss the final runtime state's exact
         * key. During reload, pre-index states whose block, direction, sprite, and complete
         * PreparedMethod agree. Any conflict removes the fallback key, while the hot path
         * performs only three O(1) map lookups and never scans all surfaces.
         */
        private static Map<Block, Map<Direction, Map<ResourceLocation, PreparedMethod>>>
                siblingMethodsByBlock(Map<Key, PreparedMethod> methods) {
            java.util.IdentityHashMap<Block, LinkedHashMap<SiblingKey, PreparedMethod>>
                    candidates = new java.util.IdentityHashMap<>();
            java.util.IdentityHashMap<Block, java.util.Set<SiblingKey>> conflicts =
                    new java.util.IdentityHashMap<>();
            Objects.requireNonNull(methods, "methods").forEach((key, prepared) -> {
                Block block = key.state().getBlock();
                SiblingKey sibling = new SiblingKey(key.direction(), key.spriteId());
                java.util.Set<SiblingKey> blockConflicts = conflicts.computeIfAbsent(
                        block,
                        ignored -> new java.util.HashSet<>());
                if (blockConflicts.contains(sibling)) {
                    return;
                }
                LinkedHashMap<SiblingKey, PreparedMethod> blockCandidates =
                        candidates.computeIfAbsent(block, ignored -> new LinkedHashMap<>());
                PreparedMethod previous = blockCandidates.putIfAbsent(sibling, prepared);
                if (previous != null && !previous.equals(prepared)) {
                    blockCandidates.remove(sibling);
                    blockConflicts.add(sibling);
                }
            });

            java.util.IdentityHashMap<
                            Block,
                            Map<Direction, Map<ResourceLocation, PreparedMethod>>>
                    indexed = new java.util.IdentityHashMap<>();
            candidates.forEach((block, blockCandidates) -> {
                java.util.EnumMap<Direction, LinkedHashMap<ResourceLocation, PreparedMethod>>
                        byDirection = new java.util.EnumMap<>(Direction.class);
                blockCandidates.forEach((key, prepared) ->
                        byDirection
                                .computeIfAbsent(
                                        key.direction(),
                                        ignored -> new LinkedHashMap<>())
                                .put(key.spriteId(), prepared));
                java.util.EnumMap<Direction, Map<ResourceLocation, PreparedMethod>> stable =
                        new java.util.EnumMap<>(Direction.class);
                byDirection.forEach((direction, bySprite) ->
                        stable.put(direction, Collections.unmodifiableMap(bySprite)));
                indexed.put(block, Collections.unmodifiableMap(stable));
            });
            return Collections.unmodifiableMap(indexed);
        }

        private static Map<Block, Map<Direction, Map<ResourceLocation, PreparedMethod>>>
                immutableSiblingMethods(
                        Map<Block, Map<Direction, Map<ResourceLocation, PreparedMethod>>> source) {
            java.util.IdentityHashMap<
                            Block,
                            Map<Direction, Map<ResourceLocation, PreparedMethod>>>
                    stable = new java.util.IdentityHashMap<>();
            source.forEach((block, byDirection) -> {
                java.util.EnumMap<Direction, Map<ResourceLocation, PreparedMethod>> directions =
                        new java.util.EnumMap<>(Direction.class);
                byDirection.forEach((direction, bySprite) ->
                        directions.put(
                                direction,
                                Collections.unmodifiableMap(new LinkedHashMap<>(bySprite))));
                stable.put(block, Collections.unmodifiableMap(directions));
            });
            return Collections.unmodifiableMap(stable);
        }

        private record SiblingKey(
                Direction direction,
                ResourceLocation spriteId) {
            private SiblingKey {
                Objects.requireNonNull(direction, "direction");
                Objects.requireNonNull(spriteId, "spriteId");
            }
        }

        /**
         * 中文：返回资源重载时选定的 auto 代表方法，工作台打开时只做一次身份查表。
         *
         * English:
         * Returns the auto representative selected during resource reload so
         * opening the workbench performs one identity lookup.
         */
        public Optional<PreparedAutoMethod> autoMethod(
                Block block) {
            return Optional.ofNullable(
                    autoMethods.get(
                            Objects.requireNonNull(
                                    block,
                                    "block")));
        }

        /**
         * 中文：返回预缝合阶段保留的模型候选证据；候选存在不等于已经安全推断出方法。
         *
         * English:
         * Returns model-candidate evidence retained before stitching. Presence does not imply that
         * a connection method was safe to infer.
         */
        public Optional<PreparedDiscovery> discovery(
                Block block) {
            return Optional.ofNullable(
                    discoveries.get(
                            Objects.requireNonNull(
                                    block,
                                    "block")));
        }

        private static Map<Block, PreparedAutoMethod>
                autoMethodsByBlock(
                        Map<Key, PreparedMethod> methods) {
            java.util.IdentityHashMap<
                            Block,
                            PreparedAutoMethod>
                    representatives =
                            new java.util.IdentityHashMap<>();
            Objects.requireNonNull(methods, "methods")
                    .forEach((key, prepared) ->
                            prepared.decision()
                                    .resolvedMethod()
                                    .filter(method ->
                                            method
                                                    != ConnectionMethod
                                                            .NONE)
                                    .map(method ->
                                            new PreparedAutoMethod(
                                                    key.state(),
                                                    key.direction(),
                                                    key.spriteId(),
                                                    method))
                                    .ifPresent(candidate ->
                                            representatives.merge(
                                                    key.state()
                                                            .getBlock(),
                                                    candidate,
                                                    (left, right) ->
                                                            AUTO_METHOD_ORDER
                                                                            .compare(
                                                                                    left,
                                                                                    right)
                                                                    <= 0
                                                                    ? left
                                                                    : right)));
            return representatives;
        }

        private static Map<Block, PreparedDiscovery>
                discoveriesByBlock(
                        InitialSurfacePreparation.Result prepared) {
            java.util.IdentityHashMap<
                            Block,
                            PreparedDiscovery>
                    discoveries =
                            new java.util.IdentityHashMap<>();
            prepared.candidates().forEach(candidate ->
                    discoveries.merge(
                            candidate.state().getBlock(),
                            PreparedDiscovery.from(candidate),
                            PreparedDiscovery::merge));
            return discoveries;
        }

        private static final java.util.Comparator<
                        PreparedAutoMethod>
                AUTO_METHOD_ORDER =
                        java.util.Comparator
                                .comparingInt(
                                        (PreparedAutoMethod candidate) ->
                                                candidate.direction()
                                                                .getAxis()
                                                                .isHorizontal()
                                                        ? 0
                                                        : 1)
                                .thenComparing(candidate ->
                                        candidate.state()
                                                .toString())
                                .thenComparingInt(candidate ->
                                        candidate.direction()
                                                .ordinal())
                                .thenComparing(candidate ->
                                        candidate.spriteId()
                                                .toString());
    }

    /**
     * 中文：按方块聚合的预缝合发现证据，供路由与后续烘焙阶段读取，不回写配置规则。
     *
     * English:
     * Per-block pre-stitch discovery evidence for routing and later bake stages; it never writes
     * configuration rules.
     */
    public record PreparedDiscovery(
            List<BlockState> states,
            InitialSurfacePreparation.CandidateStatus status,
            List<String> evidence) {
        public PreparedDiscovery {
            states = List.copyOf(Objects.requireNonNull(states, "states"));
            Objects.requireNonNull(status, "status");
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
            if (states.isEmpty() || evidence.isEmpty()) {
                throw new IllegalArgumentException(
                        "prepared discovery requires states and evidence");
            }
        }

        private static PreparedDiscovery from(
                InitialSurfacePreparation.StateCandidate candidate) {
            return new PreparedDiscovery(
                    List.of(candidate.state()),
                    candidate.status(),
                    candidate.evidence());
        }

        private PreparedDiscovery merge(
                PreparedDiscovery other) {
            ArrayList<BlockState> mergedStates = new ArrayList<>(states);
            other.states().stream()
                    .filter(state -> !mergedStates.contains(state))
                    .forEach(mergedStates::add);
            ArrayList<String> mergedEvidence = new ArrayList<>(evidence);
            other.evidence().stream()
                    .filter(value -> !mergedEvidence.contains(value))
                    .forEach(mergedEvidence::add);
            InitialSurfacePreparation.CandidateStatus mergedStatus =
                    status.ordinal() <= other.status().ordinal()
                            ? status
                            : other.status();
            return new PreparedDiscovery(
                    mergedStates,
                    mergedStatus,
                    mergedEvidence);
        }
    }

    /**
     * 中文：资源重载期间预选的每方块 auto 方法和表面身份。
     *
     * English:
     * Per-block auto method and surface identity preselected during resource reload.
     */
    public record PreparedAutoMethod(
            BlockState state,
            Direction direction,
            ResourceLocation spriteId,
            ConnectionMethod method) {
        public PreparedAutoMethod {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(spriteId, "spriteId");
            Objects.requireNonNull(method, "method");
        }
    }

    /** 中文：重载期间只求值一次的表面事实与方法决策。 / English: Surface facts and method decision evaluated exactly once during reload. */
    public record PreparedMethod(
            InferenceFacts facts,
            InferenceDecision decision) {
        public PreparedMethod {
            Objects.requireNonNull(facts, "facts");
            Objects.requireNonNull(decision, "decision");
        }

        public MethodPlanResolution resolution(
                long generation,
                String reloadToken,
                String engineId,
                String identity) {
            PlanIdentity planIdentity = new PlanIdentity(
                    generation,
                    reloadToken,
                    engineId,
                    identity);
            return new MethodPlanResolution(
                    facts,
                    decision,
                    ConfiguredMethodPlan.fromDecision(
                            planIdentity,
                            decision));
        }

        private static PreparedMethod rejected(
                InferenceFacts facts) {
            return new PreparedMethod(
                    facts,
                    new InferenceDecision(
                            ConnectionMethod.AUTO,
                            Optional.of(ConnectionMethod.NONE),
                            false,
                            InferenceDecision.Confidence.CERTAIN,
                            List.of(
                                    "conflicting_surface_methods_passthrough"),
                            List.of()));
        }
    }

    public record Key(
            BlockState state,
            Direction direction,
            ResourceLocation spriteId) {
        public Key {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(spriteId, "spriteId");
        }
    }
}
