package com.kltyton.autoseamblend.compat.continuity.runtime;

import me.pepperbell.continuity.api.client.CachingPredicates;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：让 AutoBlend 处理器参与 Continuity 原生首遍切片；精确表面过滤在冻结运行时目录中完成。
 *
 * English: Keeps the AutoBlend processor in Continuity's native first-pass slice; exact-surface
 * filtering is performed by the frozen runtime catalog.
 */
public final class ContinuityCachingPredicates implements CachingPredicates {
    public static final ContinuityCachingPredicates INSTANCE =
            new ContinuityCachingPredicates();

    private ContinuityCachingPredicates() {}

    @Override
    public boolean affectsSprites() {
        return false;
    }

    @Override
    public boolean affectsSprite(TextureAtlasSprite sprite) {
        return false;
    }

    @Override
    public boolean affectsBlockStates() {
        return false;
    }

    @Override
    public boolean affectsBlockState(BlockState state) {
        return false;
    }

    @Override
    public boolean isValidForMultipass() {
        return false;
    }
}
