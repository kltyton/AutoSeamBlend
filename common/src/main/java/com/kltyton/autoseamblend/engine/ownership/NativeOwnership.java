package com.kltyton.autoseamblend.engine.ownership;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 中文：一个 ConnectionQuery 的精确原生所有权结果。 / English: Exact native ownership result for one ConnectionQuery. */
public record NativeOwnership(
        Match match,
        Optional<NativeRuleSource> source,
        Optional<ConnectionMethod> requestedMethod,
        Optional<ConnectionMethod> resolvedMethod,
        List<NativeSlot> slots,
        String reason) {
    public NativeOwnership {
        Objects.requireNonNull(match, "match");
        source = Objects.requireNonNull(source, "source");
        requestedMethod = Objects.requireNonNull(requestedMethod, "requestedMethod");
        resolvedMethod = Objects.requireNonNull(resolvedMethod, "resolvedMethod");
        slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason must not be blank");
        if (match == Match.NO_MATCH
                && (source.isPresent() || requestedMethod.isPresent()
                        || resolvedMethod.isPresent() || !slots.isEmpty())) {
            throw new IllegalArgumentException("no-match ownership cannot claim a source, method or slots");
        }
        if (match == Match.MATCH && source.isEmpty()) {
            throw new IllegalArgumentException("an exact native ownership match requires document provenance");
        }
        if (match == Match.CONSERVATIVE_UNKNOWN
                && (requestedMethod.isPresent() || resolvedMethod.isPresent() || !slots.isEmpty())) {
            throw new IllegalArgumentException("unknown native effects cannot assert method or slot facts");
        }
        if (resolvedMethod.filter(method -> method == ConnectionMethod.AUTO).isPresent()) {
            throw new IllegalArgumentException("native ownership resolved method must be concrete");
        }
    }

    public static NativeOwnership noMatch(String reason) {
        return new NativeOwnership(
                Match.NO_MATCH,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                reason);
    }

    public boolean ownsQuery() {
        return match != Match.NO_MATCH;
    }

    public enum Match {
        NO_MATCH,
        MATCH,
        CONSERVATIVE_UNKNOWN
    }
}
