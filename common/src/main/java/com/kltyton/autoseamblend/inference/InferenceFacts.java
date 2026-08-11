package com.kltyton.autoseamblend.inference;

import java.util.Objects;
import java.util.Set;

/** 中文：选择自动方法前所需的完整引擎无关证据。 / English: Complete engine-neutral evidence required before an automatic method may be selected. */
public record InferenceFacts(
        FactState vanillaCuboidGeometry,
        FactState axisAlignedGeometry,
        FactState validUv,
        FactState spriteConsistent,
        FactState stateKnown,
        FactState alphaOpaque,
        FactState framedAlpha,
        FactState animated,
        FactState tintPresent,
        FactState fullBlock,
        FactState partialGeometry,
        FactState topOnly,
        FactState nativeOwnership,
        FactState allowedAxesKnown,
        Set<ConnectionAxis> allowedAxes) {
    public InferenceFacts {
        Objects.requireNonNull(vanillaCuboidGeometry, "vanillaCuboidGeometry");
        Objects.requireNonNull(axisAlignedGeometry, "axisAlignedGeometry");
        Objects.requireNonNull(validUv, "validUv");
        Objects.requireNonNull(spriteConsistent, "spriteConsistent");
        Objects.requireNonNull(stateKnown, "stateKnown");
        Objects.requireNonNull(alphaOpaque, "alphaOpaque");
        Objects.requireNonNull(framedAlpha, "framedAlpha");
        Objects.requireNonNull(animated, "animated");
        Objects.requireNonNull(tintPresent, "tintPresent");
        Objects.requireNonNull(fullBlock, "fullBlock");
        Objects.requireNonNull(partialGeometry, "partialGeometry");
        Objects.requireNonNull(topOnly, "topOnly");
        Objects.requireNonNull(nativeOwnership, "nativeOwnership");
        Objects.requireNonNull(allowedAxesKnown, "allowedAxesKnown");
        allowedAxes = Set.copyOf(Objects.requireNonNull(allowedAxes, "allowedAxes"));
        if (allowedAxesKnown != FactState.TRUE && !allowedAxes.isEmpty()) {
            throw new IllegalArgumentException("unknown allowed axes cannot contain asserted axes");
        }
    }

    public static InferenceFacts unknown() {
        return new InferenceFacts(
                FactState.UNKNOWN, FactState.UNKNOWN, FactState.UNKNOWN, FactState.UNKNOWN,
                FactState.UNKNOWN, FactState.UNKNOWN, FactState.UNKNOWN, FactState.UNKNOWN,
                FactState.UNKNOWN,
                FactState.UNKNOWN, FactState.UNKNOWN, FactState.UNKNOWN, FactState.UNKNOWN,
                FactState.UNKNOWN, Set.of());
    }

    public enum FactState {
        TRUE,
        FALSE,
        UNKNOWN;

        public static FactState of(boolean value) {
            return value ? TRUE : FALSE;
        }

        public boolean isTrue() {
            return this == TRUE;
        }

        public boolean isFalse() {
            return this == FALSE;
        }
    }
}
