package com.kltyton.autoseamblend.reload.surface;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：保存最近一次首轮表面准备得到的 model-id -> BlockState 索引，供 Fusion 1.20.1
 * modifier 所有权目录把模型定位展开成方块状态。
 *
 * English: Holds the latest first-pass surface-preparation model-id -> BlockState index so the
 * Fusion 1.20.1 modifier ownership catalog can expand model locations into block states.
 */
public final class StateModelIndex {
    private static final AtomicReference<Map<ResourceLocation, Set<BlockState>>>
            ACTIVE = new AtomicReference<>(Map.of());

    private StateModelIndex() {
    }

    public static void publish(
            Map<ResourceLocation, Set<BlockState>> byModelId) {
        Objects.requireNonNull(byModelId, "byModelId");
        LinkedHashMap<ResourceLocation, Set<BlockState>> copy =
                new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Set<BlockState>> entry
                : byModelId.entrySet()) {
            copy.put(
                    Objects.requireNonNull(entry.getKey(), "model id"),
                    Set.copyOf(Objects.requireNonNull(
                            entry.getValue(),
                            "states")));
        }
        ACTIVE.set(Collections.unmodifiableMap(copy));
    }

    public static Set<BlockState> statesForModel(
            ResourceLocation modelId) {
        return ACTIVE.get().getOrDefault(
                Objects.requireNonNull(modelId, "modelId"),
                Set.of());
    }

    /** 中文：把 state -> modelIds 翻转成 modelId -> states。 / English: Inverts state -> modelIds into modelId -> states. */
    public static Map<ResourceLocation, Set<BlockState>> invert(
            Map<BlockState, ? extends Iterable<ResourceLocation>> stateModels) {
        LinkedHashMap<ResourceLocation, LinkedHashSet<BlockState>> inverted =
                new LinkedHashMap<>();
        for (Map.Entry<BlockState, ? extends Iterable<ResourceLocation>> entry
                : Objects.requireNonNull(stateModels, "stateModels").entrySet()) {
            for (ResourceLocation modelId : entry.getValue()) {
                inverted.computeIfAbsent(
                                Objects.requireNonNull(modelId, "model id"),
                                ignored -> new LinkedHashSet<>())
                        .add(Objects.requireNonNull(
                                entry.getKey(),
                                "state"));
            }
        }
        LinkedHashMap<ResourceLocation, Set<BlockState>> result =
                new LinkedHashMap<>();
        inverted.forEach((modelId, states) ->
                result.put(modelId, Set.copyOf(states)));
        return result;
    }
}
