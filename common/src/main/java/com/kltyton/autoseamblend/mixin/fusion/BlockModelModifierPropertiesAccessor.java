package com.kltyton.autoseamblend.mixin.fusion;

import java.util.List;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 中文：读取锁定 Fusion parser 保留的顶层文档及模型列表。 / English: Reads the top-level document and model lists retained by the locked Fusion parser. */
@Mixin(
        targets = "com.supermartijn642.fusion.model.modifiers.block.BlockModelModifierReloadListener$Properties",
        remap = false)
public interface BlockModelModifierPropertiesAccessor {
    @Accessor(value = "location", remap = false)
    Identifier autoseamblend$location();

    @Accessor(value = "defaultModelOverrides", remap = false)
    List<?> autoseamblend$defaultModelOverrides();

    @Accessor(value = "appendModels", remap = false)
    List<?> autoseamblend$appendModels();
}
