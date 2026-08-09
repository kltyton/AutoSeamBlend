package com.kltyton.autoseamblend.compat.fusion.runtime;

import com.supermartijn642.fusion.model.modifiers.block.BlockModelModifierBakedModel;
import com.supermartijn642.fusion.model.types.base.BaseBakedModel;
import com.supermartijn642.fusion.model.types.composite.CompositeBakedModel;
import net.minecraft.client.resources.model.BakedModel;

/**
 * 中文：精确的 Fusion 1.3.12 所有权边界；具体类型被刻意限制在 compat/fusion，原生 Fusion 模型会一直保留到其已接受文档公开精确缺失槽位信息。
 *
 * English:
 * Exact Fusion 1.3.12 ownership boundary (1.21.1 names: BaseBakedModel/CompositeBakedModel).
 *
 * <p>These concrete types are intentionally confined to compat/fusion. A native Fusion model is
 * preserved until its accepted document exposes exact missing-slot information.
 */
public final class FusionNativeModelOwnership {
    private FusionNativeModelOwnership() {}

    public static boolean owns(BakedModel model) {
        return model instanceof BaseBakedModel
                || model instanceof CompositeBakedModel
                || model instanceof BlockModelModifierBakedModel;
    }
}
