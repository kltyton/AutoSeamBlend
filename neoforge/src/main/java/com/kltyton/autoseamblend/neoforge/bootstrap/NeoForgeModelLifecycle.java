package com.kltyton.autoseamblend.neoforge.bootstrap;

import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.engine.routing.ModelOwnershipRuntime;
import com.kltyton.autoseamblend.neoforge.runtime.culling.GlassPaneSeamCulling;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.texture.generation.GeneratedSpritePlanning;
import java.util.Map;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;

/** 中文：从同一模型烘焙代次发布选择器和模型事实。 / English: Publishes selectors and model facts from one model-bake generation. */
public final class NeoForgeModelLifecycle {
    private NeoForgeModelLifecycle() {}

    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ReloadPublication.Generation prepared =
                ReloadPublication.pendingPreparation()
                        .orElse(null);
        if (prepared == null) {
            if (!GeneratedSpritePlanning.hasInitialPlanners()) {
                return;
            }
            Constants.LOG.error(
                    "Retained the active reload generation because model bake has no complete pre-atlas candidate");
            return;
        }
        long generation = prepared.generation();
        Map<BlockState, BlockStateModel> models =
                event.getBakingResult().blockStateModels();
        ModelOwnershipRuntime.PreparedCapture ownership =
                null;
        try {
            ownership = ModelOwnershipRuntime.prepare(
                    models,
                    generation);
            MinecraftSurfaceCatalog.Snapshot surfaces =
                    MinecraftSurfaceCatalog.prepare(
                            models,
                            prepared.preparedMethods(),
                            generation);
            int paneModels = GlassPaneSeamCulling.install(
                    models,
                    prepared.selectors(),
                    prepared.preparedMethods(),
                    surfaces);
            ReloadPublication.stageModelFacts(
                    ownership,
                    surfaces);
            Constants.LOG.info(
                    "Prepared vanilla pane-cap culling for {} eligible block states",
                    paneModels);
        } catch (RuntimeException exception) {
            if (ownership != null) {
                ModelOwnershipRuntime.abort(
                        ownership);
            }
            ReloadPublication.discardPending(
                    generation);
            throw exception;
        }
    }
}
