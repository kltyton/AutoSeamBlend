package com.kltyton.autoseamblend.texture.profile;

import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import com.kltyton.autoseamblend.texture.mask.TextureFrameProfile;
import java.util.Objects;

/**
 * 中文：首轮 Atlas 源图像生成的不可变框架与 overlay 配置档。
 *
 * English: Immutable frame and overlay profiles generated from an initial Atlas source image.
 */
public record InitialTextureProfiles(
        TextureFrameProfile frame,
        OverlayCutoutProfile overlay) {
    public InitialTextureProfiles {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(overlay, "overlay");
    }

    /**
     * 中文：把模型 tint 事实合入冻结轮廓；English: folds the model tint fact into the frozen
     * overlay profile.
     */
    public OverlayCutoutProfile overlay(boolean tinted) {
        return tinted
                ? overlay.forTintedSurface()
                : overlay;
    }
}
