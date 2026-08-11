package com.kltyton.autoseamblend.compat.ctm_mod.evidence;

import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import com.kltyton.autoseamblend.engine.ownership.NativeSlotIntent;
import com.kltyton.autoseamblend.engine.ownership.evidence.NativeResourceSource;
import com.kltyton.autoseamblend.engine.ownership.evidence.NativeResourceSource.SheetFramePolicy;
import com.kltyton.autoseamblend.engine.ownership.evidence.NativeResourceSource.TextureResourceState;
import com.kltyton.autoseamblend.engine.ownership.evidence.NativeSlotEvidence;
import com.kltyton.autoseamblend.engine.ownership.evidence.NativeSlotEvidence.Observation;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：把 CTM Mod 载体声明归一化为公共逐槽证据。
 * English: Normalizes CTM Mod carrier declarations into common per-slot evidence.
 */
public final class CtmModSlotEvidenceResolver {
    private CtmModSlotEvidenceResolver() {}

    public static Observation observe(
            String role,
            boolean declared,
            Optional<String> spriteId,
            int columns,
            int rows,
            NativeResourceSource resources) {
        Objects.requireNonNull(role, "role");
        spriteId = Objects.requireNonNull(spriteId, "spriteId");
        Objects.requireNonNull(resources, "resources");
        if (!declared) {
            return new Observation(
                    role,
                    NativeSlotIntent.OMITTED,
                    Optional.empty());
        }
        if (spriteId.isEmpty()) {
            return new Observation(
                    role,
                    NativeSlotIntent.UNKNOWN,
                    Optional.empty());
        }
        String sprite = spriteId.orElseThrow();
        TextureResourceState state = resources.inspectTexture(
                sprite,
                columns,
                rows,
                SheetFramePolicy.STANDARD);
        return switch (state) {
            case PRESENT -> new Observation(
                    role,
                    NativeSlotIntent.PRESENT,
                    Optional.of(sprite));
            case MISSING -> new Observation(
                    role,
                    NativeSlotIntent.DECLARED_MISSING,
                    Optional.of(sprite));
            case INVALID -> new Observation(
                    role,
                    NativeSlotIntent.UNKNOWN,
                    Optional.empty());
        };
    }

    public static List<NativeSlot> resolve(
            ConnectionMethod method,
            List<Observation> evidence) {
        Objects.requireNonNull(method, "method");
        evidence = List.copyOf(
                Objects.requireNonNull(evidence, "evidence"));
        if (evidence.isEmpty()) {
            return NativeSlotEvidence.unknown(
                    method,
                    NativeSlotEvidence.FULL_CTM_SLOTS);
        }
        List<Integer> logical = NativeSlotEvidence.domain(
                method,
                NativeSlotEvidence.FULL_CTM_SLOTS);
        if (logical.isEmpty()) {
            return List.of();
        }
        Optional<Observation> disconnected = evidence.stream()
                .filter(value -> value.role().endsWith("disconnected"))
                .findFirst();
        ArrayList<NativeSlot> slots = new ArrayList<>(logical.size());
        for (int slot : logical) {
            List<Observation> required = slot == 0
                            && disconnected.isPresent()
                    ? List.of(disconnected.orElseThrow())
                    : evidence;
            slots.add(NativeSlotEvidence.collapse(slot, required));
        }
        return List.copyOf(slots);
    }
}
