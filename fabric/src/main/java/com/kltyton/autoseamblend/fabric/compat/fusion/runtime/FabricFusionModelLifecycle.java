package com.kltyton.autoseamblend.fabric.compat.fusion.runtime;

import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.PreparedModelDecorationCandidates;
import com.kltyton.autoseamblend.runtime.surface.PreparedSurfaceMethods;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：为已准备 Fusion 表面的状态模型安装 AutoBlend 动态包装；active surfaces 优先，
 * 首烤回退同代 pending 候选。
 *
 * English:
 * Installs the AutoBlend dynamic wrapper for states with prepared Fusion surfaces. Active
 * surfaces win; the first bake falls back to same-generation pending candidates.
 */
public final class FabricFusionModelLifecycle {
    /**
     * 中文：按重载代次与 reload token 缓存的候选 state 集合；只在代次变化时重建一次，
     * 避免每次烘焙扫描整张预缝合方法表。单槽缓存：同一时刻只有一个 pending 代次。
     *
     * English:
     * Per-reload-generation/reload-token cache of candidate states; rebuilt once per new
     * generation so baking never rescans the whole pre-stitch method table. Single slot: only
     * one pending generation exists at a time.
     */
    private static final AtomicReference<CandidateStates> CANDIDATES =
            new AtomicReference<>();

    private record CandidateStates(
            long generation,
            String reloadToken,
            Set<BlockState> states) {}

    private FabricFusionModelLifecycle() {}

    public static BlockStateModel wrap(
            BlockStateModel model,
            ModelModifier.AfterBakeBlock.Context context) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(context, "context");
        BlockState state = context.state();
        if (!shouldDecorate(state)) {
            return model;
        }
        return new FabricFusionConnectedBlockStateModel(
                model,
                state);
    }

    /**
     * 中文：active surfaces 命中必须包装；否则用同代 pending 的预缝合方法表判定候选，
     * 无 pending 候选与 NONE 解析候选一律透传，绝不无条件包装所有方块。
     *
     * English:
     * Active surfaces hits must wrap; otherwise the same-generation pending pre-stitch method
     * table gates candidates, while states without a pending candidate or with a NONE-resolved
     * candidate pass through, so wrapping is never unconditional.
     */
    static boolean shouldDecorate(BlockState state) {
        MinecraftSurfaceCatalog.Snapshot active =
                ReloadPublication.current().surfaces();
        if (active.states().containsKey(state)) {
            return true;
        }
        Optional<ReloadPublication.Generation> pending =
                ReloadPublication.pendingPreparation();
        if (pending.isEmpty()) {
            return false;
        }
        ReloadPublication.Generation pendingGeneration =
                pending.orElseThrow();
        long generation =
                pendingGeneration.generation();
        String reloadToken =
                pendingGeneration.preparedMethods()
                        .reloadToken();
        CandidateStates cached = CANDIDATES.get();
        if (cached == null
                || cached.generation() != generation
                || !cached.reloadToken()
                        .equals(reloadToken)) {
            synchronized (FabricFusionModelLifecycle.class) {
                cached = CANDIDATES.get();
                if (cached == null
                        || cached.generation()
                                != generation
                        || !cached.reloadToken()
                                .equals(reloadToken)) {
                    cached = computeCandidates(
                            pendingGeneration
                                    .preparedMethods(),
                            generation);
                    CANDIDATES.set(cached);
                }
            }
        }
        return cached.states().contains(state);
    }

    private static CandidateStates computeCandidates(
            PreparedSurfaceMethods.Snapshot preparedMethods,
            long generation) {
        return new CandidateStates(
                generation,
                preparedMethods.reloadToken(),
                PreparedModelDecorationCandidates.states(preparedMethods));
    }
}
