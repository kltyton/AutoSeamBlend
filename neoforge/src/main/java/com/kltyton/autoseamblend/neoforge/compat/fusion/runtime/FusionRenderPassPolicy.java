package com.kltyton.autoseamblend.neoforge.compat.fusion.runtime;

import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.client.renderer.RenderType;

/**
 * 中文：Fusion 1.21.1 的渲染 pass 纯策略。该版本的 BakedQuad 不携带 Fusion
 * MutableQuad 上的 RenderType，因此 overlay 必须与原模型 pass 分离并只在 CUTOUT 发射。
 *
 * <p>English: Pure render-pass policy for Fusion 1.21.1. This version's BakedQuad does
 * not retain the RenderType stored on Fusion's MutableQuad, so overlays must be separated
 * from the delegate passes and emitted only in CUTOUT.
 */
final class FusionRenderPassPolicy {
    private FusionRenderPassPolicy() {}

    /** 中文：base/overlay pass 分流结果。 / English: Base/overlay pass routing result. */
    record PassDecision(boolean basePass, boolean overlayPass) {}

    static List<RenderType> advertisedTypes(
            Iterable<RenderType> original,
            boolean needsOverlay) {
        LinkedHashSet<RenderType> advertised = new LinkedHashSet<>();
        for (RenderType type : original) {
            advertised.add(type);
        }
        if (needsOverlay) {
            advertised.add(RenderType.cutout());
        }
        return List.copyOf(advertised);
    }

    static PassDecision decision(
            Iterable<RenderType> original,
            RenderType renderType,
            boolean needsOverlay) {
        boolean basePass = renderType == null
                || containsIdentity(original, renderType);
        boolean overlayPass = needsOverlay
                && renderType == RenderType.cutout();
        return new PassDecision(basePass, overlayPass);
    }

    private static boolean containsIdentity(
            Iterable<RenderType> types,
            RenderType renderType) {
        for (RenderType type : types) {
            if (type == renderType) {
                return true;
            }
        }
        return false;
    }
}
