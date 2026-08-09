package com.kltyton.autoseamblend.inference;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Objects;

/**
 * 中文：统一表面方法决策与 AUTO 拒绝后的透传策略；Loader 不再复制推断和回退分支。
 * English: Centralizes surface-method decisions and the passthrough fallback after rejected AUTO;
 * Loaders no longer duplicate inference and fallback branches.
 */
public final class SurfaceMethodDecisionPolicy {
    private static final String REJECTED_AUTO_PASSTHROUGH =
            "rejected_auto_passthrough";

    private SurfaceMethodDecisionPolicy() {
    }

    /**
     * 中文：对一次表面请求只执行一次透明同状态边界策略，并把未知 AUTO 安全降级为 NONE。
     * English: Applies the transparent equal-state boundary policy once and safely degrades an
     * unknown AUTO request to NONE.
     */
    public static InferenceDecision decide(
            ConnectionMethod requested,
            InferenceFacts facts,
            boolean equalStateBoundarySuppressed) {
        InferenceDecision decision = TransparentSelfConnectionInference.decide(
                Objects.requireNonNull(requested, "requested"),
                Objects.requireNonNull(facts, "facts"),
                equalStateBoundarySuppressed);
        if (decision.resolvedMethod().isPresent()) {
            return decision;
        }
        return new InferenceDecision(
                ConnectionMethod.AUTO,
                java.util.Optional.of(ConnectionMethod.NONE),
                false,
                InferenceDecision.Confidence.CERTAIN,
                List.of(REJECTED_AUTO_PASSTHROUGH),
                decision.unknownFacts());
    }

    /**
     * 中文：返回一次表面决策的具体执行方法；被拒绝的 AUTO 已由 decide() 变为 NONE。
     * English: Returns the concrete execution method for one surface decision; rejected AUTO has
     * already been converted to NONE by decide().
     */
    public static ConnectionMethod resolve(
            ConnectionMethod requested,
            InferenceFacts facts,
            boolean equalStateBoundarySuppressed) {
        return decide(requested, facts, equalStateBoundarySuppressed)
                .resolvedMethod()
                .orElse(ConnectionMethod.NONE);
    }
}
