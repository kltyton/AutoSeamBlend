package com.kltyton.autoseamblend.neoforge.compat.fusion.runtime;

import com.kltyton.autoseamblend.compat.fusion.runtime.FusionModifierLifecycleHooks;
import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：把共用 Fusion modifier 生命周期接到 NeoForge 原生文档目录。
 * English: Connects the shared Fusion modifier lifecycle to the NeoForge native document catalog.
 */
public final class NeoForgeFusionModifierLifecycleHooks
        implements FusionModifierLifecycleHooks.Hooks {
    public static final NeoForgeFusionModifierLifecycleHooks INSTANCE =
            new NeoForgeFusionModifierLifecycleHooks();

    private NeoForgeFusionModifierLifecycleHooks() {
    }

    @Override
    public void begin(
            ResourceManager resources,
            Map<Identifier, Resource> documents) {
        FusionAcceptedModifierDocumentCatalog.beginReload(documents);
    }

    @Override
    public void publish(
            ModelBakery.BakingResult bakingResult,
            Map<BlockState, ? extends List<?>> modifiers) {
        FusionAcceptedModifierDocumentCatalog.publishAccepted(modifiers);
    }
}
