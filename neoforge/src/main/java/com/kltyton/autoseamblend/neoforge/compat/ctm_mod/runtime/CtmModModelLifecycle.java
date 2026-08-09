package com.kltyton.autoseamblend.neoforge.compat.ctm_mod.runtime;

import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import java.util.Map;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;

/** 中文：使用 CTM Lib 原生动态状态采样器装饰非原生烘焙模型。 / English: Decorates non-native baked models with CTM Lib's native dynamic state sampler. */
public final class CtmModModelLifecycle {
    private CtmModModelLifecycle() {}

    public static void onModifyBakingResult(
            ModelEvent.ModifyBakingResult event) {
        Map<BlockState, BlockStateModel> models =
                event.getBakingResult().blockStateModels();
        MinecraftSurfaceCatalog.Snapshot surfaces =
                ReloadPublication.modelDecorationSurfaces();
        int[] decorated = {0};
        models.replaceAll((state, model) -> {
            if (!surfaces.states().containsKey(state)) {
                return model;
            }
            decorated[0]++;
            return new CtmModConnectedBlockStateModel(
                    model,
                    state);
        });
        Constants.LOG.info(
                "Installed CTM Lib-native AutoBlend model routing for {} block states",
                decorated[0]);
    }
}
