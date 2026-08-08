package com.kltyton.autoseamblend.fabric.reload.lifecycle;

import com.kltyton.autoseamblend.fabric.runtime.culling.FabricGlassPaneSeamCulling;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.surface.PreparedSurfaceMethods;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.minecraft.world.level.block.IronBarsBlock;

/**
 * 中文：Loader 级模型生命周期：先捕获基础模型，再在原生包装器阶段安装玻璃板端盖剔除。
 *
 * English: Loader-level model lifecycle: captures base models first, then
 * installs glass-pane cap culling in the native wrapper phase.
 */
public final class FabricModelLifecycle {
    private FabricModelLifecycle() {}

    public static void register() {
        PreparableModelLoadingPlugin.<Long>register(
                (state, executor) ->
                        CompletableFuture.completedFuture(
                                FabricModelCapture.begin()),
                (session, context) ->
                        registerPhases(session, context));
    }

    private static void registerPhases(
            long session,
            net.fabricmc.fabric.api.client.model.loading.v1
                    .ModelLoadingPlugin.Context context) {
        context.modifyBlockModelAfterBake()
                .register(
                        ModelModifier.OVERRIDE_PHASE,
                        (model, modifierContext) -> {
                            FabricModelCapture.capture(
                                    session,
                                    modifierContext.state(),
                                    model);
                            return model;
                        });
        context.modifyBlockModelAfterBake()
                .register(
                        ModelModifier.WRAP_PHASE,
                        FabricModelLifecycle::paneCulling);
    }

    private static net.minecraft.client.renderer.block.dispatch.BlockStateModel
            paneCulling(
                    net.minecraft.client.renderer.block.dispatch.BlockStateModel
                            model,
                    ModelModifier.AfterBakeBlock.Context context) {
        if (!(context.state().getBlock()
                instanceof IronBarsBlock)) {
            return model;
        }
        // 中文：首次烘焙时 current() 仍是 bootstrap 空代次（methods/surfaces=0），必须读
        // 同代次 pending 的预缝合方法表，否则自动发现的染色玻璃板永远装不上端盖剔除包装器；
        // NeoForge 在同一烘焙内现算表面，因此没有这个问题。
        // English: On the first bake current() is still the empty bootstrap generation
        // (methods/surfaces=0); read the same-reload pending pre-stitch method table so
        // auto-discovered stained panes get the cap-culling wrapper. NeoForge computes
        // surfaces inside the same bake and therefore has no such gap.
        ReloadPublication.Generation pending =
                ReloadPublication.pendingPreparation()
                        .orElse(null);
        PreparedSurfaceMethods.Snapshot methods =
                pending != null
                        ? pending.preparedMethods()
                        : ReloadPublication.current()
                                .preparedMethods();
        MinecraftSurfaceCatalog.Snapshot surfaces =
                ReloadPublication.current()
                        .surfaces();
        if (!FabricGlassPaneSeamCulling.applies(
                context.state().getBlock(),
                pending != null
                        ? pending.selectors()
                        : ReloadPublication.current()
                                .selectors(),
                methods,
                surfaces)) {
            return model;
        }
        return FabricGlassPaneSeamCulling.wrap(model);
    }
}
