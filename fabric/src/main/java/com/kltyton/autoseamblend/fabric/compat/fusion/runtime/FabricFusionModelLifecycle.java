package com.kltyton.autoseamblend.fabric.compat.fusion.runtime;

import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：为已准备 Fusion 表面的状态模型安装 AutoBlend 动态包装。
 * English: Installs the AutoBlend dynamic wrapper for states with prepared
 * Fusion surfaces.
 */
public final class FabricFusionModelLifecycle {
    private FabricFusionModelLifecycle() {}

    public static BlockStateModel wrap(
            BlockStateModel model,
            ModelModifier.AfterBakeBlock.Context context) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(context, "context");
        MinecraftSurfaceCatalog.Snapshot surfaces =
                ReloadPublication.modelDecorationSurfaces();
        if (!surfaces.states().containsKey(
                context.state())) {
            return model;
        }
        return new FabricFusionConnectedBlockStateModel(
                model,
                context.state());
    }
}
