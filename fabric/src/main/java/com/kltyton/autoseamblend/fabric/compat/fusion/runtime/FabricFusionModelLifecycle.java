package com.kltyton.autoseamblend.fabric.compat.fusion.runtime;

import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.PreparedSurfaceMethods;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：为已准备 Fusion 表面的状态模型安装 AutoBlend 动态包装。
 *
 * <p>English: Installs the AutoBlend dynamic wrapper for states with prepared
 * Fusion surfaces.
 */
public final class FabricFusionModelLifecycle {
    /**
     * 中文：1.21.1 AfterBake 上下文没有 state()；用原版
     * {@link BlockModelShaper#stateToModelLocation(BlockState)} 的逆映射从
     * topLevelId 恢复 BlockState（一次性构建，仅当 Fusion 切片加载时初始化）。
     *
     * <p>English: The 1.21.1 AfterBake context has no state(); the inverse of
     * {@link BlockModelShaper#stateToModelLocation(BlockState)} restores the
     * BlockState from topLevelId (built once, only when this Fusion slice is
     * loaded).
     */
    private static final Map<ModelResourceLocation, BlockState> STATE_BY_MODEL =
            buildStateByModel();

    /**
     * 中文：按重载代次缓存的候选 state 集合；只在代次变化时重建一次，避免每次烘焙扫描
     * 整张预缝合方法表。单槽缓存：同一时刻只有一个 pending 代次。
     *
     * English: Per-reload-generation cache of candidate states; rebuilt once per new
     * generation so baking never rescans the whole pre-stitch method table. Single slot:
     * only one pending generation exists at a time.
     */
    private static final AtomicReference<CandidateStates> CANDIDATES =
            new AtomicReference<>();

    private record CandidateStates(
            long generation,
            Set<BlockState> states) {}

    private FabricFusionModelLifecycle() {}

    public static BakedModel wrap(
            BakedModel model,
            ModelModifier.AfterBake.Context context) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(context, "context");
        // 中文：Fabric 的 missing-model bake 允许 AfterBake.Context.topLevelId() 为 null；
        // 此时没有可查询的方块状态，必须原样返回 delegate，否则对不可变 Map.copyOf 的
        // STATE_BY_MODEL 执行 get(null) 会抛 NullPointerException（"pk is null"）。
        // English: Fabric's missing-model bake allows AfterBake.Context.topLevelId() to be
        // null; with no queryable block state the delegate must pass through unchanged, or
        // get(null) on the immutable Map.copyOf STATE_BY_MODEL throws NullPointerException
        // ("pk is null").
        ModelResourceLocation topLevelId =
                context.topLevelId();
        if (topLevelId == null) {
            return model;
        }
        BlockState state =
                STATE_BY_MODEL.get(
                        topLevelId);
        if (state == null
                || !shouldDecorate(state)) {
            return model;
        }
        return new FabricFusionConnectedBlockStateModel(
                model,
                state);
    }

    /**
     * 中文：首次烘焙时 surfaces 尚未发布（pending 已 prepare、modelFacts 未 stage），
     * modelDecorationSurfaces() 回退到 bootstrap 空代次；此时用同代次 pending 的预缝合
     * 方法表判定候选，否则首会话没有任何方块会被 Fusion 包装（与 Fabric Athena 2026-08-08
     * 零连接纹理缺陷同型）。active surfaces 已发布时仍以发布快照精确判定；无 pending 候选
     * 也不包装，绝不无条件包装所有方块。
     *
     * English: During the first bake surfaces are not published yet (pending prepared but
     * modelFacts unstaged), so modelDecorationSurfaces() falls back to the empty bootstrap
     * generation; the same-reload pending pre-stitch method table then gates candidates, or
     * no block is ever wrapped in the first session (same shape as the 2026-08-08 Fabric
     * Athena zero-connected-texture defect). Once surfaces are published the published
     * snapshot stays the precise gate; blocks without a pending candidate are never wrapped,
     * so wrapping is never unconditional.
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
        CandidateStates cached = CANDIDATES.get();
        if (cached == null
                || cached.generation() != generation) {
            synchronized (FabricFusionModelLifecycle.class) {
                cached = CANDIDATES.get();
                if (cached == null
                        || cached.generation()
                                != generation) {
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
        IdentityHashMap<BlockState, Boolean> candidates =
                new IdentityHashMap<>();
        preparedMethods.methods()
                .forEach((key, method) -> {
                    if (method.decision()
                            .resolvedMethod()
                            .filter(resolved ->
                                    resolved
                                            != ConnectionMethod.NONE)
                            .isPresent()) {
                        candidates.put(
                                key.state(),
                                Boolean.TRUE);
                    }
                });
        preparedMethods.autoMethods()
                .forEach((block, auto) ->
                        candidates.put(
                                auto.state(),
                                Boolean.TRUE));
        return new CandidateStates(
                generation,
                candidates.keySet());
    }

    private static Map<ModelResourceLocation, BlockState>
            buildStateByModel() {
        HashMap<ModelResourceLocation, BlockState> states =
                new HashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            for (BlockState state
                    : block.getStateDefinition()
                            .getPossibleStates()) {
                states.put(
                        BlockModelShaper
                                .stateToModelLocation(
                                        state),
                        state);
            }
        }
        return Map.copyOf(states);
    }
}
