package com.kltyton.autoseamblend.texture.budget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 中文：校验一次 reload 捕获的 required 唯一源精灵的聚合像素和 CPU 保留预算。
 * English: Validates aggregate pixels and retained CPU memory for the unique required source
 * sprites captured by one reload.
 *
 * <p>中文：该预算按一次 reload 捕获、冻结、owning merge 与共享 planning catalog 期间的
 * canonical ARGB 保留峰值计算；三引擎和多面 snapshot 只共享 catalog image，不按引擎复制
 * 计费。English: This budget estimates the canonical ARGB retention peak across capture,
 * freezing, owning merge, and the shared planning catalog; three engines and many face snapshots
 * reuse one catalog image and are not charged once per engine.</p>
 */
public final class TextureSourceBudget {
    /** 中文：一次 reload 允许捕获的 required 源像素总数。 / English: Maximum required source pixels captured by one reload. */
    public static final long MAX_REQUIRED_SOURCE_PIXELS = 67_108_864L;

    /**
     * 中文：一次 reload 允许登记的 required 唯一 source 数量；先于新 map entry 分配拒绝。
     * English: Maximum unique required sources registered by one reload; reject before a new map
     * entry can be allocated.
     */
    public static final long MAX_REQUIRED_SOURCES = 65_536L;

    /** 中文：一次 reload 允许保留的 required 源 CPU 字节数（512 MiB）。 / English: Maximum retained required-source CPU bytes per reload (512 MiB). */
    public static final long MAX_REQUIRED_SOURCE_CPU_BYTES = 512L * 1024L * 1024L;

    /** 中文：一次 planning catalog 允许保留的源 metadata 总字节数（64 MiB）。 / English: Maximum aggregate source metadata retained by one planning catalog (64 MiB). */
    public static final long MAX_REQUIRED_SOURCE_METADATA_BYTES = 64L * 1024L * 1024L;

    /** 中文：单份 ARGB int[] 每像素字节数。 / English: Bytes per pixel in one ARGB int[]. */
    public static final long ARGB_BYTES_PER_PIXEL = 4L;

    /**
     * 中文：capture、freeze/merge 与一个共享 planning-catalog canonical ARGB 数组的峰值
     * CPU 字节数/像素。English: Peak CPU bytes per pixel for capture, freeze/merge, and one
     * shared planning-catalog canonical ARGB array.
     */
    public static final long PEAK_CPU_BYTES_PER_PIXEL = 3L * ARGB_BYTES_PER_PIXEL;

    /** 中文：无状态预算实例。 / English: Stateless budget instance. */
    public static final TextureSourceBudget DEFAULT = new TextureSourceBudget();

    private TextureSourceBudget() {
    }

    /**
     * 中文：创建一次 reload 的局部累加器；累加器不跨 reload 或线程共享。
     * English: Creates a reload-local accumulator; the accumulator is never shared across reloads
     * or threads.
     */
    public Accumulator accumulator() {
        return new Accumulator();
    }

    /**
     * 中文：一次 required source 捕获的可变局部预算累加器。
     * English: Mutable reload-local budget accumulator for required source capture.
     */
    public static final class Accumulator {
        private final LinkedHashMap<String, Long> reservedSources = new LinkedHashMap<>();
        private long sourceCount;
        private long pixelCount;
        private long cpuBytes;
        private long metadataBytes;

        private Accumulator() {
        }

        /**
         * 中文：在任何像素数组 clone 或 SourceImage 保留前预留一个唯一 source。
         * English: Reserves one unique source before any pixel-array clone or SourceImage retention.
         *
         * @return immutable usage after the reservation
         * @throws ViolationException when the reservation would exceed a frozen aggregate limit
         */
        public Usage reserve(String spriteId, long pixels) {
            String checkedSpriteId = Objects.requireNonNull(spriteId, "spriteId");
            if (checkedSpriteId.isBlank()) {
                throw violation(
                        ViolationCode.SOURCE_ID_BLANK,
                        0L,
                        1L,
                        checkedSpriteId);
            }
            if (pixels < 0L) {
                throw violation(
                        ViolationCode.SOURCE_PIXEL_COUNT_NEGATIVE,
                        pixels,
                        0L,
                        checkedSpriteId);
            }

            Long previousPixels = reservedSources.get(checkedSpriteId);
            if (previousPixels != null) {
                if (previousPixels.longValue() != pixels) {
                    throw violation(
                            ViolationCode.SOURCE_DUPLICATE_PIXEL_MISMATCH,
                            pixels,
                            previousPixels,
                            checkedSpriteId);
                }
                // 中文：同一 sprite 在 required 集中只能占一份聚合预算。
                // English: One sprite can consume only one aggregate reservation in the required
                // set.
                return usage();
            }

            // 中文：source map 已到上限时，在任何新 entry/object 分配前 fail-closed；重复 source
            // 仍可通过上面的去重路径成功。English: Fail closed before allocating any new map
            // entry/object at the source limit; duplicate sources still use the deduplicating path above.
            if (sourceCount >= MAX_REQUIRED_SOURCES) {
                throw violation(
                        ViolationCode.SOURCE_COUNT_EXCEEDED,
                        MAX_REQUIRED_SOURCES + 1L,
                        MAX_REQUIRED_SOURCES,
                        checkedSpriteId);
            }

            long nextSourceCount = checkedAdd(
                    sourceCount,
                    1L,
                    ViolationCode.SOURCE_COUNT_OVERFLOW,
                    checkedSpriteId);
            long nextPixels = checkedAdd(
                    pixelCount,
                    pixels,
                    ViolationCode.SOURCE_PIXEL_TOTAL_OVERFLOW,
                    checkedSpriteId);
            if (nextPixels > MAX_REQUIRED_SOURCE_PIXELS) {
                throw violation(
                        ViolationCode.SOURCE_PIXEL_TOTAL_EXCEEDED,
                        nextPixels,
                        MAX_REQUIRED_SOURCE_PIXELS,
                        checkedSpriteId);
            }
            long bytes = checkedMultiply(
                    pixels,
                    PEAK_CPU_BYTES_PER_PIXEL,
                    checkedSpriteId);
            long nextCpuBytes = checkedAdd(
                    cpuBytes,
                    bytes,
                    ViolationCode.SOURCE_CPU_TOTAL_OVERFLOW,
                    checkedSpriteId);
            if (nextCpuBytes > MAX_REQUIRED_SOURCE_CPU_BYTES) {
                throw violation(
                        ViolationCode.SOURCE_CPU_TOTAL_EXCEEDED,
                        nextCpuBytes,
                        MAX_REQUIRED_SOURCE_CPU_BYTES,
                        checkedSpriteId);
            }

            // 中文：所有检查通过后才更新计数，拒绝不会留下半个 source 的预算状态。
            // English: Update counters only after every check succeeds so rejection cannot leave
            // a partial source reservation behind.
            sourceCount = nextSourceCount;
            pixelCount = nextPixels;
            cpuBytes = nextCpuBytes;
            reservedSources.put(checkedSpriteId, pixels);
            return usage();
        }

        /**
         * 中文：在 planning catalog 保留 metadata 前预留聚合字节预算；同一 catalog 只应对
         * 每个唯一 sprite 调用一次。English: Reserves aggregate bytes before a planning catalog
         * retains metadata; one catalog should call this once per unique sprite.
         */
        public Usage reserveMetadata(String spriteId, long bytes) {
            String checkedSpriteId = Objects.requireNonNull(spriteId, "spriteId");
            if (checkedSpriteId.isBlank()) {
                throw violation(
                        ViolationCode.SOURCE_ID_BLANK,
                        0L,
                        1L,
                        checkedSpriteId,
                        "required-source-metadata");
            }
            if (bytes < 0L) {
                throw violation(
                        ViolationCode.SOURCE_METADATA_BYTES_NEGATIVE,
                        bytes,
                        0L,
                        checkedSpriteId,
                        "required-source-metadata");
            }
            long nextMetadataBytes;
            try {
                nextMetadataBytes = Math.addExact(metadataBytes, bytes);
            } catch (ArithmeticException exception) {
                throw violation(
                        ViolationCode.SOURCE_METADATA_TOTAL_OVERFLOW,
                        Long.MAX_VALUE,
                        Long.MAX_VALUE,
                        checkedSpriteId,
                        "required-source-metadata");
            }
            if (nextMetadataBytes > MAX_REQUIRED_SOURCE_METADATA_BYTES) {
                throw violation(
                        ViolationCode.SOURCE_METADATA_TOTAL_EXCEEDED,
                        nextMetadataBytes,
                        MAX_REQUIRED_SOURCE_METADATA_BYTES,
                        checkedSpriteId,
                        "required-source-metadata");
            }
            metadataBytes = nextMetadataBytes;
            return usage();
        }

        /** 中文：返回当前不可变预算快照。 / English: Returns the current immutable budget snapshot. */
        public Usage usage() {
            return new Usage(sourceCount, pixelCount, cpuBytes, metadataBytes);
        }

        private static long checkedAdd(
                long left,
                long right,
                ViolationCode code,
                String spriteId) {
            try {
                return Math.addExact(left, right);
            } catch (ArithmeticException exception) {
                throw violation(code, Long.MAX_VALUE, Long.MAX_VALUE, spriteId);
            }
        }

        private static long checkedMultiply(
                long left,
                long right,
                String spriteId) {
            try {
                return Math.multiplyExact(left, right);
            } catch (ArithmeticException exception) {
                throw violation(
                        ViolationCode.SOURCE_CPU_TOTAL_OVERFLOW,
                        Long.MAX_VALUE,
                        Long.MAX_VALUE,
                        spriteId);
            }
        }

        private static ViolationException violation(
                ViolationCode code,
                long observed,
                long limit,
                String spriteId) {
            return violation(code, observed, limit, spriteId, "required-source-capture");
        }

        private static ViolationException violation(
                ViolationCode code,
                long observed,
                long limit,
                String spriteId,
                String scope) {
            return new ViolationException(new Violation(
                    code,
                    observed,
                    limit,
                    Map.of(
                            "scope", scope,
                            "spriteId", spriteId)));
        }
    }

    /**
     * 中文：一次 successful required source 捕获的不可变预算用量。
     * English: Immutable budget usage for one successful required-source capture.
     */
    public record Usage(
            long sourceCount,
            long pixelCount,
            long cpuBytes,
            long metadataBytes) {
        public Usage {
            if (sourceCount < 0L || sourceCount > MAX_REQUIRED_SOURCES) {
                throw new IllegalArgumentException("sourceCount is outside source budget");
            }
            if (pixelCount < 0L || pixelCount > MAX_REQUIRED_SOURCE_PIXELS) {
                throw new IllegalArgumentException("pixelCount is outside source budget");
            }
            if (cpuBytes < 0L || cpuBytes > MAX_REQUIRED_SOURCE_CPU_BYTES) {
                throw new IllegalArgumentException("cpuBytes is outside source budget");
            }
            if (metadataBytes < 0L
                    || metadataBytes > MAX_REQUIRED_SOURCE_METADATA_BYTES) {
                throw new IllegalArgumentException("metadataBytes is outside source budget");
            }
            long minimum = Math.multiplyExact(pixelCount, PEAK_CPU_BYTES_PER_PIXEL);
            if (cpuBytes < minimum) {
                throw new IllegalArgumentException(
                        "cpuBytes must include capture, freeze/merge, and planning-catalog ARGB arrays");
            }
        }
    }

    /** 中文：稳定的 required-source 聚合预算诊断码。 / English: Stable aggregate required-source budget codes. */
    public enum ViolationCode {
        SOURCE_ID_BLANK,
        SOURCE_PIXEL_COUNT_NEGATIVE,
        SOURCE_DUPLICATE_PIXEL_MISMATCH,
        SOURCE_COUNT_OVERFLOW,
        SOURCE_COUNT_EXCEEDED,
        SOURCE_PIXEL_TOTAL_OVERFLOW,
        SOURCE_PIXEL_TOTAL_EXCEEDED,
        SOURCE_CPU_TOTAL_OVERFLOW,
        SOURCE_CPU_TOTAL_EXCEEDED,
        SOURCE_METADATA_BYTES_NEGATIVE,
        SOURCE_METADATA_TOTAL_OVERFLOW,
        SOURCE_METADATA_TOTAL_EXCEEDED
    }

    /**
     * 中文：不可变的 code/observed/limit/details 结构化诊断。
     * English: Immutable structured code/observed/limit/details diagnostic.
     */
    public record Violation(
            ViolationCode code,
            long observed,
            long limit,
            Map<String, String> details) {
        public Violation {
            Objects.requireNonNull(code, "code");
            Map<String, String> source = Objects.requireNonNull(details, "details");
            source.forEach((key, value) -> {
                Objects.requireNonNull(key, "details key");
                Objects.requireNonNull(value, "details value");
            });
            details = Collections.unmodifiableMap(new LinkedHashMap<>(source));
        }
    }

    /**
     * 中文：保留结构化预算诊断并兼容现有 IllegalArgumentException 调用链。
     * English: Retains structured budget diagnostics while remaining compatible with existing
     * IllegalArgumentException call chains.
     */
    public static final class ViolationException extends IllegalArgumentException {
        private final Violation violation;

        private ViolationException(Violation violation) {
            super(format(violation));
            this.violation = Objects.requireNonNull(violation, "violation");
        }

        public Violation violation() {
            return violation;
        }

        private static String format(Violation violation) {
            return "TEXTURE_SOURCE_BUDGET_VIOLATION:"
                    + violation.code()
                    + ":observed="
                    + violation.observed()
                    + ":limit="
                    + violation.limit()
                    + ":scope="
                    + violation.details().getOrDefault("scope", "unknown");
        }
    }
}
