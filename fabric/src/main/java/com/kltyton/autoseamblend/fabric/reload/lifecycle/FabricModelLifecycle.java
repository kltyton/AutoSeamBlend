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
        PreparedSurfaceMethods.Snapshot methods =
                ReloadPublication.current()
                        .preparedMethods();
        MinecraftSurfaceCatalog.Snapshot surfaces =
                ReloadPublication.current()
                        .surfaces();
        if (!FabricGlassPaneSeamCulling.applies(
                context.state().getBlock(),
                ReloadPublication.current().selectors(),
                methods,
                surfaces)) {
            return model;
        }
        return FabricGlassPaneSeamCulling.wrap(model);
    }
}
