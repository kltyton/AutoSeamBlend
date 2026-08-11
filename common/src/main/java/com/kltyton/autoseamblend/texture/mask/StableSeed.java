package com.kltyton.autoseamblend.texture.mask;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** 中文：稳定哈希与坐标采样；刻意独立于 JVM hashCode。 / English: Stable hashes and coordinate samples; deliberately independent of JVM hashCode. */
public final class StableSeed {
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private StableSeed() {
    }

    public static long hashUtf8(String value) {
        Objects.requireNonNull(value, "value");
        long hash = FNV_OFFSET_BASIS;
        for (byte element : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= element & 0xFFL;
            hash *= FNV_PRIME;
        }
        return hash;
    }

    public static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }

    public static long combine(long seed, long value) {
        return mix64(seed ^ (value + 0x9e3779b97f4a7c15L + (seed << 6) + (seed >>> 2)));
    }

    /** 中文：返回区间 [0, 1) 内的确定性值。 / English: Returns a deterministic value in [0, 1). */
    public static double sample01(long seed, int x, int y, int salt) {
        long value = combine(seed, Integer.toUnsignedLong(x));
        value = combine(value, Integer.toUnsignedLong(y));
        value = combine(value, Integer.toUnsignedLong(salt));
        return (mix64(value) >>> 11) * 0x1.0p-53;
    }
}
