package com.kltyton.autoseamblend.neoforge.bootstrap;

import com.kltyton.autoseamblend.bootstrap.CommonClass;
import com.kltyton.autoseamblend.authoring.document.NativeDocumentOperations;
import com.kltyton.autoseamblend.authoring.property.NativePropertyDocumentLoader;
import com.kltyton.autoseamblend.authoring.template.ManagedAuthoringTemplates;
import com.kltyton.autoseamblend.config.runtime.FzzyConfigRuntime;
import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.neoforge.command.client.NeoForgeClientCommands;
import com.kltyton.autoseamblend.neoforge.compat.athena.bootstrap.AthenaRuntimeBootstrap;
import com.kltyton.autoseamblend.neoforge.compat.continuity.bootstrap.ContinuityRuntimeBootstrap;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.template.CtmModAuthoringTemplateExtension;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.property.CtmModNativePropertyExtension;
import com.kltyton.autoseamblend.compat.ctm_mod.document.CtmModDocumentOperations;
import com.kltyton.autoseamblend.compat.ctm_mod.evidence.CtmModEvidenceExtension;
import com.kltyton.autoseamblend.compat.ctm_mod.reload.CtmModRuleCodecExtension;
import com.kltyton.autoseamblend.neoforge.compat.ctm_mod.bootstrap.CtmModRuntimeBootstrap;
import com.kltyton.autoseamblend.neoforge.compat.fusion.bootstrap.FusionRuntimeBootstrap;
import com.kltyton.autoseamblend.neoforge.engine.registry.NeoForgeEngineRegistry;
import com.kltyton.autoseamblend.engine.registry.EngineRegistryRuntimeState;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.neoforge.frontend.uilib.entry.UilibWorkbenchEntry;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.publication.ReloadRulePublication;
import com.kltyton.autoseamblend.neoforge.runtime.texture.atlas.NeoForgeGeneratedSpriteResolutionEvents;
import com.kltyton.autoseamblend.neoforge.runtime.texture.atlas.NeoForgeGeneratedSpriteSourceRegistration;
import com.kltyton.autoseamblend.reload.rule.RuleDocumentCodec;
import com.kltyton.autoseamblend.reload.rule.evidence.NativeSlotEvidenceResolver;
import com.kltyton.autoseamblend.reload.texture.InitialGeneratedSpritePreparation;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolution;
import com.kltyton.autoseamblend.texture.atlas.InitialAtlasPreparationHooks;
import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.engine.EngineFamily;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/** 中文：1.21.1 NeoForge 主入口。 / English: 1.21.1 NeoForge primary entrypoint. */
@Mod(value = Constants.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeEntryPoint {
    public NeoForgeEntryPoint(IEventBus modEventBus, ModContainer modContainer) {
        // 中文：CTM Mod 只存在于 NeoForge/Forge；其规则解析与槽位证据作为扩展接入公共机制。
        // English: CTM Mod exists only on NeoForge/Forge; its rule parsing and slot
        // evidence join the shared mechanisms as Loader-registered extensions.
        RuleDocumentCodec.register(
                CtmModRuleCodecExtension.INSTANCE);
        NativeSlotEvidenceResolver.registerCtmMod(
                CtmModEvidenceExtension.INSTANCE);
        ManagedAuthoringTemplates.registerFamily(
                EngineFamily.CTM_MOD,
                CtmModAuthoringTemplateExtension.INSTANCE);
        NativePropertyDocumentLoader.registerFamily(
                EngineFamily.CTM_MOD,
                CtmModNativePropertyExtension.INSTANCE);
        OverlayDonorResolution.installRouteLookup(
                (family, state) ->
                        EngineQueryRouter.select(state)
                                .filter(selection ->
                                        selection.family() == family)
                                .map(EngineQuerySelection::route));
        // 中文：NeoForge 21.1 的 IClientBlockExtensions 没有动态 tint 收集 API（26.1 才有），
        // 1.21.1 方块着色由原版 BlockColors 完整承载，公共预览 tint 解析不再需要 Loader 钩子。
        // English: NeoForge 21.1 IClientBlockExtensions has no dynamic-tint collector (that is a
        // 26.1 API); 1.21.1 block tinting is fully covered by vanilla BlockColors, so the shared
        // preview-tint resolution needs no Loader hook here.
        NativeDocumentOperations.registerFamily(
                EngineFamily.CTM_MOD,
                CtmModDocumentOperations.INSTANCE);
        InitialAtlasPreparationHooks.install(InitialGeneratedSpritePreparation::prepare);
        RuleRuntime.installPublication(ReloadRulePublication.INSTANCE);
        CommonClass.init();
        FzzyConfigRuntime.initialize();
        RuleRuntime.refresh("client-bootstrap");

        // 中文：共享捕获必须先于任何引擎适配器包装模型。 / English: Shared capture must run before any engine adapter wraps models.
        modEventBus.addListener(NeoForgeModelLifecycle::onModifyBakingResult);
        for (String engineId :
                NeoForgeEngineRegistry.RUNTIME.linkableEngineIds()) {
            registerEngineProviders(
                    engineId,
                    modEventBus);
        }
        EngineRegistryRuntimeState engines =
                NeoForgeEngineRegistry.RUNTIME.initialize();
        EngineQueryRouter.installFallback(
                NeoForgeEngineRegistry.RUNTIME::current);
        EngineQueryRouter.initialize(engines);
        NeoForge.EVENT_BUS.addListener(
                NeoForgeClientCommands::register);
        NeoForge.EVENT_BUS.addListener(
                NeoForgeClientLifecycle::onLoggingIn);
        NeoForge.EVENT_BUS.addListener(
                NeoForgeClientLifecycle::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(
                UilibWorkbenchEntry::onClientTick);
        modEventBus.addListener(
                UilibWorkbenchEntry::register);
        // 中文：1.21.1 没有 PIP 注册事件，预览由 RenderPort 在 GUI 绘制通道直接提交。
        // English: 1.21.1 has no PIP registration event; the preview is
        // submitted directly by the RenderPort in the GUI draw pass.
        if (engines.engineRequired()) {
            Constants.LOG.error(
                    "ENGINE_REQUIRED: install NeoContinuity, CTM Mod, Fusion, or Athena");
            return;
        }

        modEventBus.addListener(NeoForgeGeneratedSpriteSourceRegistration::register);
        modEventBus.addListener(NeoForgeGeneratedSpriteResolutionEvents::onTextureAtlasStitched);
        Constants.LOG.info(
                "Activated native dynamic connected-texture routing: installed={}",
                engines.readyEngineIds());
    }

    /** 中文：仅在无第三方类型的发现门确认目标后链接并注册该引擎 provider。 / English: Links and registers engine providers only after the third-party-free discovery gate accepts the target. */
    private static void registerEngineProviders(
            String engineId,
            IEventBus modEventBus) {
        switch (engineId) {
            case "continuity" ->
                    ContinuityRuntimeBootstrap.register();
            case "ctm" ->
                    CtmModRuntimeBootstrap.register(
                            modEventBus);
            case "fusion" ->
                    FusionRuntimeBootstrap.register(
                            modEventBus);
            case "athena" ->
                    AthenaRuntimeBootstrap.register(
                            modEventBus);
            default -> throw new IllegalStateException(
                    "unmapped NeoForge engine id: "
                            + engineId);
        }
    }
}
