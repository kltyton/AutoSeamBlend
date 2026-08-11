package com.kltyton.autoseamblend.forge.compat.fusion.runtime;

import com.kltyton.autoseamblend.compat.fusion.runtime.FusionModifierLifecycleHooks;
import com.kltyton.autoseamblend.forge.bootstrap.ForgeModelLifecycle;
import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 中文：把共用 Fusion modifier 生命周期接到 Forge 原生文档目录。
 * English: Connects the shared Fusion modifier lifecycle to the Forge native document catalog.
 */
public final class ForgeFusionModifierLifecycleHooks
        implements FusionModifierLifecycleHooks.Hooks {
    public static final ForgeFusionModifierLifecycleHooks INSTANCE =
            new ForgeFusionModifierLifecycleHooks();

    private ForgeFusionModifierLifecycleHooks() {
    }

    @Override
    public void begin(
            ResourceManager resources,
            Map<ResourceLocation, Resource> documents) {
        FusionAcceptedModifierDocumentCatalog.beginReload(documents);
    }

    @Override
    public void publish(
            ModelBakery bakery,
            Map<ModelResourceLocation, ? extends List<?>> modifiers) {
        FusionAcceptedModifierDocumentCatalog.publishAccepted(modifiers);
        // 中文：Fusion 1.3.12 的 ModelManager Mixin 在 Forge ModelEvent 之后才应用
        // modifier；因此必须在 accepted-document snapshot 可见后捕获最终原生模型，并在
        // 捕获完成后安装 AutoBlend 最外层包装器。
        // English: Fusion 1.3.12's ModelManager Mixin applies modifiers after Forge's
        // ModelEvent. Capture final native models after the accepted-document snapshot becomes
        // visible, then install the outer AutoBlend wrapper.
        ForgeModelLifecycle.onFusionModifiersApplied(bakery);
        FusionModelLifecycle.onFusionModifiersApplied(bakery);
    }
}
