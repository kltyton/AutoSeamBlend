package com.kltyton.autoseamblend.runtime.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** 中文：在供体方块纹理输出到另一方块前解析其世界染色。 / English: Resolves a donor block's world tint before its texture is emitted on another block. */
public final class DonorTintResolver {
    private DonorTintResolver() {}

    public static int resolve(
            BlockState donorState,
            BlockAndTintGetter level,
            BlockPos pos,
            int tintIndex) {
        if (tintIndex < 0) {
            return -1;
        }
        int color = Minecraft.getInstance()
                .getBlockColors()
                .getColor(
                        donorState,
                        level,
                        pos,
                        tintIndex);
        if (color == -1) {
            return -1;
        }
        return 0xFF000000
                | color;
    }
}
