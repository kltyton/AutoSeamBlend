package com.kltyton.autoseamblend.neoforge.compat.ctm_mod.runtime;

import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.client.renderer.RenderType;

/**
 * 中文：CTM 渲染 pass 的纯策略：广告渲染类型与按 pass 分流。不访问 generation/引擎，
 * 只根据调用方提供的原始渲染类型、当前 renderType 与 needsOverlay 布尔做决定，便于
 * JUnit 直接锁定（ChunkRenderTypeSet 实现 Iterable<RenderType>，测试用 List 即可）。
 *
 * <p>English: Pure CTM render-pass policy: advertised render types and per-pass routing.
 * It never touches generation or engine state; it decides only from the caller-supplied
 * original render types, the current renderType, and the needsOverlay boolean, so JUnit can
 * lock it directly (ChunkRenderTypeSet implements Iterable<RenderType>; tests may use List).
 */
final class CtmModRenderPassPolicy {
    private CtmModRenderPassPolicy() {}

    /** 中文：base/overlay pass 分流结果。 / English: Base/overlay pass routing result. */
    record PassDecision(boolean basePass, boolean overlayPass) {}

    /**
     * 中文：仅在 needsOverlay 时把 RenderType.cutout() 并入广告类型；否则原样返回。
     *
     * <p>English: Unions RenderType.cutout() into the advertised types only when
     * needsOverlay; otherwise returns the original set unchanged.
     */
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

    /**
     * 中文：按当前 pass 的 renderType 分流：basePass 在 renderType 为 null 或属于
     * 原始类型时成立；overlayPass 仅在 needsOverlay 且 renderType 为 cutout 时成立。
     * RenderType 为单例，contains 使用实例恒等。
     *
     * <p>English: Routes the current pass by renderType: basePass holds when renderType is
     * null or belongs to the original types; overlayPass holds only when needsOverlay and
     * renderType is cutout. RenderType instances are singletons, so containment uses
     * instance identity.
     */
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
