package com.kltyton.autoseamblend.runtime.publication;

import java.util.Objects;
import java.util.Optional;

/**
 * 中文：跨 Loader 的候选快照暂存与双屏障状态；具体模型/精灵 payload 作为泛型保存。
 *
 * English: Loader-neutral staged candidate and two-barrier state; concrete model/sprite payloads
 * are retained only as generic values.
 */
public record GenerationStaging<C, M, R>(
        C candidate,
        Optional<M> modelFacts,
        Optional<R> resolvedSprites,
        GenerationPublicationState.StagedParts stages) {
    public GenerationStaging {
        Objects.requireNonNull(candidate, "candidate");
        modelFacts = Objects.requireNonNull(modelFacts, "modelFacts");
        resolvedSprites = Objects.requireNonNull(resolvedSprites, "resolvedSprites");
        Objects.requireNonNull(stages, "stages");
        if (stages.modelFactsReady() != modelFacts.isPresent()
                || stages.resolvedSpritesReady() != resolvedSprites.isPresent()) {
            throw new IllegalArgumentException(
                    "staged readiness flags must match staged payload presence");
        }
    }

    public static <C, M, R> GenerationStaging<C, M, R> empty(
            C candidate,
            long generation) {
        return new GenerationStaging<>(
                candidate,
                Optional.empty(),
                Optional.empty(),
                new GenerationPublicationState.StagedParts(generation, false, false));
    }

    public GenerationStaging<C, M, R> withModelFacts(M facts) {
        return new GenerationStaging<>(
                candidate,
                Optional.of(Objects.requireNonNull(facts, "facts")),
                resolvedSprites,
                stages.withModelFacts());
    }

    public GenerationStaging<C, M, R> withResolvedSprites(R sprites) {
        return new GenerationStaging<>(
                candidate,
                modelFacts,
                Optional.of(Objects.requireNonNull(sprites, "sprites")),
                stages.withResolvedSprites());
    }

    public boolean complete() {
        return stages.complete();
    }
}
