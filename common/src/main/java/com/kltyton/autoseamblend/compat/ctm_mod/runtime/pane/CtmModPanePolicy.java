package com.kltyton.autoseamblend.compat.ctm_mod.runtime.pane;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：冻结 CTM Mod 玻璃板终止面保护规则；模型/Quad 的实际发射仍由 NeoForge 桥接负责。
 *
 * <p>English: Freezes the CTM Mod pane terminator preservation rule while the NeoForge bridge
 * remains responsible for model/Quad emission.</p>
 */
public final class CtmModPanePolicy {
    private CtmModPanePolicy() {}

    /**
     * 中文：CTM 的 pane 主体仅替换水平侧面；剔除桶或顶/底面保留原材质。
     * English: CTM replaces only pane body side faces; cull buckets and top/bottom faces retain
     * their source material.
     */
    public static boolean preservesTerminator(
            BlockState state,
            ConnectionMethod method,
            Direction quadDirection,
            Direction cullFace) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(quadDirection, "quadDirection");
        return state.getBlock() instanceof IronBarsBlock
                && method == ConnectionMethod.CTM
                && (cullFace != null || quadDirection.getAxis() == Direction.Axis.Y);
    }
}
