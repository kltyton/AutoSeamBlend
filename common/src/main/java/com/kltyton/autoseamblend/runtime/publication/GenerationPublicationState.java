package com.kltyton.autoseamblend.runtime.publication;

import java.util.Objects;

/**
 * 中文：跨 Loader 共用的资源代次版本状态机；Loader 只持有 payload、锁和原生捕获值。
 * English: Shared resource-generation version state machine; loaders retain only payloads, locks,
 * and native capture values.
 *
 * <p>This type deliberately owns no renderer or resource payload. It centralizes the version
 * pairing, stale-token rejection, selector-revision transition, and staged-generation readiness
 * rules used by both loaders.
 */
public final class GenerationPublicationState {
    private GenerationPublicationState() {}

    /**
     * 中文：从当前根代次创建下一次资源重载 token。
     * English: Creates the next resource-reload token from the current root generation.
     */
    public static Token begin(
            Marker current,
            long ordinal,
            String reason) {
        Objects.requireNonNull(current, "current");
        if (ordinal <= 0) {
            throw new IllegalArgumentException("reload ordinal must be positive");
        }
        requireText(reason, "reason");
        return new Token(
                ordinal,
                current.generation(),
                current.selectorRevision(),
                Math.addExact(current.generation(), 1),
                reason);
    }

    /**
     * 中文：确认候选代次与 token 配对，并返回可发布的新根版本。
     * English: Confirms candidate/token pairing and returns the new root version to publish.
     */
    public static Marker publish(
            Marker current,
            Token token,
            Marker candidate) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(candidate, "candidate");
        if (current.generation() != token.expectedGeneration()
                || current.selectorRevision() != token.expectedSelectorRevision()) {
            throw new IllegalStateException("stale reload token rejected before publication");
        }
        if (candidate.generation() != token.targetGeneration()
                || candidate.selectorRevision() != token.expectedSelectorRevision()) {
            throw new IllegalArgumentException("candidate generation differs from reload token");
        }
        return candidate;
    }

    /**
     * 中文：确认无 token 的同步阶段只提交严格的下一代版本。
     * English: Confirms that a tokenless synchronous phase commits exactly the next generation.
     */
    public static Marker commitNext(
            Marker current,
            long candidateGeneration,
            long candidateSelectorRevision) {
        Objects.requireNonNull(current, "current");
        if (candidateGeneration != Math.addExact(current.generation(), 1)) {
            throw new IllegalStateException("candidate generation is not the next generation");
        }
        if (candidateSelectorRevision < 0) {
            throw new IllegalArgumentException("candidate selector revision must be non-negative");
        }
        return new Marker(candidateGeneration, candidateSelectorRevision);
    }

    public static long nextGeneration(Marker current) {
        Objects.requireNonNull(current, "current");
        return Math.addExact(current.generation(), 1);
    }

    /**
     * 中文：选择器单独编译可从下一代快照开始，但发布时仍回写当前资源代次。
     * English: Selector-only compilation may start from the next-generation snapshot while
     * publication still aligns the result to the active resource generation.
     */
    public static Marker alignSelectorCandidate(
            Marker current,
            long candidateGeneration) {
        Objects.requireNonNull(current, "current");
        long nextGeneration = Math.addExact(current.generation(), 1);
        if (candidateGeneration != current.generation()
                && candidateGeneration != nextGeneration) {
            throw new IllegalStateException(
                    "selector candidate generation is not active or next generation");
        }
        return current;
    }

    /**
     * 中文：推进同一资源代次的选择器 revision，拒绝 generation/revision 漂移。
     * English: Advances a selector revision for the same resource generation, rejecting generation
     * or revision drift.
     */
    public static Marker advanceSelectorRevision(
            Marker current,
            long generation,
            long expectedSelectorRevision) {
        Objects.requireNonNull(current, "current");
        if (current.generation() != generation
                || current.selectorRevision() != expectedSelectorRevision) {
            throw new IllegalStateException("selector revision update is stale");
        }
        if (current.selectorRevision() == Long.MAX_VALUE) {
            throw new IllegalStateException("selector publication revision exhausted");
        }
        return new Marker(current.generation(), current.selectorRevision() + 1);
    }

    public static void requireGeneration(
            long actual,
            long expected,
            String context) {
        requireText(context, "context");
        if (actual != expected) {
            throw new IllegalArgumentException(context + " generation differs");
        }
    }

    /**
     * 中文：统一验证工作台捕获的资源代次仍是当前代次。
     * English: Uniformly verifies that a workbench-captured resource generation is still current.
     */
    public static void requireCurrentWorkbenchGeneration(
            long capturedGeneration,
            long currentGeneration) {
        if (capturedGeneration < 0 || capturedGeneration != currentGeneration) {
            throw new IllegalStateException("WORKBENCH_GENERATION_STALE");
        }
    }

    public record Marker(long generation, long selectorRevision) {
        public Marker {
            if (generation < 0 || selectorRevision < 0) {
                throw new IllegalArgumentException(
                        "generation and selector revision must be non-negative");
            }
        }
    }

    public record Token(
            long ordinal,
            long expectedGeneration,
            long expectedSelectorRevision,
            long targetGeneration,
            String reason) {
        public Token {
            if (ordinal <= 0
                    || expectedGeneration < 0
                    || expectedSelectorRevision < 0
                    || targetGeneration != Math.addExact(expectedGeneration, 1)) {
                throw new IllegalArgumentException("invalid reload token sequence");
            }
            requireText(reason, "reason");
        }
    }

    /**
     * 中文：记录 NeoForge 多阶段候选所需的两个原生完成屏障。
     * English: Records the two native completion barriers required by a NeoForge staged candidate.
     */
    public record StagedParts(long generation, boolean modelFactsReady, boolean resolvedSpritesReady) {
        public StagedParts {
            if (generation < 0) {
                throw new IllegalArgumentException("staged generation must be non-negative");
            }
        }

        public StagedParts withModelFacts() {
            return new StagedParts(generation, true, resolvedSpritesReady);
        }

        public StagedParts withResolvedSprites() {
            return new StagedParts(generation, modelFactsReady, true);
        }

        public boolean complete() {
            return modelFactsReady && resolvedSpritesReady;
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
