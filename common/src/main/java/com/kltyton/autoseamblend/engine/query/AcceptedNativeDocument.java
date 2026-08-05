package com.kltyton.autoseamblend.engine.query;

import com.kltyton.autoseamblend.engine.ownership.AutoBlendPolicy;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：精确查询实际命中的一个原生文档，以及引擎能够直接证明的可选槽位证据。
 *
 * English:
 * One native document actually matched by an exact query, plus optional slot evidence proven
 * directly by the engine.
 */
public record AcceptedNativeDocument(
        NativeDocumentIdentity identity,
        Optional<AcceptedEvidence> evidence) {
    public AcceptedNativeDocument {
        Objects.requireNonNull(identity, "identity");
        evidence = Objects.requireNonNull(evidence, "evidence");
    }

    public static AcceptedNativeDocument identityOnly(NativeDocumentIdentity identity) {
        return new AcceptedNativeDocument(identity, Optional.empty());
    }

    /**
     * 中文：已接受 holder 直接携带的来源、策略、方法与槽位事实。
     *
     * English: Provenance, policy, method, and slot facts carried directly by an accepted holder.
     */
    public record AcceptedEvidence(
            SourceTier sourceTier,
            Optional<AutoBlendPolicy> strategyPolicy,
            ConnectionMethod requestedMethod,
            ConnectionMethod resolvedMethod,
            List<NativeSlot> slots,
            int packPriority,
            int documentOrder) {
        public AcceptedEvidence {
            Objects.requireNonNull(sourceTier, "sourceTier");
            strategyPolicy = Objects.requireNonNull(strategyPolicy, "strategyPolicy");
            Objects.requireNonNull(requestedMethod, "requestedMethod");
            Objects.requireNonNull(resolvedMethod, "resolvedMethod");
            slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
            if (resolvedMethod == ConnectionMethod.AUTO
                    || packPriority < 0
                    || documentOrder < 0) {
                throw new IllegalArgumentException(
                        "accepted evidence requires a concrete method and non-negative precedence");
            }
        }
    }
}
