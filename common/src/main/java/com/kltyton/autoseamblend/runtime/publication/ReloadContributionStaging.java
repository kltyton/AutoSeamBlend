package com.kltyton.autoseamblend.runtime.publication;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * 中文：跨 Loader 的 token 参与者 prepare/abort 暂存；排序和一次性 abort 由公共状态机负责。
 *
 * English: Loader-neutral token participant prepare/abort staging; ordering and one-shot abort are
 * owned by this shared state machine.
 */
public final class ReloadContributionStaging<T> {
    private final Map<String, T> staged = new TreeMap<>();
    private boolean aborted;

    public void stage(String participantId, T value) {
        requireId(participantId);
        Objects.requireNonNull(value, "value");
        if (aborted) {
            throw new IllegalStateException("cannot stage an aborted reload contribution");
        }
        T previous = staged.putIfAbsent(participantId, value);
        if (previous != null) {
            throw new IllegalStateException(
                    "duplicate prepared reload contribution: " + participantId);
        }
    }

    public boolean abort() {
        if (aborted) {
            return false;
        }
        aborted = true;
        return true;
    }

    public boolean aborted() {
        return aborted;
    }

    public Map<String, T> snapshot() {
        return Collections.unmodifiableMap(new TreeMap<>(staged));
    }

    private static void requireId(String participantId) {
        if (participantId == null || participantId.isBlank()) {
            throw new IllegalArgumentException("participantId must not be blank");
        }
    }
}
