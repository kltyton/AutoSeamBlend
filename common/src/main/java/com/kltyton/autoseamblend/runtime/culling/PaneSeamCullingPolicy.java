package com.kltyton.autoseamblend.runtime.culling;

import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.PreparedSurfaceMethods;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;
import net.minecraft.world.level.block.Block;

/**
 * 中文：判定玻璃板端盖模型是否应进入连接纹理剔除桶。
 *
 * <p>English: Decides whether a pane cap model belongs in the connected-texture culling bucket.
 */
public final class PaneSeamCullingPolicy {
    private PaneSeamCullingPolicy() {}

    public static boolean applies(
            Block block,
            RuleRuntime.Snapshot ruleSnapshot,
            PreparedSurfaceMethods.Snapshot preparedMethods,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(ruleSnapshot, "ruleSnapshot");
        Objects.requireNonNull(preparedMethods, "preparedMethods");
        Objects.requireNonNull(surfaces, "surfaces");
        ConnectionRuleSet<Block> rules = ruleSnapshot.rules();
        if (rules.isTarget(block)) {
            return rules.method(block) != ConnectionMethod.NONE;
        }
        if (!ruleSnapshot.automaticDiscovery()
                || rules.configuredSelector(block).isPresent()
                || rules.isExcluded(
                        block,
                        ConnectionMethod.AUTO,
                        ConnectionRuleSet.ResourcePackMode.COMPATIBILITY)) {
            return false;
        }
        return preparedMethods.autoMethod(block).isPresent()
                || surfaces.representative(block)
                        .map(value -> value.surface().inferredMethod() != ConnectionMethod.NONE)
                        .orElse(false);
    }
}
