package com.kltyton.autoseamblend.engine.ownership.fusion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：仅保存可静态证明的 Fusion donor 选择；位置相关或复合 predicate 明确标记 unavailable。
 * <p>
 * English: Retains only statically provable Fusion donor selection; positional or composite
 * predicates are explicitly marked unavailable.
 */
public record FusionDonorSelection(
        boolean explicitNativePredicate,
        Map<Query, BlockState> firstDonorByQuery) {
    public FusionDonorSelection {
        firstDonorByQuery = Collections.unmodifiableMap(new LinkedHashMap<>(
                Objects.requireNonNull(firstDonorByQuery, "firstDonorByQuery")));
    }

    public static FusionDonorSelection selfConnect() {
        return new FusionDonorSelection(false, Map.of());
    }

    public static FusionDonorSelection unavailable() {
        return new FusionDonorSelection(true, Map.of());
    }

    public Optional<BlockState> donor(
            BlockState receiver,
            Direction observedFace,
            Relation relation) {
        return explicitNativePredicate
                ? Optional.ofNullable(firstDonorByQuery.get(
                        new Query(receiver, observedFace, relation)))
                : Optional.of(Objects.requireNonNull(receiver, "receiver"));
    }

    public enum Relation {
        TOP,
        TOP_RIGHT,
        RIGHT,
        BOTTOM_RIGHT,
        BOTTOM,
        BOTTOM_LEFT,
        LEFT,
        TOP_LEFT
    }

    public record Query(
            BlockState receiver,
            Direction observedFace,
            Relation relation) {
        public Query {
            Objects.requireNonNull(receiver, "receiver");
            Objects.requireNonNull(observedFace, "observedFace");
            Objects.requireNonNull(relation, "relation");
        }
    }
}
