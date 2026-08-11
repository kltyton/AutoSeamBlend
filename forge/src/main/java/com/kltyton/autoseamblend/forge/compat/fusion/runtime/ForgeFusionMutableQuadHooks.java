package com.kltyton.autoseamblend.forge.compat.fusion.runtime;

import com.kltyton.autoseamblend.compat.fusion.runtime.FusionMutableQuadHooks;
import com.supermartijn642.fusion.api.model.custom.quad.MutableQuad;

/**
 * 中文：Fusion 1.3.12（fabric 与 neoforge）的 MutableQuad 没有 color/neoColors ABI；
 * 固定 overlay ARGB 由 {@link FusionConnectedBlockStateModel} 在发射后写入顶点色。
 *
 * English: Fusion 1.3.12 (fabric and neoforge) MutableQuad exposes no color
 * ABI; the fixed overlay ARGB is written into vertex colors by
 * {@link FusionConnectedBlockStateModel} after emission.
 */
public enum ForgeFusionMutableQuadHooks implements FusionMutableQuadHooks.Hooks {
    INSTANCE;

    @Override
    public void color(MutableQuad quad, int argb) {
        // 中文：无颜色状态可写；见类注释。
        // English: No color state is writable; see class javadoc.
    }
}
