package com.kltyton.autoseamblend.compat.continuity.runtime;

import com.kltyton.autoseamblend.engine.plan.CompletionPlan;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;
import net.minecraft.world.level.block.Block;

/**
 * 中文：集中 Continuity 运行时的方法分类、补全门槛和连接判定。
 *
 * English: Centralizes Continuity runtime method classification, completion gates, and connection
 * decisions.
 */
public final class ContinuityMethodPolicy {
    private ContinuityMethodPolicy() {}

    public static boolean replacement(ConnectionMethod method) {
        return ContinuityNativeStatePolicy.replacementKind(method).isPresent();
    }

    /** 中文：把已解析方法收敛为一个运行时动作；English: Collapses a resolved method into one runtime action. */
    public static RuntimeAction action(ConnectionMethod method) {
        ConnectionMethod resolved = Objects.requireNonNull(method, "method");
        if (resolved == ConnectionMethod.AUTO) {
            throw new IllegalStateException("CONTINUITY_RUNTIME_METHOD_UNRESOLVED");
        }
        if (resolved == ConnectionMethod.NONE) {
            return RuntimeAction.PASSTHROUGH;
        }
        if (resolved == ConnectionMethod.FIXED) {
            return RuntimeAction.FIXED;
        }
        if (resolved == ConnectionMethod.TOP) {
            return RuntimeAction.TOP;
        }
        if (replacement(resolved)) {
            return RuntimeAction.REPLACEMENT;
        }
        if (overlay(resolved)) {
            return RuntimeAction.OVERLAY;
        }
        throw new IllegalStateException("CONTINUITY_RUNTIME_METHOD_UNMAPPED:" + resolved);
    }

    public static boolean overlay(ConnectionMethod method) {
        return method.overlayCapable();
    }

    public static boolean allowsAutoBlend(CompletionPlan completion) {
        return completion.outcome() == CompletionPlan.Outcome.FULL
                || completion.outcome() == CompletionPlan.Outcome.COMPLEMENT;
    }

    public static boolean connects(
            ConnectionRuleSet<Block> rules,
            Block current,
            Block neighbor) {
        return rules.isTarget(current)
                ? rules.connects(current, neighbor)
                : current == neighbor;
    }

    public static boolean receivesOverlay(
            ConnectionRuleSet<Block> rules,
            Block donor,
            Block receiver) {
        return donor != receiver
                && !rules.connects(receiver, donor);
    }

    public enum RuntimeAction {
        PASSTHROUGH,
        FIXED,
        TOP,
        REPLACEMENT,
        OVERLAY
    }
}
