package com.kltyton.autoseamblend.compat.continuity.runtime;

import java.util.Objects;

/**
 * 中文：保存当前代次及其前一代的 Continuity 捕获状态，并拒绝旧 token 覆盖同一目标代次。
 *
 * English: Retains the current and immediately previous Continuity capture generations and
 * rejects an older token from replacing the same target generation.
 *
 * @param <T> 中文：代次捕获值；English: captured generation value
 */
public final class ContinuityGenerationHistory<T> {
    private Slot<T> first;
    private Slot<T> second;

    /**
     * 中文：按目标代次清理旧状态并开始新的捕获；English: clears stale state and starts a
     * capture for the target generation.
     */
    public synchronized void begin(long targetGeneration) {
        requireGeneration(targetGeneration);
        if (first != null && first.generation() < targetGeneration - 1) {
            first = null;
        }
        if (second != null && second.generation() < targetGeneration - 1) {
            second = null;
        }
        removeGeneration(targetGeneration);
    }

    /**
     * 中文：将候选代次放入 current/target 双槽；English: stages a candidate in the
     * current/target two-slot window.
     */
    public synchronized boolean stage(
            long targetGeneration,
            long activeGeneration,
            long tokenOrdinal,
            T value) {
        requireGeneration(targetGeneration);
        if (activeGeneration < 0) {
            throw new IllegalArgumentException("activeGeneration must be non-negative");
        }
        if (tokenOrdinal <= 0) {
            throw new IllegalArgumentException("tokenOrdinal must be positive");
        }
        Objects.requireNonNull(value, "value");
        Slot<T> sameTarget = slotForGeneration(targetGeneration);
        if (sameTarget != null && sameTarget.tokenOrdinal() > tokenOrdinal) {
            return false;
        }
        Slot<T> active = slotForGeneration(activeGeneration);
        Slot<T> target = new Slot<>(targetGeneration, tokenOrdinal, value);
        if (active == null || active.generation() == targetGeneration) {
            first = target;
            second = null;
        } else {
            first = active;
            second = target;
        }
        return true;
    }

    public synchronized T value(long generation) {
        Slot<T> slot = slotForGeneration(generation);
        return slot == null ? null : slot.value();
    }

    public synchronized boolean matches(
            long generation,
            long tokenOrdinal,
            T expected) {
        Slot<T> slot = slotForGeneration(generation);
        return slot != null
                && slot.tokenOrdinal() == tokenOrdinal
                && slot.value() == expected;
    }

    public synchronized void discardToken(long tokenOrdinal) {
        if (tokenOrdinal <= 0) {
            throw new IllegalArgumentException("tokenOrdinal must be positive");
        }
        if (first != null && first.tokenOrdinal() == tokenOrdinal) {
            first = null;
        }
        if (second != null && second.tokenOrdinal() == tokenOrdinal) {
            second = null;
        }
    }

    public synchronized void removeGeneration(long generation) {
        requireGeneration(generation);
        if (first != null && first.generation() == generation) {
            first = second;
            second = null;
        } else if (second != null && second.generation() == generation) {
            second = null;
        }
    }

    public synchronized void clear() {
        first = null;
        second = null;
    }

    private Slot<T> slotForGeneration(long generation) {
        if (first != null && first.generation() == generation) {
            return first;
        }
        return second != null && second.generation() == generation
                ? second
                : null;
    }

    private static void requireGeneration(long generation) {
        if (generation <= 0) {
            throw new IllegalArgumentException("generation must be positive");
        }
    }

    private record Slot<T>(long generation, long tokenOrdinal, T value) {
        private Slot {
            if (generation <= 0 || tokenOrdinal <= 0) {
                throw new IllegalArgumentException("generation and tokenOrdinal must be positive");
            }
            Objects.requireNonNull(value, "value");
        }
    }
}
