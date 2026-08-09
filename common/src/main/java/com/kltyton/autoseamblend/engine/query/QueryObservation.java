package com.kltyton.autoseamblend.engine.query;

import com.kltyton.autoseamblend.engine.ownership.NativeOwnership;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 中文：有序的精确原生槽位声明，以及至多一个查询局部的保守阻断项。 / English: Ordered exact native slot claims plus at most one query-local conservative blocker. */
public record QueryObservation(
        List<NativeOwnership> slotClaims,
        Optional<NativeOwnership> conservativeBlocker) {
    public QueryObservation {
        slotClaims = List.copyOf(Objects.requireNonNull(slotClaims, "slotClaims"));
        conservativeBlocker = Objects.requireNonNull(conservativeBlocker, "conservativeBlocker");
        if (slotClaims.stream().anyMatch(claim ->
                claim.match() != NativeOwnership.Match.MATCH
                        || claim.slots().isEmpty()
                                && claim.requestedMethod().isEmpty()
                                && claim.resolvedMethod().isEmpty())) {
            throw new IllegalArgumentException(
                    "ownership claims must be exact matches and an empty slot set requires a method claim");
        }
        conservativeBlocker.ifPresent(blocker -> {
            if (blocker.match() != NativeOwnership.Match.CONSERVATIVE_UNKNOWN) {
                throw new IllegalArgumentException("conservative blocker must use CONSERVATIVE_UNKNOWN");
            }
        });
    }

    public static QueryObservation empty() {
        return new QueryObservation(List.of(), Optional.empty());
    }

    public List<NativeOwnership> orderedOwnership() {
        if (conservativeBlocker.isEmpty()) return slotClaims;
        java.util.ArrayList<NativeOwnership> ownership = new java.util.ArrayList<>(slotClaims);
        ownership.add(conservativeBlocker.orElseThrow());
        return List.copyOf(ownership);
    }
}
