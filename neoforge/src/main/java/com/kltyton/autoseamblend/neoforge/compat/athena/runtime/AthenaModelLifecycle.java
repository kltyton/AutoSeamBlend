package com.kltyton.autoseamblend.neoforge.compat.athena.runtime;

import com.kltyton.autoseamblend.foundation.Constants;
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
        int[] decorated = {0};
        int[] nativePanes = {0};
        models.replaceAll((state, model) -> {
            if (!surfaces.states().containsKey(state)) {
                return model;
            }
            decorated[0]++;
            BlockStateModel paneModel = AthenaGeneratedPaneModelFactory
                    .create(
                            event.getTextureGetter(),
                            generation,
                            surfaces,
                            state,
                            model)
                    .orElse(null);
            if (paneModel != null) {
                nativePanes[0]++;
                return paneModel;
            }
            return new AthenaConnectedBlockStateModel(
                    model,
                    state);
        });
        Constants.LOG.info(
                "Installed Athena-native AutoBlend model routing for {} block states",
                decorated[0]);
        Constants.LOG.info(
                "Installed Athena native pane geometry for {} block states",
                nativePanes[0]);
    }
}
