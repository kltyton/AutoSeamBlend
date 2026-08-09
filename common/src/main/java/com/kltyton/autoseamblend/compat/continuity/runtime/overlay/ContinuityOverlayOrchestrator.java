package com.kltyton.autoseamblend.compat.continuity.runtime.overlay;

import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityMethodPolicy;
import com.kltyton.autoseamblend.runtime.overlay.OverlayCandidateArbitration;
import com.kltyton.autoseamblend.runtime.overlay.OverlayCandidatePriority;
import com.kltyton.autoseamblend.runtime.overlay.PlanarOverlayNeighborhood;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：集中 Continuity overlay 的邻域枚举、连接过滤、方法判定、优先级仲裁和绘制顺序；
 * Loader 只提供快照候选及其原生事实。
 *
 * English: Centralizes Continuity overlay neighborhood enumeration, connection filtering, method
 * checks, priority arbitration, and painter ordering; loaders provide only snapshot candidates
 * and native facts.
 */
public final class ContinuityOverlayOrchestrator {
    private static final Comparator<Candidate<?>> CANDIDATE_ORDER =
            OverlayCandidateArbitration.orderBy(Candidate::priority);

    private ContinuityOverlayOrchestrator() {}

    /**
     * 中文：按 Continuity 原生方向顺序选择可覆盖当前接收面的供体；English: Selects donors
     * that may cover the receiver in Continuity's native direction order.
     *
     * @param <S> Loader-specific immutable surface snapshot type
     */
    public static <S> List<Candidate<S>> selectDonors(
            BlockAndTintGetter level,
            BlockPos pos,
            Direction face,
            BlockState receiver,
            ConnectionRuleSet<Block> rules,
            List<Direction> directions,
            Function<BlockState, Optional<Candidate<S>>> candidateResolver,
            Optional<OverlayCandidatePriority> receiverPriority) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(rules, "rules");
        List<Direction> orderedDirections = List.copyOf(
                Objects.requireNonNull(directions, "directions"));
        Function<BlockState, Optional<Candidate<S>>> resolver =
                Objects.requireNonNull(candidateResolver, "candidateResolver");
        Optional<OverlayCandidatePriority> explicitReceiverPriority =
                Objects.requireNonNull(receiverPriority, "receiverPriority");
        if (orderedDirections.size() != 4
                || orderedDirections.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Continuity overlay requires four ordered directions");
        }

        Optional<OverlayCandidatePriority> effectiveReceiverPriority = explicitReceiverPriority
                .or(() -> resolver.apply(receiver)
                        .filter(candidate ->
                                ContinuityMethodPolicy.overlay(candidate.method()))
                        .map(Candidate::priority));

        ArrayList<BlockState> states = new ArrayList<>(8);
        for (PlanarOverlayNeighborhood.NeighborOffset offset
                : PlanarOverlayNeighborhood.neighbors(orderedDirections)) {
            addUnique(states, level.getBlockState(offset.positionFrom(pos)));
        }
        ArrayList<Candidate<S>> donors = new ArrayList<>();
        for (BlockState candidateState : states) {
            if (!ContinuityMethodPolicy.receivesOverlay(
                    rules,
                    candidateState.getBlock(),
                    receiver.getBlock())) {
                continue;
            }
            Optional<Candidate<S>> candidate = resolver.apply(candidateState)
                    .filter(value -> ContinuityMethodPolicy.overlay(value.method()))
                    .filter(value -> effectiveReceiverPriority
                            .map(value.priority()::winsOver)
                            .orElse(true));
            candidate.ifPresent(donors::add);
        }
        OverlayCandidateArbitration.sortInPlace(
                donors,
                (left, right) -> CANDIDATE_ORDER.compare(left, right));
        return List.copyOf(donors);
    }

    private static void addUnique(List<BlockState> states, BlockState candidate) {
        if (!states.contains(candidate)) {
            states.add(candidate);
        }
    }

    /** 中文：一个不可变、已完成优先级计算的供体候选。 / English: Immutable donor candidate with precomputed priority. */
    public record Candidate<S>(
            BlockState state,
            S surface,
            ConnectionMethod method,
            OverlayCandidatePriority priority) {
        public Candidate {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(surface, "surface");
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(priority, "priority");
        }
    }
}
