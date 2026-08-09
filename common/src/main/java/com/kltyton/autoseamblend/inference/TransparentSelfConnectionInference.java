package com.kltyton.autoseamblend.inference;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：在通用事实推断后应用透明同状态边界消隐合同；加载器只负责提供原生边界观察。
 * English: Applies the transparent equal-state boundary-suppression contract after common fact
 * inference; loaders provide only the native boundary observation.
 */
public final class TransparentSelfConnectionInference {
    private TransparentSelfConnectionInference() {}

    /**
     * 中文：观察原生方块状态是否会消隐同状态边界。
     * English: Observes whether the native block state suppresses an equal-state boundary.
     */
    public static boolean observesEqualStateBoundary(BlockState state) {
        Objects.requireNonNull(state, "state");
        for (Direction direction : Direction.values()) {
            if (state.skipRendering(state, direction)) {
                return true;
            }
        }
        return false;
    }

    public static InferenceDecision decide(
            ConnectionMethod requested,
            InferenceFacts facts,
            boolean equalStateBoundarySuppressed) {
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(facts, "facts");
        InferenceDecision baseline = InferencePolicy.decide(requested, facts);
        if (requested != ConnectionMethod.AUTO
                || !qualifies(facts, equalStateBoundarySuppressed)) {
            return baseline;
        }
        String evidence = facts.partialGeometry().isTrue()
                ? "transparent_self_culling_partial_surface_ctm"
                : "transparent_self_culling_full_surface_ctm";
        return new InferenceDecision(
                ConnectionMethod.AUTO,
                java.util.Optional.of(ConnectionMethod.CTM),
                false,
                InferenceDecision.Confidence.CERTAIN,
                List.of(evidence),
                List.of());
    }

    /**
     * 中文：资格只依赖完整几何、纹理事实和加载器已观察到的同状态边界消隐，不读取 ID、名称或路径。
     * English: Eligibility depends only on complete geometry/texture facts and an observed native
     * equal-state boundary suppression; IDs, names, and paths are excluded.
     */
    public static boolean qualifies(
            InferenceFacts facts,
            boolean equalStateBoundarySuppressed) {
        Objects.requireNonNull(facts, "facts");
        if (!equalStateBoundarySuppressed
                || !facts.vanillaCuboidGeometry().isTrue()
                || !facts.axisAlignedGeometry().isTrue()
                || !facts.validUv().isTrue()
                || !facts.stateKnown().isTrue()
                || !facts.alphaOpaque().isFalse()
                || !facts.animated().isFalse()
                || facts.topOnly().isTrue()) {
            return false;
        }
        return facts.partialGeometry().isTrue()
                || (facts.fullBlock().isTrue() && facts.partialGeometry().isFalse());
    }
}
