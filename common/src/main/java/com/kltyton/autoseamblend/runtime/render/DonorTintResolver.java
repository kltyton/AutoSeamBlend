package com.kltyton.autoseamblend.runtime.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
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
        BlockTintSource source = Minecraft.getInstance()
                .getBlockColors()
                .getTintSource(
                        donorState,
                        tintIndex);
        if (source == null) {
            return -1;
        }
        return 0xFF000000
                | source.colorInWorld(
                        donorState,
                        level,
                        pos);
    }
}
