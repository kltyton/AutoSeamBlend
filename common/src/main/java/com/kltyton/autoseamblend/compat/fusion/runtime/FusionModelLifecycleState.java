package com.kltyton.autoseamblend.compat.fusion.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：保存跨 loader 共用的 Fusion bake token、基础模型、预览模型和 retained 代次状态。
 * Loader 只负责 token 类型转换、事件时机和模型包装。
 *
 * English: Stores the Fusion bake-token, base-model, preview-model, and retained-generation state
 * shared by Loaders. Loaders only adapt token types, event timing, and model wrappers.
 */
public final class FusionModelLifecycleState {
    private final Map<Long, BakeSlot> stagedByToken = new LinkedHashMap<>();
    private final Map<Long, Long> retainedTokenByGeneration = new LinkedHashMap<>();

    public synchronized void retainBaseModel(
            long tokenOrdinal,
            long generation,
            BlockState state,
            BlockStateModel model) {
        slot(tokenOrdinal, generation).baseModels().put(
                Objects.requireNonNull(state, "state"),
                Objects.requireNonNull(model, "model"));
    }

    public synchronized Optional<BlockStateModel> baseModel(
            long tokenOrdinal,
            long generation,
            BlockState state) {
        BakeSlot slot = stagedByToken.get(tokenOrdinal);
        if (slot == null || slot.generation() != generation) {
            return Optional.empty();
        }
        return Optional.ofNullable(slot.baseModels().get(Objects.requireNonNull(state, "state")));
    }

    public synchronized void putPreviewModel(
            long tokenOrdinal,
            long generation,
            BlockState state,
            BlockStateModel model) {
        slot(tokenOrdinal, generation).previewModels().put(
                Objects.requireNonNull(state, "state"),
                Objects.requireNonNull(model, "model"));
    }

    public synchronized void putPreviewModelIfAbsent(
            long tokenOrdinal,
            long generation,
            BlockState state,
            BlockStateModel model) {
        slot(tokenOrdinal, generation).previewModels().putIfAbsent(
                Objects.requireNonNull(state, "state"),
                Objects.requireNonNull(model, "model"));
    }

    /**
     * 中文：通过 retained 代次读取预览模型，若没有预览则回退到同代基础模型。
     * English: Reads a preview model through the retained generation, falling back to its
     * same-generation base model when no preview exists.
     */
    public synchronized Optional<BlockStateModel> previewModel(
            long generation,
            BlockState state) {
        Long tokenOrdinal = retainedTokenByGeneration.get(generation);
        BakeSlot slot = tokenOrdinal == null ? null : stagedByToken.get(tokenOrdinal);
        if (slot == null || slot.generation() != generation) {
            return Optional.empty();
        }
        BlockState key = Objects.requireNonNull(state, "state");
        BlockStateModel model = slot.previewModels().get(key);
        if (model == null) {
            model = slot.baseModels().get(key);
        }
        return Optional.ofNullable(model);
    }

    /**
     * 中文：仅保留与目标代次一致的 stage，并清理早于当前代次的 retained 绑定。
     * English: Retains only a stage matching the target generation and removes older retained
     * bindings before publishing the new candidate.
     */
    public synchronized boolean retain(
            long tokenOrdinal,
            long targetGeneration,
            long currentGeneration) {
        BakeSlot slot = stagedByToken.get(tokenOrdinal);
        if (slot == null || slot.generation() != targetGeneration) {
            return false;
        }
        retainedTokenByGeneration.keySet().removeIf(value -> value < currentGeneration);
        retainedTokenByGeneration.put(targetGeneration, tokenOrdinal);
        return true;
    }

    public synchronized boolean prepared(long tokenOrdinal, long targetGeneration) {
        Long retained = retainedTokenByGeneration.get(targetGeneration);
        BakeSlot slot = stagedByToken.get(tokenOrdinal);
        return retained != null
                && retained == tokenOrdinal
                && slot != null
                && slot.generation() == targetGeneration;
    }

    /** 中文：撤销候选绑定但保留 token stage。 / English: Remove a candidate binding while retaining the token stage. */
    public synchronized void unretain(long tokenOrdinal, long targetGeneration) {
        Long retained = retainedTokenByGeneration.get(targetGeneration);
        if (retained != null && retained == tokenOrdinal) {
            retainedTokenByGeneration.remove(targetGeneration);
        }
    }

    /**
     * 中文：失败或未发布 token 的清理规则；已发布代次的 retained 模型继续存活。
     * English: Discards failed or unpublished tokens while preserving models retained by the
     * currently published generation.
     */
    public synchronized void discard(
            long tokenOrdinal,
            long targetGeneration,
            long currentGeneration,
            boolean published) {
        Long retained = retainedTokenByGeneration.get(targetGeneration);
        if (retained != null && retained == tokenOrdinal && !published) {
            retainedTokenByGeneration.remove(targetGeneration);
        }
        if (!published || retained == null || retained != tokenOrdinal) {
            stagedByToken.remove(tokenOrdinal);
        }
    }

    /** 中文：publication 后只保留当前代次。 / English: Keep only the current generation after publication. */
    public synchronized void onPublished(long currentGeneration) {
        Long currentToken = retainedTokenByGeneration.get(currentGeneration);
        stagedByToken.entrySet().removeIf(entry ->
                currentToken == null
                        || !entry.getKey().equals(currentToken)
                        || entry.getValue().generation() != currentGeneration);
        retainedTokenByGeneration.entrySet().removeIf(entry ->
                currentToken == null
                        || entry.getKey() != currentGeneration
                        || !entry.getValue().equals(currentToken));
    }

    public synchronized void purgeUnselected() {
        stagedByToken.clear();
        retainedTokenByGeneration.clear();
    }

    private BakeSlot slot(long tokenOrdinal, long generation) {
        return stagedByToken.compute(tokenOrdinal, (ordinal, existing) -> {
            if (existing != null && existing.generation() != generation) {
                throw new IllegalStateException("Fusion bake token changed target generation");
            }
            return existing == null
                    ? new BakeSlot(generation, new LinkedHashMap<>(), new LinkedHashMap<>())
                    : existing;
        });
    }

    private record BakeSlot(
            long generation,
            Map<BlockState, BlockStateModel> baseModels,
            Map<BlockState, BlockStateModel> previewModels) {
    }
}
