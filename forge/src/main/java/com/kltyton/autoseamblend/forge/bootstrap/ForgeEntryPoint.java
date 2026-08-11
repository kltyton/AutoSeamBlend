package com.kltyton.autoseamblend.forge.bootstrap;

import com.kltyton.autoseamblend.bootstrap.CommonClass;
import com.kltyton.autoseamblend.authoring.document.NativeDocumentOperations;
import com.kltyton.autoseamblend.authoring.property.NativePropertyDocumentLoader;
import com.kltyton.autoseamblend.authoring.template.ManagedAuthoringTemplates;
import com.kltyton.autoseamblend.config.runtime.FzzyConfigRuntime;
import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.forge.command.client.ForgeClientCommands;
import com.kltyton.autoseamblend.forge.compat.athena.bootstrap.AthenaRuntimeBootstrap;
import com.kltyton.autoseamblend.forge.compat.continuity.bootstrap.ContinuityRuntimeBootstrap;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.template.CtmModAuthoringTemplateExtension;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.property.CtmModNativePropertyExtension;
import com.kltyton.autoseamblend.compat.ctm_mod.document.CtmModDocumentOperations;
import com.kltyton.autoseamblend.compat.ctm_mod.evidence.CtmModEvidenceExtension;
import com.kltyton.autoseamblend.compat.ctm_mod.reload.CtmModRuleCodecExtension;
import com.kltyton.autoseamblend.forge.compat.ctm_mod.bootstrap.CtmModRuntimeBootstrap;
import com.kltyton.autoseamblend.forge.compat.fusion.bootstrap.FusionRuntimeBootstrap;
import com.kltyton.autoseamblend.forge.engine.registry.ForgeEngineRegistry;
import com.kltyton.autoseamblend.engine.registry.EngineRegistryRuntimeState;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.forge.frontend.uilib.entry.UilibWorkbenchEntry;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.publication.ReloadRulePublication;
import com.kltyton.autoseamblend.forge.runtime.texture.atlas.ForgeGeneratedSpriteResolutionEvents;
import com.kltyton.autoseamblend.forge.runtime.texture.atlas.ForgeGeneratedSpriteSourceRegistration;
import com.kltyton.autoseamblend.reload.rule.RuleDocumentCodec;
import com.kltyton.autoseamblend.reload.rule.evidence.NativeSlotEvidenceResolver;
import com.kltyton.autoseamblend.reload.texture.InitialGeneratedSpritePreparation;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolution;
import com.kltyton.autoseamblend.texture.atlas.InitialAtlasPreparationHooks;
import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.engine.EngineFamily;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;

/** 中文：1.20.1 Forge 主入口。 / English: 1.20.1 Forge primary entrypoint. */
@Mod(value = Constants.MOD_ID)
public final class ForgeEntryPoint {
    public ForgeEntryPoint() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get()
                .getModEventBus();
        // 中文：CTM Mod 只存在于 Forge/Forge；其规则解析与槽位证据作为扩展接入公共机制。
        // English: CTM Mod exists only on Forge/Forge; its rule parsing and slot
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
        // 中文：Forge 47.x 的 IClientBlockExtensions 没有动态 tint 收集 API（26.1 才有），
        // 1.20.1 方块着色由原版 BlockColors 完整承载，公共预览 tint 解析不再需要 Loader 钩子。
        // English: Forge 47.x IClientBlockExtensions has no dynamic-tint collector (that is a
        // 26.1 API); 1.20.1 block tinting is fully covered by vanilla BlockColors, so the shared
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
        modEventBus.addListener(ForgeModelLifecycle::onModifyBakingResult);
        for (String engineId :
                ForgeEngineRegistry.RUNTIME.linkableEngineIds()) {
            registerEngineProviders(
                    engineId,
                    modEventBus);
        }
        EngineRegistryRuntimeState engines =
                ForgeEngineRegistry.RUNTIME.initialize();
        EngineQueryRouter.installFallback(
                ForgeEngineRegistry.RUNTIME::current);
        EngineQueryRouter.initialize(engines);
        MinecraftForge.EVENT_BUS.addListener(
                ForgeClientCommands::register);
        MinecraftForge.EVENT_BUS.addListener(
                ForgeClientLifecycle::onLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(
                ForgeClientLifecycle::onLoggingOut);
        MinecraftForge.EVENT_BUS.addListener(
                UilibWorkbenchEntry::onClientTick);
        modEventBus.addListener(
                UilibWorkbenchEntry::register);
        // 中文：1.20.1 没有 PIP 注册事件，预览由 RenderPort 在 GUI 绘制通道直接提交。
        // English: 1.20.1 has no PIP registration event; the preview is
        // submitted directly by the RenderPort in the GUI draw pass.

        // 1.20.1 Forge has no RegisterSpriteSourceTypesEvent. The bundled atlas JSON always
        // references this type, so it must be registered even when the engine gate fails.
        ForgeGeneratedSpriteSourceRegistration.register();
        if (engines.engineRequired()) {
            Constants.LOG.error(
                    "ENGINE_REQUIRED: install Continuity or Constancy, CTM Mod, Fusion, or Athena for Forge 1.20.1");
            return;
        }

        // TextureStitchEvent.Post implements IModBusEvent on Forge 47.x.
        modEventBus.addListener(
                ForgeGeneratedSpriteResolutionEvents::onTextureAtlasStitched);
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
                    "unmapped Forge engine id: "
                            + engineId);
        }
    }
}
