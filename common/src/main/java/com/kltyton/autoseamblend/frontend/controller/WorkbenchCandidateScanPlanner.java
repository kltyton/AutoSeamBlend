package com.kltyton.autoseamblend.frontend.controller;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * 中文：跨 Loader 的候选扫描切片器；不依赖注册表，只调度原生候选源。
 *
 * English: Loader-neutral candidate scan slicer. It knows no registry and only
 * schedules a native candidate source.
 */
public final class WorkbenchCandidateScanPlanner<C> {
    private final int maxItems;
    private final long maxNanos;
    private Iterator<C> iterator;

    public WorkbenchCandidateScanPlanner(int maxItems, long maxNanos) {
        if (maxItems <= 0 || maxNanos <= 0) {
            throw new IllegalArgumentException(
                    "candidate scan limits must be positive");
        }
        this.maxItems = maxItems;
        this.maxNanos = maxNanos;
    }

    public void begin(Iterable<C> source) {
        Objects.requireNonNull(source, "source");
        if (iterator != null) {
            throw new IllegalStateException(
                    "candidate scan already active");
        }
        iterator = Objects.requireNonNull(
                source.iterator(),
                "candidate iterator");
    }

    public boolean active() {
        return iterator != null;
    }

    public Slice tick(
            LongSupplier clock,
            Consumer<C> sink) {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(sink, "sink");
        Iterator<C> current = iterator;
        if (current == null) {
            return new Slice(0, true, 0L);
        }
        long started = clock.getAsLong();
        int processed = 0;
        while (current.hasNext()
                && processed < maxItems
                && (processed == 0
                        || clock.getAsLong() - started < maxNanos)) {
            sink.accept(current.next());
            processed++;
        }
        boolean complete = !current.hasNext();
        if (complete) {
            iterator = null;
        }
        return new Slice(
                processed,
                complete,
                Math.max(0L, clock.getAsLong() - started));
    }

    public record Slice(
            int processed,
            boolean complete,
            long elapsedNanos) {
        public Slice {
            if (processed < 0 || elapsedNanos < 0) {
                throw new IllegalArgumentException(
                        "candidate scan slice values must be nonnegative");
            }
        }
    }
}
