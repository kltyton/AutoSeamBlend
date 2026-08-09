package com.kltyton.autoseamblend.compat.fusion.runtime;

import com.supermartijn642.fusion.model.CombinedBlockStateModel;
import com.supermartijn642.fusion.model.modifiers.block.BlockModelModifierBakedModel;
import com.supermartijn642.fusion.model.types.base.BaseBlockStateModel;
import com.supermartijn642.fusion.model.types.composite.CompositeBlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;

/**
 * 中文：精确的 Fusion 1.3.12 所有权边界；具体类型被刻意限制在 compat/fusion，原生 Fusion 模型会一直保留到其已接受文档公开精确缺失槽位信息。
 *
 * English:
 * Exact Fusion 1.3.12 ownership boundary.
 *
 * <p>These concrete types are intentionally confined to compat/fusion. A native Fusion model is
 * preserved until its accepted document exposes exact missing-slot information.
 */
public final class FusionNativeModelOwnership {
    private FusionNativeModelOwnership() {}

    public static boolean owns(BlockStateModel model) {
        // 中文：1.3.12 的 combined 模型类型同样属于原生 Fusion 所有权。
        // English: The 1.3.12 combined model type is also native Fusion ownership.
        return model instanceof BaseBlockStateModel
                || model instanceof CompositeBlockStateModel
                || model instanceof CombinedBlockStateModel
                || model instanceof BlockModelModifierBakedModel;
    }
}
