package com.kltyton.autoseamblend.engine.routing.query;

import com.kltyton.autoseamblend.authoring.storage.ManagedPackIdentity;
import com.kltyton.autoseamblend.engine.EngineDescriptor;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.ownership.AutoBlendPolicy;
import com.kltyton.autoseamblend.engine.ownership.NativeOwnership;
import com.kltyton.autoseamblend.engine.ownership.NativeRuleSource;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import com.kltyton.autoseamblend.engine.ownership.NativeSlotIntent;
import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import com.kltyton.autoseamblend.engine.query.AcceptedNativeDocument;
import com.kltyton.autoseamblend.engine.query.NativeDocumentIdentity;
import com.kltyton.autoseamblend.engine.query.NativeQueryObservation;
import com.kltyton.autoseamblend.engine.query.QueryObservation;
import com.kltyton.autoseamblend.reload.rule.ManagedRule;
import com.kltyton.autoseamblend.reload.rule.NativeRule;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.selection.method.MethodSlotDomain;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：将引擎已接受文档映射为精确所有权，包括唯一身份匹配、保守未知与 Athena 物理槽位归一化。
 * English: Maps engine-accepted documents to exact ownership, including unique identity matching,
 * conservative unknowns, and Athena physical-slot normalization.
 */
public final class AcceptedDocumentOwnershipMapper {
    private AcceptedDocumentOwnershipMapper() {}

    public static QueryObservation map(Input input) {
        Objects.requireNonNull(input, "input");
        ArrayList<NativeOwnership> claims = new ArrayList<>();
        ArrayList<String> unknown = new ArrayList<>();
        input.observation().unknownDiagnostic().ifPresent(unknown::add);
        for (AcceptedNativeDocument document : input.observation().acceptedDocuments()) {
            document.evidence().ifPresentOrElse(
                    evidence -> claims.add(acceptedClaim(
                            input.engine().engineId(),
                            document.identity(),
                            evidence,
                            input.inferredMethod())),
                    () -> mapAcceptedIdentity(input, document.identity(), claims, unknown));
        }
        Optional<NativeOwnership> blocker = unknown.isEmpty()
                ? Optional.empty()
                : Optional.of(conservative(
                        input.engine(), input.blockId(), String.join("+", unknown)));
        return new QueryObservation(claims, blocker);
    }

    private static void mapAcceptedIdentity(
            Input input,
            NativeDocumentIdentity identity,
            List<NativeOwnership> claims,
            List<String> unknown) {
        List<NativeRule> nativeMatches = input.nativeRules().stream()
                .filter(rule -> rule.family() == input.engine().family())
                .filter(rule -> rule.targetBlockId().equals(input.blockId()))
                .filter(rule -> matches(identity, rule.packId(), rule.resourceId()))
                .toList();
        List<ManagedRule> managedMatches = input.managedRules().stream()
                .filter(rule -> rule.family() == input.engine().family())
                .filter(rule -> rule.targetBlockId().equals(input.blockId()))
                .filter(rule -> identity.resourceId().equals(rule.resourceId()))
                .filter(rule -> rule.effectivePackId()
                        .filter(ManagedPackIdentity::matchesId)
                        .isPresent())
                .filter(rule -> identity.packId()
                        .map(value -> value.equals(rule.effectivePackId().orElseThrow()))
                        .orElse(true))
                .toList();
        int matchCount = nativeMatches.size() + managedMatches.size();
        if (matchCount == 1 && !nativeMatches.isEmpty()) {
            NativeRule rule = nativeMatches.getFirst();
            ConnectionMethod concrete = resolved(rule.requestedMethod(), input.inferredMethod());
            claims.add(nativeClaim(
                    input.engine().engineId(),
                    rule,
                    concrete,
                    nativeSlots(input.engine().family(), rule.slots(), concrete)));
            return;
        }
        if (matchCount == 1) {
            ManagedRule rule = managedMatches.getFirst();
            ConnectionMethod concrete = resolved(rule.requestedMethod(), input.inferredMethod());
            claims.add(managedClaim(
                    input.engine().engineId(),
                    rule,
                    concrete,
                    input.managedPackPriority()));
            return;
        }
        unknown.add((matchCount == 0
                        ? "ACCEPTED_DOCUMENT_EXTENSION_UNAVAILABLE:"
                        : "ACCEPTED_DOCUMENT_PROVENANCE_AMBIGUOUS:")
                + identity.resourceId());
    }

    private static boolean matches(
            NativeDocumentIdentity identity,
            String packId,
            String resourceId) {
        return identity.resourceId().equals(resourceId)
                && identity.packId().map(packId::equals).orElse(true);
    }

    private static NativeOwnership acceptedClaim(
            String engineId,
            NativeDocumentIdentity identity,
            AcceptedNativeDocument.AcceptedEvidence evidence,
            ConnectionMethod inferredMethod) {
        NativeRuleSource source = new NativeRuleSource(
                engineId,
                evidence.sourceTier(),
                evidence.strategyPolicy(),
                identity.packId().orElseThrow(() -> new IllegalArgumentException(
                        "direct accepted evidence requires pack identity")),
                identity.resourceId(),
                evidence.packPriority(),
                evidence.documentOrder());
        return new NativeOwnership(
                NativeOwnership.Match.MATCH,
                Optional.of(source),
                Optional.of(evidence.requestedMethod()),
                Optional.of(evidence.requestedMethod() == ConnectionMethod.AUTO
                                && evidence.resolvedMethod() == ConnectionMethod.NONE
                        ? inferredMethod
                        : evidence.resolvedMethod()),
                evidence.slots(),
                "accepted_native_holder_identity");
    }

    private static List<NativeSlot> nativeSlots(
            EngineFamily family,
            List<NativeSlot> evidence,
            ConnectionMethod method) {
        if (family == EngineFamily.ATHENA) {
            return normalizedAthenaSlots(evidence, method);
        }
        LinkedHashMap<Integer, NativeSlot> byIndex = new LinkedHashMap<>();
        for (NativeSlot slot : evidence) {
            byIndex.putIfAbsent(slot.index(), slot);
        }
        return MethodSlotDomain.of(method).slots().stream()
                .map(index -> byIndex.getOrDefault(
                        index,
                        new NativeSlot(index, NativeSlotIntent.OMITTED, Optional.empty())))
                .toList();
    }

    private static List<NativeSlot> normalizedAthenaSlots(
            List<NativeSlot> physicalEvidence,
            ConnectionMethod method) {
        List<Integer> domain = MethodSlotDomain.of(method).slots();
        boolean unknown = physicalEvidence.stream().anyMatch(slot ->
                slot.intent() == NativeSlotIntent.UNKNOWN
                        || slot.intent() == NativeSlotIntent.DEFAULT
                        || slot.intent() == NativeSlotIntent.SKIP);
        if (unknown) {
            return domain.stream()
                    .map(index -> new NativeSlot(index, NativeSlotIntent.UNKNOWN, Optional.empty()))
                    .toList();
        }
        boolean missing = physicalEvidence.isEmpty()
                || physicalEvidence.stream().anyMatch(slot -> slot.intent().fillable());
        if (missing) {
            return domain.stream()
                    .map(index -> new NativeSlot(index, NativeSlotIntent.OMITTED, Optional.empty()))
                    .toList();
        }
        Optional<String> representative = physicalEvidence.stream()
                .flatMap(slot -> slot.spriteId().stream())
                .findFirst();
        if (representative.isEmpty()) {
            return domain.stream()
                    .map(index -> new NativeSlot(index, NativeSlotIntent.UNKNOWN, Optional.empty()))
                    .toList();
        }
        return domain.stream()
                .map(index -> new NativeSlot(index, NativeSlotIntent.PRESENT, representative))
                .toList();
    }

    private static NativeOwnership nativeClaim(
            String engineId,
            NativeRule rule,
            ConnectionMethod concrete,
            List<NativeSlot> slots) {
        NativeRuleSource source = new NativeRuleSource(
                engineId,
                SourceTier.NATIVE_AUTHOR,
                Optional.of(AutoBlendPolicy.fromCompatibility(rule.compatibility())),
                rule.packId(),
                rule.resourceId(),
                rule.packPriority(),
                rule.order());
        return new NativeOwnership(
                NativeOwnership.Match.MATCH,
                Optional.of(source),
                Optional.of(rule.requestedMethod()),
                Optional.of(concrete),
                slots,
                "accepted_native_author_extension");
    }

    private static NativeOwnership managedClaim(
            String engineId,
            ManagedRule rule,
            ConnectionMethod concrete,
            int packPriority) {
        SourceTier tier = rule.compatibility()
                ? SourceTier.MANAGED_COMPATIBILITY
                : SourceTier.MANAGED_NON_COMPATIBILITY;
        NativeRuleSource source = new NativeRuleSource(
                engineId,
                tier,
                Optional.of(AutoBlendPolicy.fromCompatibility(rule.compatibility())),
                rule.effectivePackId().orElseThrow(() -> new IllegalStateException(
                        "an accepted Managed claim requires the effective pack identity")),
                rule.resourceId(),
                packPriority,
                rule.order());
        return new NativeOwnership(
                NativeOwnership.Match.MATCH,
                Optional.of(source),
                Optional.of(rule.requestedMethod()),
                Optional.of(concrete),
                nativeSlots(rule.family(), rule.slots(), concrete),
                "accepted_managed_extension");
    }

    private static NativeOwnership conservative(
            EngineDescriptor engine,
            String blockId,
            String diagnostic) {
        return new NativeOwnership(
                NativeOwnership.Match.CONSERVATIVE_UNKNOWN,
                Optional.of(new NativeRuleSource(
                        engine.engineId(),
                        SourceTier.NATIVE_AUTHOR,
                        Optional.empty(),
                        "native-engine:" + engine.engineId(),
                        "accepted-model:" + blockId,
                        0,
                        engine.family().stableOrder())),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                diagnostic);
    }

    private static ConnectionMethod resolved(
            ConnectionMethod requested,
            ConnectionMethod inferred) {
        return requested == ConnectionMethod.AUTO ? inferred : requested;
    }

    public record Input(
            EngineDescriptor engine,
            String blockId,
            ConnectionMethod inferredMethod,
            NativeQueryObservation observation,
            List<NativeRule> nativeRules,
            List<ManagedRule> managedRules,
            int managedPackPriority) {
        public Input {
            Objects.requireNonNull(engine, "engine");
            if (blockId == null || blockId.isBlank()) {
                throw new IllegalArgumentException("blockId must not be blank");
            }
            Objects.requireNonNull(inferredMethod, "inferredMethod");
            Objects.requireNonNull(observation, "observation");
            nativeRules = List.copyOf(Objects.requireNonNull(nativeRules, "nativeRules"));
            managedRules = List.copyOf(Objects.requireNonNull(managedRules, "managedRules"));
            if (managedPackPriority < 0) {
                throw new IllegalArgumentException("managedPackPriority must be non-negative");
            }
        }
    }
}
