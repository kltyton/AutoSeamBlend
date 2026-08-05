package com.kltyton.autoseamblend.neoforge.compat.fusion.runtime;

import com.kltyton.autoseamblend.compat.fusion.runtime.FusionMutableQuadHooks;
import com.supermartijn642.fusion.api.model.custom.quad.MutableQuad;

/**
 * 中文：使用 Fusion NeoForge 1.3.12 的 neoColors ABI 写入一致的覆盖色。
 * English: Uses Fusion NeoForge 1.3.12's neoColors ABI to write the verified overlay color.
 */
public enum NeoForgeFusionMutableQuadHooks implements FusionMutableQuadHooks.Hooks {
    INSTANCE;

    @Override
    public void color(MutableQuad quad, int argb) {
        quad.neoColors(argb);
    }
}
