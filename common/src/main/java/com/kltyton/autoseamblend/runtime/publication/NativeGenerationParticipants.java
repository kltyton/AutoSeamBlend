package com.kltyton.autoseamblend.runtime.publication;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 中文：为根重载边界登记、检查并丢弃无模型载体的原生捕获。
 *
 * English: Registers, checks, and discards native captures without model carriers at the root
 * reload boundary.
 */
public final class NativeGenerationParticipants {
    private static final ConcurrentHashMap<String, NativeGenerationParticipant> PARTICIPANTS =
            new ConcurrentHashMap<>();

    private NativeGenerationParticipants() {}

    public static void register(NativeGenerationParticipant participant) {
        Objects.requireNonNull(participant, "participant");
        NativeGenerationParticipant previous = PARTICIPANTS.putIfAbsent(
                participant.engineId(),
                participant);
        if (previous != null
                && previous != participant
                && !previous.getClass().equals(participant.getClass())) {
            throw new IllegalStateException(
                    "native generation participant already registered: " + participant.engineId());
        }
    }

    public static boolean allPrepared(long generation) {
        return ordered().stream().allMatch(participant -> participant.prepared(generation));
    }

    public static void abort(long generation) {
        ordered().forEach(participant -> participant.abort(generation));
    }

    private static List<NativeGenerationParticipant> ordered() {
        ArrayList<NativeGenerationParticipant> values = new ArrayList<>(PARTICIPANTS.values());
        values.sort(Comparator.comparing(NativeGenerationParticipant::engineId));
        return List.copyOf(values);
    }
}
