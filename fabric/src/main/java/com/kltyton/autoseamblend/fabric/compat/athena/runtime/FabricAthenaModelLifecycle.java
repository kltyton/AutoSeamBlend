package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import java.util.Objects;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;

/**
 * 中文：共享表面快照发布后装饰非原生烘焙模型。
 * English: Decorates non-native baked models after the shared surface snapshot
 * was published.
 */
public final class FabricAthenaModelLifecycle {
    private FabricAthenaModelLifecycle() {}

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
        return new FabricAthenaConnectedBlockStateModel(
                model,
                context.state());
    }
}
