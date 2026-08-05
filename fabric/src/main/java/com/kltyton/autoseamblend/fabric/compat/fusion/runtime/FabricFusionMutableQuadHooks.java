package com.kltyton.autoseamblend.fabric.compat.fusion.runtime;

import com.kltyton.autoseamblend.compat.fusion.runtime.FusionMutableQuadHooks;
import com.supermartijn642.fusion.api.model.custom.quad.MutableQuad;

/**
 * 中文：Fusion Fabric 1.3.5 的 MutableQuad 没有 color/neoColors ABI；它的着色机制是
 * tintIndex/材质在 FRAPI 发射时解析（javap 确认 MutableQuadImpl/EmittableQuadImpl
 * 无颜色状态）。因此固定 overlay ARGB 由 {@link FabricFusionConnectedBlockStateModel}
 * 在 FRAPI 发射时写入顶点色。
 *
 * English: Fusion Fabric 1.3.5's MutableQuad exposes no color ABI; its tint
 * mechanism resolves tintIndex/material at FRAPI emission (javap confirms
 * MutableQuadImpl/EmittableQuadImpl hold no color state). The fixed overlay
 * ARGB is therefore written to vertex colors by
 * {@link FabricFusionConnectedBlockStateModel} at FRAPI emission.
 */
public enum FabricFusionMutableQuadHooks
        implements FusionMutableQuadHooks.Hooks {
    INSTANCE;

    @Override
    public void color(MutableQuad quad, int argb) {
        // 中文：无颜色状态可写；见类注释。
        // English: No color state is writable; see class javadoc.
    }
}
