package com.kltyton.autoseamblend.fabric.bootstrap;

import com.kltyton.autoseamblend.bootstrap.CommonClass;
import com.kltyton.autoseamblend.config.runtime.FzzyConfigRuntime;
import com.kltyton.autoseamblend.engine.registry.EngineRegistryRuntimeState;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.fabric.engine.registry.FabricEngineRegistry;
import com.kltyton.autoseamblend.fabric.reload.lifecycle.FabricEngineBootstrap;
import com.kltyton.autoseamblend.fabric.reload.lifecycle.FabricModelLifecycle;
import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolution;
import com.kltyton.autoseamblend.runtime.publication.ReloadRulePublication;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.reload.texture.InitialGeneratedSpritePreparation;
import com.kltyton.autoseamblend.texture.atlas.InitialAtlasPreparationHooks;
import net.fabricmc.api.ClientModInitializer;

/**
 * 中文：26.1.2 Fabric 客户端 composition root。
 *
 * <p>English: 26.1.2 Fabric client composition root. Fabric has no CTM Mod
 * target, so only the shared MCPatcher/Fusion/Athena machinery is wired here.
 */
public final class AutoSeamBlendFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CommonClass.init();
        RuleRuntime.installPublication(ReloadRulePublication.INSTANCE);
        FzzyConfigRuntime.initialize();
        RuleRuntime.refresh("client-bootstrap");
        OverlayDonorResolution.installRouteLookup(
                (family, state) ->
                        EngineQueryRouter.select(state)
                                .filter(selection ->
                                        selection.family() == family)
                                .map(EngineQuerySelection::route));
        InitialAtlasPreparationHooks.install(
                InitialGeneratedSpritePreparation::prepare);
        FabricModelLifecycle.register();
        for (String engineId :
                FabricEngineRegistry.RUNTIME.linkableEngineIds()) {
            FabricEngineBootstrap.require(engineId).register();
        }
        EngineRegistryRuntimeState engines =
                FabricEngineRegistry.RUNTIME.initialize();
        EngineQueryRouter.installFallback(
                FabricEngineRegistry.RUNTIME::current);
        EngineQueryRouter.initialize(engines);
        FabricClientLifecycle.register(engines);
        if (engines.engineRequired()) {
            Constants.LOG.error(
                    "ENGINE_REQUIRED: install Continuity, Fusion, or Athena for Fabric 26.1.2");
        }
    }
}
