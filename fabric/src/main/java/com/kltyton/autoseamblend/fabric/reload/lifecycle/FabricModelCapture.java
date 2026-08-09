package com.kltyton.autoseamblend.fabric.reload.lifecycle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：在引擎包装器介入前按 reload 会话捕获基础烘焙模型，供根 apply 计算所有权与表面。
 *
 * English: Captures base baked models per reload session before engine wrappers
 * run, so the root apply can compute ownership and surfaces from the same
 * pre-wrapper models NeoForge observes.
 */
public final class FabricModelCapture {
    private static final AtomicLong SESSION = new AtomicLong();
    private static volatile long activeSession = -1;
    private static volatile Map<BlockState, BakedModel> baseModels =
            Map.of();

    private FabricModelCapture() {}

    public static synchronized long begin() {
        long session = SESSION.incrementAndGet();
        activeSession = session;
        baseModels = new LinkedHashMap<>();
        return session;
    }

    public static synchronized void capture(
            long session,
            BlockState state,
            BakedModel model) {
        if (session != activeSession) {
            return;
        }
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(model, "model");
        baseModels.put(state, model);
    }

    public static synchronized Map<BlockState, BakedModel>
            latestBaseModels() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(baseModels));
    }
}
