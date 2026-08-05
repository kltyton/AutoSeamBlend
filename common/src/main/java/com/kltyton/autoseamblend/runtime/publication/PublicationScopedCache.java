package com.kltyton.autoseamblend.runtime.publication;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 中文：按不可变发布对象身份原子轮换的并发缓存；旧发布的读取者继续使用自己的表。
 *
 * <p>English: Concurrent cache atomically rotated by immutable publication identity. Readers of an
 * old publication keep using their own table.
 */
public final class PublicationScopedCache<P, K, V> {
    private final AtomicReference<Entry<P, K, V>> current;

    public PublicationScopedCache(P bootstrapPublication) {
        current = new AtomicReference<>(Entry.create(bootstrapPublication));
    }

    public ConcurrentHashMap<K, V> entries(P publication) {
        Objects.requireNonNull(publication, "publication");
        Entry<P, K, V> entry = current.get();
        if (entry.matches(publication)) {
            return entry.entries();
        }
        Entry<P, K, V> next = Entry.create(publication);
        current.compareAndSet(entry, next);
        return current.get().entries();
    }

    public void reset(P publication) {
        current.set(Entry.create(publication));
    }

    private record Entry<P, K, V>(
            P publication,
            ConcurrentHashMap<K, V> entries) {
        private Entry {
            Objects.requireNonNull(publication, "publication");
            Objects.requireNonNull(entries, "entries");
        }

        private static <P, K, V> Entry<P, K, V> create(P publication) {
            return new Entry<>(
                    Objects.requireNonNull(publication, "publication"),
                    new ConcurrentHashMap<>());
        }

        private boolean matches(P candidate) {
            return publication == candidate;
        }
    }
}
