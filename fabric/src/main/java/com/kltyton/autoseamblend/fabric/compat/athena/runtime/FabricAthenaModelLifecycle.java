package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane.FabricAthenaGeneratedPaneModelFactory;
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
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：共享表面快照发布后装饰非原生烘焙模型。
 *
 * English: Decorates non-native baked models after the shared surface snapshot
 * was published.
 */
public final class FabricAthenaModelLifecycle {
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
            String reloadToken,
            Set<BlockState> states) {}

    private FabricAthenaModelLifecycle() {}

    public static BlockStateModel wrap(
            BlockStateModel model,
            ModelModifier.AfterBakeBlock.Context context) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(context, "context");
        BlockState state = context.state();
        if (!shouldDecorate(state)) {
            return model;
        }
        // 中文：pane 候选先经 FabricAthenaGeneratedPaneModelFactory 进入 Athena 原生玻璃板
        // 生命周期；工厂内部以 IronBarsBlock/非原生模型/代次对齐门控，返回空时回退本通用
        // 包装器（普通方块保持既有 wrapper）。surfaces 取自 modelDecorationSurfaces()
        // （pending modelFacts 已 stage 时用同代次表面，否则退回已发布快照），generation
        // 优先取与 surfaces 同代次的 pending 候选，保证首烤与后续烘焙使用同代 surfaces。
        // English: Pane candidates first enter Athena's native pane lifecycle through
        // FabricAthenaGeneratedPaneModelFactory; the factory gates on IronBarsBlock /
        // non-native models / generation alignment and this generic wrapper stays the
        // fallback (regular blocks keep the existing wrapper). Surfaces come from
        // modelDecorationSurfaces() (same-generation surfaces once pending modelFacts are
        // staged, otherwise the published snapshot), and the generation prefers the pending
        // candidate aligned with the surfaces generation, so first bake and later bakes use
        // same-generation surfaces.
        MinecraftSurfaceCatalog.Snapshot surfaces =
                ReloadPublication
                        .modelDecorationSurfaces();
        ReloadPublication.Generation generation =
                ReloadPublication
                        .pendingPreparation()
                        .filter(candidate ->
                                candidate.generation()
                                        == surfaces.generation())
                        .orElseGet(
                                ReloadPublication::current);
        return FabricAthenaGeneratedPaneModelFactory
                .create(
                        context.baker().materials(),
                        generation,
                        surfaces,
                        state,
                        model)
                .orElseGet(() ->
                        new FabricAthenaConnectedBlockStateModel(
                                model,
                                state));
    }

    /**
     * 中文：首次烘焙时 surfaces 尚未发布（pending 已 prepare、modelFacts 未 stage），
     * modelDecorationSurfaces() 回退到 bootstrap 空代次；此时用同代次 pending 的预缝合
     * 方法表判定候选，否则没有任何方块会被包装（26.1.2 Fabric Athena 零连接纹理缺陷）。
     * active surfaces 已发布时仍以发布快照精确判定；无 pending 候选也不包装，绝不无条件
     * 包装所有方块。
     *
     * English: During the first bake surfaces are not published yet (pending prepared but
     * modelFacts unstaged), so modelDecorationSurfaces() falls back to the empty bootstrap
     * generation; the same-reload pending pre-stitch method table then gates candidates,
     * otherwise no block is ever wrapped (the 26.1.2 Fabric Athena zero-connected-texture
     * defect). Once surfaces are published the published snapshot stays the precise gate;
     * blocks without a pending candidate are never wrapped, so wrapping is never
     * unconditional.
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
            synchronized (FabricAthenaModelLifecycle.class) {
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
                preparedMethods.reloadToken(),
                candidates.keySet());
    }

}
