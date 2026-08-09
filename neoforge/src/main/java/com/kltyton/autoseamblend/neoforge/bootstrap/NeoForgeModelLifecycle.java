package com.kltyton.autoseamblend.neoforge.bootstrap;

import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.engine.routing.ModelOwnershipRuntime;
import com.kltyton.autoseamblend.neoforge.compat.continuity.runtime.culling.ContinuityGlassPaneSeamCulling;
import com.kltyton.autoseamblend.neoforge.engine.registry.NeoForgeEngineRegistry;
import com.kltyton.autoseamblend.neoforge.runtime.culling.GlassPaneSeamCulling;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.texture.generation.GeneratedSpritePlanning;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;

/** 中文：从同一模型烘焙代次发布选择器和模型事实。 / English: Publishes selectors and model facts from one model-bake generation. */
public final class NeoForgeModelLifecycle {
    private NeoForgeModelLifecycle() {}

    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        // 中文：Fusion 1.3.12 NeoForge 在 ModelEvent 之后才通过 ModelManager Mixin
        // 应用方块模型 modifier。此处捕获会早于原生模型与 accepted-document 发布，必须
        // 延迟到 Fusion modifier publish 钩子，否则整个代次会被标记为 UNKNOWN。
        // English: Fusion 1.3.12 NeoForge applies block model modifiers through its
        // ModelManager Mixin after ModelEvent. Capturing here would precede both native models
        // and accepted-document publication, so defer to the Fusion modifier publish hook.
        if (NeoForgeEngineRegistry.RUNTIME
                .linkableEngineIds()
                .contains("fusion")) {
            return;
        }
        prepareModelFacts(event.getModels());
    }

    /**
     * 中文：Fusion 原生 modifier 完成并发布 accepted documents 后，使用同一 ModelBakery
     * 的最终方块模型完成所有权、表面与玻璃板端盖准备。
     * English: After Fusion's native modifiers and accepted documents are published, prepares
     * ownership, surfaces, and pane caps from the same ModelBakery's final block models.
     */
    public static void onFusionModifiersApplied(ModelBakery bakery) {
        prepareModelFacts(bakery.getBakedTopLevelModels());
    }

    private static void prepareModelFacts(
            Map<ModelResourceLocation, BakedModel> baked) {
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
        Map<BlockState, BakedModel> models =
                blockStateModels(
                        baked);
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
            // 中文：Continuity 通过 Fabric Renderer API 发射路径渲染，必须使用带
            // QuadTransform 的 Continuity 专用安装器；CTM/Fusion/Athena 走 vanilla
            // getQuads，使用纯 NeoForge 包装器。选择只依据引擎链接发现，不使用 Gradle
            // property、反射或 catch(Throwable)。
            // English: Continuity renders through the Fabric Renderer API emission path and
            // needs the Continuity-specific installer with QuadTransform; CTM/Fusion/Athena
            // render through vanilla getQuads and use the pure NeoForge wrapper. Selection
            // relies only on engine linkage discovery, never Gradle properties, reflection,
            // or catch(Throwable).
            boolean continuityLinkable =
                    NeoForgeEngineRegistry.RUNTIME
                            .linkableEngineIds()
                            .contains("continuity");
            int paneModels = continuityLinkable
                    ? ContinuityGlassPaneSeamCulling.install(
                            models,
                            prepared.selectors(),
                            prepared.preparedMethods(),
                            surfaces)
                    : GlassPaneSeamCulling.install(
                            models,
                            prepared.selectors(),
                            prepared.preparedMethods(),
                            surfaces);
            // 中文：26.1.2 直接修改 event 自身的模型表；1.21.1 的 blockStateModels() 是副本，
            // 必须把安装好的玻璃板剔除包装器写回 event.getModels()，否则游戏实际渲染仍使用
            // 未包装模型，端盖剔除修复不会生效。
            // English: 26.1.2 mutates the event's own model map; 1.21.1 blockStateModels()
            // returns a copy, so the installed pane-culling wrappers must be written back into
            // event.getModels(), otherwise the game still renders unwrapped models and the
            // pane-cap culling fix never applies.
            writeBackPaneCulling(
                    baked,
                    models);
            ReloadPublication.stageModelFacts(
                    ownership,
                    surfaces);
            Constants.LOG.info(
                    "Prepared vanilla pane-cap culling for {} eligible block states (installer={})",
                    paneModels,
                    continuityLinkable ? "continuity" : "neoforge");
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

    private static void writeBackPaneCulling(
            Map<ModelResourceLocation, BakedModel> baked,
            Map<BlockState, BakedModel> decorated) {
        for (Map.Entry<BlockState, BakedModel> entry
                : decorated.entrySet()) {
            BakedModel model = entry.getValue();
            ModelResourceLocation location =
                    BlockModelShaper.stateToModelLocation(
                            entry.getKey());
            if (baked.get(location) != model) {
                baked.put(location, model);
            }
        }
    }

    private static Map<BlockState, BakedModel> blockStateModels(
            Map<ModelResourceLocation, BakedModel> baked) {
        Map<BlockState, BakedModel> models =
                new HashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            for (BlockState state :
                    block.getStateDefinition()
                            .getPossibleStates()) {
                BakedModel model = baked.get(
                        BlockModelShaper
                                .stateToModelLocation(
                                        state));
                if (model != null) {
                    models.put(state, model);
                }
            }
        }
        return models;
    }
}
