package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane.FabricAthenaGeneratedPaneModelFactory;
import com.kltyton.autoseamblend.fabric.reload.lifecycle.FabricModelLifecycle;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.PreparedSurfaceMethods;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：共享表面快照发布后装饰非原生烘焙模型。
 *
 * English: Decorates non-native baked models after the shared surface snapshot
 * was published.
 */
public final class FabricAthenaModelLifecycle {
    private FabricAthenaModelLifecycle() {}

    /**
     * 中文：按重载代次缓存的候选 state 集合；只在代次变化时重建一次，避免每次烘焙
     * 扫描整张预缝合方法表。单槽缓存：同一时刻只有一个 pending 代次。
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

    public static BakedModel wrap(
            BakedModel model,
            ModelModifier.AfterBake.Context context) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(context, "context");
        BlockState state =
                FabricModelLifecycle.resolveState(context);
        if (state == null) {
            return model;
        }
        if (!shouldDecorate(state)) {
            return model;
        }
        // 中文：与已验收 NeoForge AthenaModelLifecycle 同序——pane 候选先走原生
        // AthenaGeneratedPaneModelFactory（Fabric 等价路径），成功则返回原生 pane 模型，
        // 否则回退通用 FabricAthenaConnectedBlockStateModel；pane 薄面/正反面/边面/端盖
        // 必须保留 Athena 原生几何。
        // English: Same order as the accepted NeoForge AthenaModelLifecycle -- pane
        // candidates first try the native AthenaGeneratedPaneModelFactory (Fabric
        // equivalent), returning the native pane model on success and falling back to the
        // generic FabricAthenaConnectedBlockStateModel otherwise; pane strips, front/back
        // faces, edge faces, and caps must keep Athena's native geometry.
        MinecraftSurfaceCatalog.Snapshot surfaces =
                ReloadPublication.modelDecorationSurfaces();
        ReloadPublication.Generation generation =
                ReloadPublication.pendingPreparation()
                        .filter(candidate ->
                                candidate.generation()
                                        == surfaces.generation())
                        .orElseGet(
                                ReloadPublication
                                        ::current);
        BakedModel paneModel =
                FabricAthenaGeneratedPaneModelFactory
                        .create(
                                context.textureGetter(),
                                generation,
                                surfaces,
                                state,
                                model)
                        .orElse(null);
        if (paneModel != null) {
            return paneModel;
        }
        return new FabricAthenaConnectedBlockStateModel(
                model,
                state);
    }

    /**
     * 中文：首次烘焙时 surfaces 尚未发布（pending 已 prepare、modelFacts 未 stage），
     * modelDecorationSurfaces() 回退到 bootstrap 空代次；此时用同代次 pending 的预缝合
     * 方法表判定候选，否则没有任何方块会被包装（2026-08-08 零连接纹理缺陷）。active
     * surfaces 已发布时仍以发布快照精确判定；无 pending 候选也不包装，绝不无条件包装
     * 所有方块。
     *
     * English: During the first bake surfaces are not published yet (pending prepared but
     * modelFacts unstaged), so modelDecorationSurfaces() falls back to the empty bootstrap
     * generation; the same-reload pending pre-stitch method table then gates candidates,
     * otherwise no block is ever wrapped (2026-08-08 zero-connected-texture defect). Once
     * surfaces are published the published snapshot stays the precise gate; blocks without a
     * pending candidate are never wrapped, so wrapping is never unconditional.
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
            synchronized (FabricAthenaModelLifecycle.class) {
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
}
