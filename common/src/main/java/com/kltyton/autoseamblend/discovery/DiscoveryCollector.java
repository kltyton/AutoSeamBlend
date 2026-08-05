package com.kltyton.autoseamblend.discovery;

import com.kltyton.autoseamblend.discovery.DiscoveryDiagnostic.Severity;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet.ResourcePackMode;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 中文：在公共领域层将烘焙面事实纯函数式组装为隐式自连接候选。 / English: Pure common-domain assembly of baked face facts into implicit self-connect candidates. */
public final class DiscoveryCollector {
    private DiscoveryCollector() {}

    public static DiscoverySnapshot collect(
            long generation,
            DiscoverySource source,
            ExclusionLookup exclusions,
            List<DiscoveryDiagnostic> additionalDiagnostics) {
        Objects.requireNonNull(exclusions, "exclusions");
        Objects.requireNonNull(source, "source");
        LinkedHashMap<String, List<FaceFacts>> byTarget = new LinkedHashMap<>();
        source.facts().stream()
                .filter(fact -> !source.incompleteTargets().contains(fact.targetId()))
                .sorted(Comparator.comparing(FaceFacts::targetId)
                        .thenComparing(FaceFacts::stateKey)
                        .thenComparing(FaceFacts::face)
                        .thenComparing(FaceFacts::spriteId))
                .forEach(fact -> byTarget.computeIfAbsent(fact.targetId(), ignored -> new ArrayList<>()).add(fact));

        LinkedHashMap<String, DiscoveryCandidate> candidates = new LinkedHashMap<>();
        ArrayList<DiscoveryDiagnostic> diagnostics = new ArrayList<>(source.diagnostics());
        diagnostics.addAll(Objects.requireNonNull(additionalDiagnostics, "additionalDiagnostics"));
        source.incompleteTargets().forEach(targetId -> diagnostics.add(new DiscoveryDiagnostic(
                Severity.WARNING,
                "INCOMPLETE_BLOCK_SUPPRESSED",
                targetId,
                "At least one required state/model inspection failed; no implicit rule was built from partial facts")));
        byTarget.forEach((targetId, targetFacts) -> {
            List<FaceFacts> connectableFaces = targetFacts.stream()
                    .filter(DiscoveryCollector::connectable)
                    .toList();
            if (connectableFaces.isEmpty()) {
                diagnostics.add(new DiscoveryDiagnostic(
                        Severity.INFO,
                        "NO_CONNECTABLE_FACE",
                        targetId,
                        rejectionEvidence(targetFacts)));
                return;
            }
            Set<ResourcePackMode> excludedModes = immutableModes(
                    exclusions.excludedModes(targetId, ConnectionMethod.AUTO));
            if (excludedModes.size() == ResourcePackMode.values().length) {
                diagnostics.add(new DiscoveryDiagnostic(
                        Severity.INFO,
                        "EXCLUDED_ALL_MODES",
                        targetId,
                        "Explicit auto exclusions suppress this implicit candidate in both policies"));
                return;
            }
            candidates.put(targetId, DiscoveryCandidate.implicitSelf(targetId, connectableFaces, excludedModes));
            diagnostics.add(new DiscoveryDiagnostic(
                    Severity.INFO,
                    "IMPLICIT_SELF_CONNECTION",
                    targetId,
                    evidence(connectableFaces) + "; connection group is block:" + targetId));
            excludedModes.forEach(mode -> diagnostics.add(new DiscoveryDiagnostic(
                    Severity.INFO,
                    "EXCLUDED_POLICY",
                    targetId,
                    "Explicit exclusion applies to auto/" + mode.serializedName())));
        });
        return new DiscoverySnapshot(generation, candidates, diagnostics);
    }

    private static boolean connectable(FaceFacts fact) {
        return fact.axisAligned()
                && fact.maxU() > fact.minU()
                && fact.maxV() > fact.minV();
    }

    private static String rejectionEvidence(List<FaceFacts> facts) {
        long states = facts.stream().map(FaceFacts::stateKey).distinct().count();
        long axisAligned = facts.stream().filter(FaceFacts::axisAligned).count();
        long nonDegenerateUv = facts.stream()
                .filter(fact -> fact.maxU() > fact.minU() && fact.maxV() > fact.minV())
                .count();
        return "No stable adjacency face passed geometry checks: states=" + states
                + ", faces=" + facts.size()
                + ", axis-aligned=" + axisAligned
                + ", non-degenerate-uv=" + nonDegenerateUv;
    }

    private static Set<ResourcePackMode> immutableModes(Set<ResourcePackMode> values) {
        if (values.isEmpty()) return Set.of();
        return Set.copyOf(EnumSet.copyOf(values));
    }

    private static String evidence(List<FaceFacts> facts) {
        long states = facts.stream().map(FaceFacts::stateKey).distinct().count();
        long fullFaces = facts.stream().filter(FaceFacts::fullFace).count();
        long opaqueFaces = facts.stream().filter(FaceFacts::opaque).count();
        long animatedFaces = facts.stream().filter(FaceFacts::animated).count();
        long tintedFaces = facts.stream().filter(FaceFacts::tinted).count();
        long unknownPriority = facts.stream()
                .filter(fact -> fact.resourcePackPriority() == FaceFacts.UNKNOWN_RESOURCE_PACK_PRIORITY)
                .count();
        long unknownNativeOwnership = facts.stream()
                .filter(fact -> fact.nativeOwnership() == FaceFacts.NativeOwnership.UNKNOWN)
                .count();
        return "Discovered axis-aligned baked geometry: states=" + states
                + ", faces=" + facts.size()
                + ", full-faces=" + fullFaces
                + ", opaque=" + opaqueFaces
                + ", animated=" + animatedFaces
                + ", tinted=" + tintedFaces
                + ", unknown-pack-priority=" + unknownPriority
                + ", unknown-native-ownership=" + unknownNativeOwnership;
    }

    @FunctionalInterface
    public interface ExclusionLookup {
        Set<ResourcePackMode> excludedModes(String targetId, ConnectionMethod method);
    }
}
