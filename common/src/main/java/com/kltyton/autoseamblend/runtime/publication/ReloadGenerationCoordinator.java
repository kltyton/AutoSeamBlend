package com.kltyton.autoseamblend.runtime.publication;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 中文：所有 Loader 共用的资源代次协调器；只保存不可变快照，不了解任意平台 payload。
 *
 * English: Loader-neutral resource-generation coordinator. It stores immutable snapshots only and
 * knows nothing about platform payloads.
 *
 * <p>The coordinator owns the reload ordinal watermark, generation read/write barrier, atomic
 * active-snapshot replacement, selector-revision transition, and stale-token rejection. Loader
 * facades retain only their public token/lease types and platform-specific snapshot factories.
 */
public final class ReloadGenerationCoordinator<T> {
    private final AtomicReference<T> active;
    private final Function<T, GenerationPublicationState.Marker> markerOf;
    private final String staleTokenMessage;
    private final AtomicLong ordinals = new AtomicLong();
    private final Object sequence = new Object();
    private final ReentrantReadWriteLock publication = new ReentrantReadWriteLock(true);
    private long latestOrdinal;

    public ReloadGenerationCoordinator(
            T initial,
            Function<T, GenerationPublicationState.Marker> markerOf,
            String staleTokenMessage) {
        this.active = new AtomicReference<>(Objects.requireNonNull(initial, "initial"));
        this.markerOf = Objects.requireNonNull(markerOf, "markerOf");
        this.staleTokenMessage = requireText(staleTokenMessage, "staleTokenMessage");
        markerOf.apply(initial);
    }

    public T current() {
        return active.get();
    }

    public <R> R read(Function<T, R> reader) {
        Objects.requireNonNull(reader, "reader");
        Lock readLock = publication.readLock();
        readLock.lock();
        try {
            return reader.apply(active.get());
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 中文：在共享写屏障内执行一个 Loader 侧的 staged-state 变更。
     * English: Runs one Loader-side staged-state mutation inside the shared write barrier.
     */
    public <R> R withWriteLock(Supplier<R> operation) {
        Objects.requireNonNull(operation, "operation");
        Lock writeLock = publication.writeLock();
        writeLock.lock();
        try {
            return operation.get();
        } finally {
            writeLock.unlock();
        }
    }

    public void withWriteLock(Runnable operation) {
        Objects.requireNonNull(operation, "operation");
        withWriteLock(() -> {
            operation.run();
            return null;
        });
    }

    public GenerationPublicationState.Token begin(String reason) {
        synchronized (sequence) {
            long ordinal = ordinals.incrementAndGet();
            latestOrdinal = ordinal;
            return GenerationPublicationState.begin(markerOf.apply(active.get()), ordinal, reason);
        }
    }

    public boolean isLatest(long ordinal) {
        if (ordinal <= 0) {
            return false;
        }
        synchronized (sequence) {
            return latestOrdinal == ordinal;
        }
    }

    public T publish(GenerationPublicationState.Token token, T candidate) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(candidate, "candidate");
        if (publication.getReadHoldCount() != 0) {
            throw new IllegalStateException(
                    "cannot publish a reload generation while holding a generation read lease");
        }
        Lock writeLock = publication.writeLock();
        writeLock.lock();
        try {
            synchronized (sequence) {
                if (token.ordinal() != latestOrdinal) {
                    throw new IllegalStateException(staleTokenMessage);
                }
                GenerationPublicationState.publish(
                        markerOf.apply(active.get()), token, markerOf.apply(candidate));
                active.set(candidate);
                return candidate;
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 中文：同步阶段只提交严格的下一代候选，不改变活动快照。
     * English: Validates an exact next-generation candidate without changing the active snapshot.
     */
    public void validateNext(T candidate) {
        Objects.requireNonNull(candidate, "candidate");
        GenerationPublicationState.commitNext(
                markerOf.apply(active.get()),
                markerOf.apply(candidate).generation(),
                markerOf.apply(candidate).selectorRevision());
    }

    /**
     * 中文：在共享写屏障内发布严格下一代候选。
     * English: Publishes an exact next-generation candidate under the shared write barrier.
     */
    public T commitNext(T candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return withWriteLock(() -> {
            GenerationPublicationState.commitNext(
                    markerOf.apply(active.get()),
                    markerOf.apply(candidate).generation(),
                    markerOf.apply(candidate).selectorRevision());
            active.set(candidate);
            return candidate;
        });
    }

    public long nextGeneration() {
        return read(value -> GenerationPublicationState.nextGeneration(markerOf.apply(value)));
    }

    /**
     * 中文：只允许在当前资源代次内更新 selector；candidate 的 revision 必须正好推进一位。
     * English: Updates selectors only within the active resource generation; the candidate revision
     * must advance by exactly one.
     */
    public Optional<T> tryPublishSelector(
            long generation,
            BiFunction<T, GenerationPublicationState.Marker, T> updater) {
        Objects.requireNonNull(updater, "updater");
        Lock writeLock = publication.writeLock();
        writeLock.lock();
        try {
            synchronized (sequence) {
                T current = active.get();
                GenerationPublicationState.Marker currentMarker = markerOf.apply(current);
                if (currentMarker.generation() != generation) {
                    return Optional.empty();
                }
                GenerationPublicationState.Marker next =
                        GenerationPublicationState.advanceSelectorRevision(
                                currentMarker, generation, currentMarker.selectorRevision());
                T candidate = Objects.requireNonNull(updater.apply(current, next), "candidate");
                if (!next.equals(markerOf.apply(candidate))) {
                    throw new IllegalArgumentException(
                            "selector candidate does not match the next selector revision");
                }
                active.set(candidate);
                return Optional.of(candidate);
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 中文：替换同一资源代次的同步 selector 快照；candidate 不得改写资源代次或 revision。
     * English: Replaces a synchronous selector snapshot within the same resource generation; the
     * candidate cannot rewrite the resource generation or revision.
     */
    public T replaceSameGeneration(T candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return withWriteLock(() -> {
            GenerationPublicationState.Marker current = markerOf.apply(active.get());
            GenerationPublicationState.Marker replacement = markerOf.apply(candidate);
            if (!current.equals(replacement)) {
                throw new IllegalArgumentException(
                        "synchronous selector replacement changed the publication marker");
            }
            active.set(candidate);
            return candidate;
        });
    }

    public ReadLease acquireReadLease(long expectedGeneration) {
        return acquireReadLease(expectedGeneration, false, 0);
    }

    public ReadLease acquireReadLease(long expectedGeneration, long expectedSelectorRevision) {
        return acquireReadLease(expectedGeneration, true, expectedSelectorRevision);
    }

    private ReadLease acquireReadLease(
            long expectedGeneration,
            boolean verifySelectorRevision,
            long expectedSelectorRevision) {
        if (expectedGeneration < 0) {
            throw new IllegalArgumentException("expectedGeneration must be non-negative");
        }
        if (verifySelectorRevision && expectedSelectorRevision < 0) {
            throw new IllegalArgumentException(
                    "expectedSelectorRevision must be non-negative");
        }
        Lock readLock = publication.readLock();
        readLock.lock();
        GenerationPublicationState.Marker marker = markerOf.apply(active.get());
        if (marker.generation() != expectedGeneration
                || (verifySelectorRevision && marker.selectorRevision() != expectedSelectorRevision)) {
            readLock.unlock();
            throw new IllegalStateException(
                    verifySelectorRevision
                            ? "WORKBENCH_GENERATION_OR_SELECTOR_REVISION_STALE"
                            : "WORKBENCH_GENERATION_STALE");
        }
        return new ReadLease(expectedGeneration, readLock, Thread.currentThread());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    /** 中文：短期、线程绑定、可重复关闭的资源代次读屏障。 / English: Short, thread-bound, idempotently closable generation read barrier. */
    public static final class ReadLease implements AutoCloseable {
        private final long generation;
        private final Lock readLock;
        private final Thread owner;
        private boolean closed;

        private ReadLease(long generation, Lock readLock, Thread owner) {
            this.generation = generation;
            this.readLock = readLock;
            this.owner = owner;
        }

        public long generation() {
            return generation;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException(
                        "generation read lease must close on its owning thread");
            }
            readLock.unlock();
            closed = true;
        }
    }
}
