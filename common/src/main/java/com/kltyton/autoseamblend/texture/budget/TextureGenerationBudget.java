package com.kltyton.autoseamblend.texture.budget;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 中文：校验一次完整 generation 的每键候选槽位、生成精灵、像素和显式内存估算。
 * English: Validates per-key candidate slots, generated sprites, pixels, and explicit memory
 * estimates for one complete generation.
 *
 * <p>中文：该类型是无 Loader、无 Minecraft 客户端和无引擎状态的无状态校验器；所有临时
 * 合并数据只在一次调用内存在，验证完成后不会保留。English: This type is a stateless
 * validator with no Loader, Minecraft-client, or engine state; temporary merge data exists only
 * for one call and is never retained.</p>
 *
 * <p>中文：CPU 字节数由调用方按实际引擎对象图聚合后传入，本类只执行冻结的 512 MiB
 * 上限校验；不要在这里假定所有引擎使用同一个 bytes-per-pixel 公式。GPU 基准按
 * {@value #GPU_BYTES_PER_PIXEL} bytes/pixel 计算，并受 256 MiB 上限约束。English: The
 * caller supplies CPU bytes aggregated from the actual engine object graph; this class enforces
 * only the frozen 512 MiB cap and does not assume one bytes-per-pixel formula for every engine.
 * The GPU base is {@value #GPU_BYTES_PER_PIXEL} bytes/pixel and is capped at 256 MiB.</p>
 */
public final class TextureGenerationBudget {
    /** 中文：每个候选-方法键允许的槽位数；不同键之间不共享此上限。 / English: Maximum slots per candidate-method key; distinct keys do not share this limit. */
    public static final long MAX_CANDIDATE_METHOD_SLOTS = 96L;

    /** 中文：一次 generation 允许的生成精灵总数。 / English: Maximum generated sprites per generation. */
    public static final long MAX_GENERATION_SPRITES = 65_536L;

    /** 中文：一次 generation 允许的生成像素总数。 / English: Maximum generated pixels per generation. */
    public static final long MAX_GENERATION_PIXELS = 67_108_864L;

    /** 中文：CPU 显式峰值估算上限（512 MiB）。 / English: Explicit CPU peak-estimate cap (512 MiB). */
    public static final long MAX_CPU_BYTES = 512L * 1024L * 1024L;

    /** 中文：GPU 显式基准估算上限（256 MiB）。 / English: Explicit GPU base-estimate cap (256 MiB). */
    public static final long MAX_GPU_BYTES = 256L * 1024L * 1024L;

    /** 中文：GPU 基准每像素字节数。 / English: GPU base bytes per generated pixel. */
    public static final long GPU_BYTES_PER_PIXEL = 4L;

    /** 中文：CPU 保留 ARGB int[] 的最低每像素字节数。 / English: Minimum CPU bytes per pixel for a retained ARGB int[]. */
    public static final long ARGB_BYTES_PER_PIXEL = 4L;

    /** 中文：无状态预算实例，供需要实例注入的调用方使用。 / English: Stateless budget instance for callers that prefer instance injection. */
    public static final TextureGenerationBudget DEFAULT = new TextureGenerationBudget();

    private TextureGenerationBudget() {
    }

    /**
     * 中文：创建一次规划期间使用的增量预算跟踪器；跟踪器不跨 generation 共享。
     * English: Creates an incremental budget tracker for one planning scope; trackers are never
     * shared across generations.
     */
    public Tracker newTracker() {
        return new Tracker();
    }

    /**
     * 中文：在像素数组复制前逐项保留 generation 预算，避免把整个数组复制完成后才发现超限。
     * English: Reserves generation budget item by item before a pixel-array copy, so an aggregate
     * limit is observed before the copy can increase the peak.
     */
    public static final class Tracker {
        private final LinkedHashMap<CandidateKey, Long> candidateCounts = new LinkedHashMap<>();
        private long candidateMethodSlots;
        private long spriteCount;
        private long pixelCount;
        private long cpuBytes;
        private long gpuBytes;

        private Tracker() {
        }

        /**
         * 中文：按已知像素数保留一个生成精灵；重复候选键会合并计数。
         * English: Reserves one generated sprite for a known pixel count; duplicate candidate keys
         * are merged into one count.
         */
        public void reserve(
                String engineId,
                String exactSurfaceStableKey,
                ConnectionMethod method,
                long pixels,
                long cpuBytesPerPixel) {
            CandidateSlots slot = new CandidateSlots(
                    engineId,
                    exactSurfaceStableKey,
                    method,
                    1L);
            if (pixels < 0L) {
                throw violation(
                        ViolationCode.PIXEL_COUNT_NEGATIVE,
                        pixels,
                        0L,
                        "pixels=" + pixels);
            }
            if (cpuBytesPerPixel < 0L) {
                throw violation(
                        ViolationCode.CPU_BYTES_NEGATIVE,
                        cpuBytesPerPixel,
                        0L,
                        "cpuBytesPerPixel=" + cpuBytesPerPixel);
            }
            if (cpuBytesPerPixel < ARGB_BYTES_PER_PIXEL) {
                throw violation(
                        ViolationCode.CPU_BYTES_UNDERESTIMATED,
                        cpuBytesPerPixel,
                        ARGB_BYTES_PER_PIXEL,
                        "cpuBytesPerPixel must include the retained ARGB int[]");
            }

            CandidateKey key = new CandidateKey(
                    slot.engineId(),
                    slot.exactSurfaceStableKey(),
                    slot.method());
            long previousKeyCount = candidateCounts.getOrDefault(key, 0L);
            long nextKeyCount = checkedAdd(
                    previousKeyCount,
                    1L,
                    ViolationCode.CANDIDATE_COUNT_OVERFLOW,
                    "candidate count overflow for " + key);
            if (nextKeyCount > MAX_CANDIDATE_METHOD_SLOTS) {
                throw violation(
                        ViolationCode.CANDIDATE_METHOD_SLOT_COUNT_EXCEEDED,
                        nextKeyCount,
                        MAX_CANDIDATE_METHOD_SLOTS,
                        "key=" + key);
            }

            long nextCandidateMethodSlots = checkedAdd(
                    candidateMethodSlots,
                    1L,
                    ViolationCode.CANDIDATE_SLOT_TOTAL_OVERFLOW,
                    "candidateMethodSlots total overflow");
            long nextSpriteCount = checkedAdd(
                    spriteCount,
                    1L,
                    ViolationCode.ARITHMETIC_OVERFLOW,
                    "spriteCount increment");
            requireNonNegativeAndWithin(
                    nextSpriteCount,
                    MAX_GENERATION_SPRITES,
                    ViolationCode.SPRITE_COUNT_EXCEEDED,
                    ViolationCode.SPRITE_COUNT_NEGATIVE,
                    "spriteCount=" + nextSpriteCount);

            long nextPixelCount = checkedAdd(
                    pixelCount,
                    pixels,
                    ViolationCode.ARITHMETIC_OVERFLOW,
                    "pixelCount increment");
            requireNonNegativeAndWithin(
                    nextPixelCount,
                    MAX_GENERATION_PIXELS,
                    ViolationCode.PIXEL_COUNT_EXCEEDED,
                    ViolationCode.PIXEL_COUNT_NEGATIVE,
                    "pixelCount=" + nextPixelCount);

            long cpuIncrement = checkedMultiply(
                    pixels,
                    cpuBytesPerPixel,
                    "pixels*cpuBytesPerPixel");
            long nextCpuBytes = checkedAdd(
                    cpuBytes,
                    cpuIncrement,
                    ViolationCode.ARITHMETIC_OVERFLOW,
                    "cpuBytes increment");
            requireNonNegativeAndWithin(
                    nextCpuBytes,
                    MAX_CPU_BYTES,
                    ViolationCode.CPU_BYTES_EXCEEDED,
                    ViolationCode.CPU_BYTES_NEGATIVE,
                    "cpuBytes=" + nextCpuBytes);

            long gpuIncrement = checkedMultiply(
                    pixels,
                    GPU_BYTES_PER_PIXEL,
                    "pixels*GPU_BYTES_PER_PIXEL");
            long nextGpuBytes = checkedAdd(
                    gpuBytes,
                    gpuIncrement,
                    ViolationCode.ARITHMETIC_OVERFLOW,
                    "gpuBytes increment");
            requireNonNegativeAndWithin(
                    nextGpuBytes,
                    MAX_GPU_BYTES,
                    ViolationCode.GPU_BYTES_EXCEEDED,
                    ViolationCode.GPU_BYTES_NEGATIVE,
                    "gpuBytes=" + nextGpuBytes);

            // 中文：所有检查通过后才提交增量，失败不会留下半个 reservation。
            // English: Commit the increment only after every check succeeds; failures leave no
            // partial reservation behind.
            candidateCounts.put(key, nextKeyCount);
            candidateMethodSlots = nextCandidateMethodSlots;
            spriteCount = nextSpriteCount;
            pixelCount = nextPixelCount;
            cpuBytes = nextCpuBytes;
            gpuBytes = nextGpuBytes;
        }

        /**
         * 中文：按图像宽高保留一个生成精灵，乘法使用 checked long 算术。
         * English: Reserves one generated sprite from sheet dimensions using checked long
         * multiplication.
         */
        public void reserve(
                String engineId,
                String exactSurfaceStableKey,
                ConnectionMethod method,
                long sheetWidth,
                long sheetHeight,
                long cpuBytesPerPixel) {
            long pixels = checkedMultiply(
                    sheetWidth,
                    sheetHeight,
                    "sheetWidth*sheetHeight");
            reserve(
                    engineId,
                    exactSurfaceStableKey,
                    method,
                    pixels,
                    cpuBytesPerPixel);
        }

        /**
         * 中文：读取当前增量状态并执行一次完整 immutable 校验；merge 阶段仍必须再次校验。
         * English: Reads the incremental state through one complete immutable validation; the
         * final merge must still validate independently.
         */
        public Usage usage() {
            ArrayList<CandidateSlots> candidates = new ArrayList<>(candidateCounts.size());
            for (Map.Entry<CandidateKey, Long> entry : candidateCounts.entrySet()) {
                CandidateKey key = entry.getKey();
                candidates.add(new CandidateSlots(
                        key.engineId(),
                        key.exactSurfaceStableKey(),
                        key.method(),
                        entry.getValue()));
            }
            return validate(
                    candidates,
                    spriteCount,
                    pixelCount,
                    cpuBytes,
                    gpuBytes);
        }

        /** 中文：返回已保留的生成精灵数。 / English: Returns the number of reserved generated sprites. */
        public long spriteCount() {
            return spriteCount;
        }

        /** 中文：返回已保留的像素数。 / English: Returns the number of reserved pixels. */
        public long pixelCount() {
            return pixelCount;
        }

        /** 中文：返回已保留的 CPU 字节数。 / English: Returns reserved CPU bytes. */
        public long cpuBytes() {
            return cpuBytes;
        }

        /** 中文：返回已保留的 GPU 字节数。 / English: Returns reserved GPU bytes. */
        public long gpuBytes() {
            return gpuBytes;
        }
    }

    /**
     * 中文：合并重复候选键并按固定顺序校验一个完整 generation。
     * English: Merges duplicate candidate keys and validates one complete generation in a fixed order.
     *
     * <p>中文：校验顺序固定为每键候选槽位、精灵/像素、CPU/GPU；重复键按首次出现顺序合并，
     * 合并和跨键总计均使用 checked long 算术。English: Validation order is per-key candidate
     * slots, sprites/pixels, then CPU/GPU; duplicate keys are merged in first-seen order, using
     * checked long arithmetic for merging and the cross-key total.</p>
     *
     * @param candidates aggregated candidate counts keyed by engine, exact surface, and concrete method
     * @param spriteCount total generated sprite count
     * @param pixelCount total generated pixel count
     * @param cpuBytes explicit peak CPU byte estimate supplied by the generation planner
     * @param gpuBytes explicit GPU base byte estimate supplied by the generation planner
     * @return an immutable, stable-order usage snapshot
     * @throws ViolationException when any frozen budget or input invariant is violated
     */
    public static Usage validate(
            List<CandidateSlots> candidates,
            long spriteCount,
            long pixelCount,
            long cpuBytes,
            long gpuBytes) {
        List<CandidateSlots> source = List.copyOf(
                Objects.requireNonNull(candidates, "candidates"));
        List<CandidateSlots> merged = mergeCandidates(source);
        validateCandidateSlotCounts(merged);

        long candidateMethodSlots = sumCandidateCounts(merged);
        requireNonNegative(
                candidateMethodSlots,
                ViolationCode.CANDIDATE_SLOT_TOTAL_NEGATIVE,
                "candidateMethodSlots=" + candidateMethodSlots);

        requireNonNegativeAndWithin(
                spriteCount,
                MAX_GENERATION_SPRITES,
                ViolationCode.SPRITE_COUNT_EXCEEDED,
                ViolationCode.SPRITE_COUNT_NEGATIVE,
                "spriteCount=" + spriteCount);
        requireNonNegativeAndWithin(
                pixelCount,
                MAX_GENERATION_PIXELS,
                ViolationCode.PIXEL_COUNT_EXCEEDED,
                ViolationCode.PIXEL_COUNT_NEGATIVE,
                "pixelCount=" + pixelCount);

        requireNonNegativeAndWithin(
                cpuBytes,
                MAX_CPU_BYTES,
                ViolationCode.CPU_BYTES_EXCEEDED,
                ViolationCode.CPU_BYTES_NEGATIVE,
                "cpuBytes=" + cpuBytes);

        requireNonNegativeAndWithin(
                gpuBytes,
                MAX_GPU_BYTES,
                ViolationCode.GPU_BYTES_EXCEEDED,
                ViolationCode.GPU_BYTES_NEGATIVE,
                "gpuBytes=" + gpuBytes);

        long minimumCpuBytes = checkedMultiply(
                pixelCount,
                ARGB_BYTES_PER_PIXEL,
                "pixelCount*ARGB_BYTES_PER_PIXEL");
        if (cpuBytes < minimumCpuBytes) {
            throw violation(
                    ViolationCode.CPU_BYTES_UNDERESTIMATED,
                    cpuBytes,
                    minimumCpuBytes,
                    "cpuBytes must include the retained ARGB int[]");
        }
        long minimumGpuBytes = checkedMultiply(
                pixelCount,
                GPU_BYTES_PER_PIXEL,
                "pixelCount*GPU_BYTES_PER_PIXEL");
        if (gpuBytes < minimumGpuBytes) {
            throw violation(
                    ViolationCode.GPU_BYTES_UNDERESTIMATED,
                    gpuBytes,
                    minimumGpuBytes,
                    "gpuBytes must include the 4-byte base estimate");
        }

        return new Usage(
                merged,
                candidateMethodSlots,
                spriteCount,
                pixelCount,
                cpuBytes,
                gpuBytes);
    }

    private static List<CandidateSlots> mergeCandidates(
            List<CandidateSlots> source) {
        LinkedHashMap<CandidateKey, Long> counts = new LinkedHashMap<>();
        for (CandidateSlots candidate : source) {
            Objects.requireNonNull(candidate, "candidates contains null");
            CandidateKey key = new CandidateKey(
                    candidate.engineId(),
                    candidate.exactSurfaceStableKey(),
                    candidate.method());
            Long previous = counts.get(key);
            long merged;
            try {
                merged = Math.addExact(
                        previous == null ? 0L : previous,
                        candidate.count());
            } catch (ArithmeticException exception) {
                throw violation(
                        ViolationCode.CANDIDATE_COUNT_OVERFLOW,
                        Long.MAX_VALUE,
                        Long.MAX_VALUE,
                        "candidate count overflow for " + key);
            }
            counts.put(key, merged);
        }

        ArrayList<CandidateSlots> merged = new ArrayList<>(counts.size());
        for (Map.Entry<CandidateKey, Long> entry : counts.entrySet()) {
            CandidateKey key = entry.getKey();
            merged.add(new CandidateSlots(
                    key.engineId(),
                    key.exactSurfaceStableKey(),
                    key.method(),
                    entry.getValue()));
        }
        return List.copyOf(merged);
    }

    private static long sumCandidateCounts(
            List<CandidateSlots> candidates) {
        long total = 0L;
        for (CandidateSlots candidate : candidates) {
            try {
                total = Math.addExact(total, candidate.count());
            } catch (ArithmeticException exception) {
                throw violation(
                        ViolationCode.CANDIDATE_SLOT_TOTAL_OVERFLOW,
                        Long.MAX_VALUE,
                        Long.MAX_VALUE,
                        "candidateMethodSlots total overflow");
            }
        }
        return total;
    }

    private static void requireNonNegativeAndWithin(
            long observed,
            long limit,
            ViolationCode exceededCode,
            ViolationCode negativeCode,
            String details) {
        if (observed < 0L) {
            throw violation(negativeCode, observed, 0L, details);
        }
        if (observed > limit) {
            throw violation(exceededCode, observed, limit, details);
        }
    }

    private static void requireNonNegative(
            long observed,
            ViolationCode code,
            String details) {
        if (observed < 0L) {
            throw violation(code, observed, 0L, details);
        }
    }

    private static void validateCandidateSlotCounts(
            List<CandidateSlots> candidates) {
        for (CandidateSlots candidate : candidates) {
            if (candidate.count() > MAX_CANDIDATE_METHOD_SLOTS) {
                throw violation(
                        ViolationCode.CANDIDATE_METHOD_SLOT_COUNT_EXCEEDED,
                        candidate.count(),
                        MAX_CANDIDATE_METHOD_SLOTS,
                        "key=" + new CandidateKey(
                                candidate.engineId(),
                                candidate.exactSurfaceStableKey(),
                                candidate.method()));
            }
        }
    }

    private static long checkedMultiply(
            long left,
            long right,
            String details) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException exception) {
            throw violation(
                    ViolationCode.ARITHMETIC_OVERFLOW,
                    Long.MAX_VALUE,
                    Long.MAX_VALUE,
                    details);
        }
    }

    private static long checkedAdd(
            long left,
            long right,
            ViolationCode code,
            String details) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            throw violation(
                    code,
                    Long.MAX_VALUE,
                    Long.MAX_VALUE,
                    details);
        }
    }

    private static ViolationException violation(
            ViolationCode code,
            long observed,
            long limit,
            String details) {
        return new ViolationException(
                new Violation(
                        code,
                        observed,
                        limit,
                        Map.of("context", Objects.requireNonNull(details, "details"))));
    }

    private record CandidateKey(
            String engineId,
            String exactSurfaceStableKey,
            ConnectionMethod method) {
    }

    /**
     * 中文：一个候选方法键的聚合槽位数；键语义严格为 engineId、exactSurfaceStableKey 和
     * concrete ConnectionMethod。English: Aggregated slot count for one candidate-method key;
     * the key semantics are exactly engineId, exactSurfaceStableKey, and concrete ConnectionMethod.
     */
    public record CandidateSlots(
            String engineId,
            String exactSurfaceStableKey,
            ConnectionMethod method,
            long count) {
        public CandidateSlots {
            Objects.requireNonNull(engineId, "engineId");
            if (engineId.isBlank()) {
                throw violation(
                        ViolationCode.ENGINE_ID_BLANK,
                        0L,
                        1L,
                        "engineId must not be blank");
            }
            Objects.requireNonNull(
                    exactSurfaceStableKey,
                    "exactSurfaceStableKey");
            if (exactSurfaceStableKey.isBlank()) {
                throw violation(
                        ViolationCode.SURFACE_KEY_BLANK,
                        0L,
                        1L,
                        "exactSurfaceStableKey must not be blank");
            }
            Objects.requireNonNull(method, "method");
            if (method == ConnectionMethod.AUTO) {
                throw violation(
                        ViolationCode.AUTO_METHOD_FORBIDDEN,
                        1L,
                        0L,
                        "method must be concrete");
            }
            if (count < 0L) {
                throw violation(
                        ViolationCode.CANDIDATE_COUNT_NEGATIVE,
                        count,
                        0L,
                        "candidate count must be nonnegative");
            }
            if (method == ConnectionMethod.NONE && count != 0L) {
                throw violation(
                        ViolationCode.NONE_METHOD_COUNT_NONZERO,
                        count,
                        0L,
                        "NONE has no generated slots");
            }
        }
    }

    /**
     * 中文：一次成功校验的不可变 generation 使用快照。English: Immutable usage snapshot for
     * one successfully validated generation.
     */
    public record Usage(
            List<CandidateSlots> candidateSlots,
            long candidateMethodSlots,
            long spriteCount,
            long pixelCount,
            long cpuBytes,
            long gpuBytes) {
        public Usage {
            candidateSlots = List.copyOf(
                    Objects.requireNonNull(candidateSlots, "candidateSlots"));
            validateUsageCandidates(candidateSlots);
            requireNonNegative(
                    candidateMethodSlots,
                    ViolationCode.CANDIDATE_SLOT_TOTAL_NEGATIVE,
                    "candidateMethodSlots=" + candidateMethodSlots);
            validateCandidateSlotCounts(candidateSlots);
            long computedCandidateSlots = sumCandidateCounts(candidateSlots);
            if (computedCandidateSlots != candidateMethodSlots) {
                throw violation(
                        ViolationCode.USAGE_CANDIDATE_TOTAL_MISMATCH,
                        candidateMethodSlots,
                        computedCandidateSlots,
                        "candidateMethodSlots does not match candidateSlots");
            }
            requireNonNegativeAndWithin(
                    spriteCount,
                    MAX_GENERATION_SPRITES,
                    ViolationCode.SPRITE_COUNT_EXCEEDED,
                    ViolationCode.SPRITE_COUNT_NEGATIVE,
                    "spriteCount=" + spriteCount);
            requireNonNegativeAndWithin(
                    pixelCount,
                    MAX_GENERATION_PIXELS,
                    ViolationCode.PIXEL_COUNT_EXCEEDED,
                    ViolationCode.PIXEL_COUNT_NEGATIVE,
                    "pixelCount=" + pixelCount);
            requireNonNegativeAndWithin(
                    cpuBytes,
                    MAX_CPU_BYTES,
                    ViolationCode.CPU_BYTES_EXCEEDED,
                    ViolationCode.CPU_BYTES_NEGATIVE,
                    "cpuBytes=" + cpuBytes);
            requireNonNegativeAndWithin(
                    gpuBytes,
                    MAX_GPU_BYTES,
                    ViolationCode.GPU_BYTES_EXCEEDED,
                    ViolationCode.GPU_BYTES_NEGATIVE,
                    "gpuBytes=" + gpuBytes);
            long minimumCpuBytes = checkedMultiply(
                    pixelCount,
                    ARGB_BYTES_PER_PIXEL,
                    "pixelCount*ARGB_BYTES_PER_PIXEL");
            if (cpuBytes < minimumCpuBytes) {
                throw violation(
                        ViolationCode.CPU_BYTES_UNDERESTIMATED,
                        cpuBytes,
                        minimumCpuBytes,
                        "cpuBytes must include the retained ARGB int[]");
            }
            long minimumGpuBytes = checkedMultiply(
                    pixelCount,
                    GPU_BYTES_PER_PIXEL,
                    "pixelCount*GPU_BYTES_PER_PIXEL");
            if (gpuBytes < minimumGpuBytes) {
                throw violation(
                        ViolationCode.GPU_BYTES_UNDERESTIMATED,
                        gpuBytes,
                        minimumGpuBytes,
                        "gpuBytes must include the 4-byte base estimate");
            }
        }

        /** 中文：返回跨全部键的观测总槽位数，不受单键 96 上限约束。 / English: Returns the cross-key observed slot total, which is not capped at 96. */
        public long totalCandidateSlots() {
            return candidateMethodSlots;
        }

        private static void validateUsageCandidates(
                List<CandidateSlots> candidates) {
            Set<CandidateKey> keys = new LinkedHashSet<>();
            for (CandidateSlots candidate : candidates) {
                Objects.requireNonNull(candidate, "candidateSlots contains null");
                if (!keys.add(new CandidateKey(
                        candidate.engineId(),
                        candidate.exactSurfaceStableKey(),
                        candidate.method()))) {
                    throw violation(
                            ViolationCode.DUPLICATE_CANDIDATE_KEY,
                            1L,
                            1L,
                            "Usage candidateSlots must already be merged");
                }
            }
        }
    }

    /** 中文：稳定的结构化预算诊断码。 / English: Stable structured budget diagnostic codes. */
    public enum ViolationCode {
        ENGINE_ID_BLANK,
        SURFACE_KEY_BLANK,
        AUTO_METHOD_FORBIDDEN,
        CANDIDATE_COUNT_NEGATIVE,
        NONE_METHOD_COUNT_NONZERO,
        CANDIDATE_COUNT_OVERFLOW,
        CANDIDATE_SLOT_TOTAL_NEGATIVE,
        CANDIDATE_METHOD_SLOT_COUNT_EXCEEDED,
        CANDIDATE_SLOT_TOTAL_OVERFLOW,
        DUPLICATE_CANDIDATE_KEY,
        USAGE_CANDIDATE_TOTAL_MISMATCH,
        SPRITE_COUNT_NEGATIVE,
        SPRITE_COUNT_EXCEEDED,
        PIXEL_COUNT_NEGATIVE,
        PIXEL_COUNT_EXCEEDED,
        CPU_BYTES_NEGATIVE,
        CPU_BYTES_EXCEEDED,
        CPU_BYTES_UNDERESTIMATED,
        GPU_BYTES_NEGATIVE,
        GPU_BYTES_EXCEEDED,
        GPU_BYTES_UNDERESTIMATED,
        ARITHMETIC_OVERFLOW
    }

    /**
     * 中文：不可变的 code/observed/limit/details 诊断。English: Immutable code/observed/limit/details diagnostic.
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

        public Violation(
                ViolationCode code,
                long observed,
                long limit) {
            this(code, observed, limit, Map.of());
        }
    }

    /**
     * 中文：携带稳定结构化诊断、同时兼容 IllegalArgumentException 调用链的异常。
     * English: Exception carrying stable structured diagnostics while remaining an
     * IllegalArgumentException for existing call chains.
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

        public ViolationCode code() {
            return violation.code();
        }

        public long observed() {
            return violation.observed();
        }

        public long limit() {
            return violation.limit();
        }

        public Map<String, String> details() {
            return violation.details();
        }

        private static String format(Violation violation) {
            StringBuilder message = new StringBuilder(
                    "TEXTURE_GENERATION_BUDGET_VIOLATION:")
                    .append(violation.code())
                    .append(":observed=")
                    .append(violation.observed())
                    .append(":limit=")
                    .append(violation.limit())
                    .append(":details=");
            boolean first = true;
            for (Map.Entry<String, String> detail : violation.details().entrySet()) {
                if (!first) {
                    message.append(';');
                }
                first = false;
                message.append(detail.getKey()).append('=').append(detail.getValue());
            }
            return message.toString();
        }
    }
}
