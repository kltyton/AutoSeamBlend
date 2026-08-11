package com.kltyton.autoseamblend.engine.ownership.evidence;

import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import com.kltyton.autoseamblend.engine.ownership.NativeSlotIntent;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.selection.method.MethodSlotDomain;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * 中文：保存格式无关的槽位域、证据构造与意图合并优先级。
 * English: Owns format-neutral slot domains, evidence construction, and intent merge priority.
 */
public final class NativeSlotEvidence {
    public static final int FULL_CTM_SLOTS = 47;

    private NativeSlotEvidence() {}

    /**
     * 中文：保护性优先级固定为 unknown、declared-missing、omitted、present。
     * English: The protection priority is unknown, declared-missing, omitted, then present.
     */
    public static NativeSlot collapse(
            int slot,
            List<Observation> required) {
        required = List.copyOf(
                Objects.requireNonNull(required, "required"));
        if (required.isEmpty()) {
            throw new IllegalArgumentException(
                    "required evidence must not be empty");
        }
        Optional<Observation> unknown = required.stream()
                .filter(value -> value.intent() == NativeSlotIntent.UNKNOWN)
                .findFirst();
        if (unknown.isPresent()) {
            return unknown(slot);
        }
        Optional<Observation> missing = required.stream()
                .filter(value -> value.intent()
                        == NativeSlotIntent.DECLARED_MISSING)
                .findFirst();
        if (missing.isPresent()) {
            return new NativeSlot(
                    slot,
                    NativeSlotIntent.DECLARED_MISSING,
                    missing.orElseThrow().spriteId());
        }
        if (required.stream().anyMatch(value ->
                value.intent() == NativeSlotIntent.OMITTED)) {
            return new NativeSlot(
                    slot,
                    NativeSlotIntent.OMITTED,
                    Optional.empty());
        }
        return new NativeSlot(
                slot,
                NativeSlotIntent.PRESENT,
                required.stream()
                        .map(Observation::spriteId)
                        .flatMap(Optional::stream)
                        .findFirst());
    }

    public static List<NativeSlot> present(
            ConnectionMethod method,
            int fallbackSlots,
            String spriteId) {
        return present(domain(method, fallbackSlots), spriteId);
    }

    public static List<NativeSlot> present(
            List<Integer> domain,
            String spriteId) {
        Objects.requireNonNull(spriteId, "spriteId");
        return domain.stream()
                .map(slot -> new NativeSlot(
                        slot,
                        NativeSlotIntent.PRESENT,
                        Optional.of(spriteId)))
                .toList();
    }

    public static List<NativeSlot> declaredMissing(
            ConnectionMethod method,
            int fallbackSlots,
            String spriteId) {
        Objects.requireNonNull(spriteId, "spriteId");
        return domain(method, fallbackSlots).stream()
                .map(slot -> new NativeSlot(
                        slot,
                        NativeSlotIntent.DECLARED_MISSING,
                        Optional.of(spriteId)))
                .toList();
    }

    public static List<NativeSlot> unknown(
            ConnectionMethod method,
            int fallbackSlots) {
        return unknown(domain(method, fallbackSlots));
    }

    public static List<NativeSlot> unknown(List<Integer> domain) {
        return domain.stream()
                .map(NativeSlotEvidence::unknown)
                .toList();
    }

    public static NativeSlot unknown(int slot) {
        return new NativeSlot(
                slot,
                NativeSlotIntent.UNKNOWN,
                Optional.empty());
    }

    public static List<Integer> domain(
            ConnectionMethod method,
            int fallbackSlots) {
        Objects.requireNonNull(method, "method");
        if (fallbackSlots < 0) {
            throw new IllegalArgumentException(
                    "fallbackSlots must be non-negative");
        }
        return method == ConnectionMethod.AUTO
                ? IntStream.range(0, fallbackSlots)
                        .boxed()
                        .toList()
                : MethodSlotDomain.of(method).slots();
    }

    public record Observation(
            String role,
            NativeSlotIntent intent,
            Optional<String> spriteId) {
        public Observation {
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException(
                        "role must not be blank");
            }
            Objects.requireNonNull(intent, "intent");
            spriteId = Objects.requireNonNull(
                    spriteId,
                    "spriteId");
            if ((intent == NativeSlotIntent.PRESENT
                            || intent == NativeSlotIntent.DECLARED_MISSING)
                    && spriteId.isEmpty()) {
                throw new IllegalArgumentException(
                        "present evidence must retain its sprite id");
            }
        }
    }
}
