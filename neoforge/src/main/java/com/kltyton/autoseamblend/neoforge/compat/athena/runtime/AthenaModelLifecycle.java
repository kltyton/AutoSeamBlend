package com.kltyton.autoseamblend.neoforge.compat.athena.runtime;

import com.kltyton.autoseamblend.neoforge.compat.athena.runtime.pane.AthenaGeneratedPaneModelFactory;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import java.util.Map;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;

/** 中文：共享表面快照发布后装饰非原生烘焙模型。 / English: Decorates non-native baked models after the shared surface snapshot was published. */
public final class AthenaModelLifecycle {
    private AthenaModelLifecycle() {}

    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<BlockState, BlockStateModel> models =
                event.getBakingResult().blockStateModels();
        MinecraftSurfaceCatalog.Snapshot surfaces =
                ReloadPublication.modelDecorationSurfaces();
        ReloadPublication.Generation generation =
                ReloadPublication.pendingPreparation()
                        .filter(candidate ->
                                candidate.generation() == surfaces.generation())
                        .orElseGet(ReloadPublication::current);
        models.replaceAll((state, model) -> {
            if (!surfaces.states().containsKey(state)) {
                return model;
            }
            BlockStateModel paneModel = AthenaGeneratedPaneModelFactory
                    .create(
                            event.getTextureGetter(),
                            generation,
                            surfaces,
                            state,
                            model)
                    .orElse(null);
            if (paneModel != null) {
                return paneModel;
            }
            return new AthenaConnectedBlockStateModel(
                    model,
                    state);
        });
    }
}
