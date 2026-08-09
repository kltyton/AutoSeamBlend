package com.kltyton.autoseamblend.mixin.plugin;

/**
 * 中文：集中保存 Loader 无关的可选引擎目标集合。 / English: Centralizes loader-neutral optional-engine target sets.
 */
public final class MixinPluginTargets {
    public static final MixinTargetRule NEOFORGE_OPTIONAL_ENGINE = MixinTargetRule.any(true);
    public static final MixinTargetRule FABRIC_CONTINUITY = MixinTargetRule.any(false);
    public static final MixinTargetRule FABRIC_ATHENA =
            MixinTargetRule.exact(
                    false,
                    "earth.terrarium.athena.api.client.fabric.AthenaBakedModel",
                    "earth.terrarium.athena.impl.client.models.PaneConnectedBlockModel",
                    "earth.terrarium.athena.impl.client.models.ctm.ConnectedTextureMap",
                    "earth.terrarium.athena.impl.client.models.materials.MaterialStorage",
                    "earth.terrarium.athena.impl.loading.AthenaResourceLoader",
                    "net.minecraft.client.resources.model.ModelManager",
                    "net.minecraft.client.resources.model.BlockStateModelLoader");
    public static final MixinTargetRule FABRIC_FUSION =
            MixinTargetRule.exact(
                    false,
                    "com.supermartijn642.fusion.model.WrappedBakedModel",
                    "com.supermartijn642.fusion.model.types.base.BaseBlockStateModel",
                    "com.supermartijn642.fusion.model.types.composite.CompositeBlockStateModel",
                    "com.supermartijn642.fusion.model.modifiers.block.BlockModelModifierBakedModel",
                    "com.supermartijn642.fusion.model.modifiers.block.BlockModelModifierBakedModel$ConditionalModel",
                    "com.supermartijn642.fusion.model.modifiers.block.BlockModelModifierReloadListener",
                    "com.supermartijn642.fusion.model.modifiers.block.BlockModelModifierReloadListener$Properties",
                    "com.supermartijn642.fusion.model.modifiers.block.BlockModelModifierReloadListener$ModelEntry",
                    "com.supermartijn642.fusion.texture.RawTextureInstanceImpl",
                    "com.supermartijn642.fusion.texture.custom.TextureCreationContextImpl",
                    "com.supermartijn642.fusion.texture.TextureTypeRegistryImpl",
                    "net.minecraft.client.resources.model.ModelManager");

    private MixinPluginTargets() {}
}
