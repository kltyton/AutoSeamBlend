package com.kltyton.autoseamblend.authoring.export;

import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import com.kltyton.autoseamblend.texture.mask.TextureFrameProfile;
import java.util.List;
import java.util.Objects;

/**
 * 中文：捕获阶段冻结的纯项目表面轮廓；不持有 Loader、Atlas、模型或引擎对象。
 *
 * English: Pure project surface profile frozen during capture. It holds no
 * loader, atlas, model, or engine object.
 */
public record ExportSurfaceSnapshot(
        String textureId,
        TextureFrameProfile frameProfile,
        OverlayCutoutProfile overlayProfile,
        List<String> overlayReceiverBlockIds) {
    public ExportSurfaceSnapshot {
        if (textureId == null || textureId.isBlank()) {
            throw new IllegalArgumentException("textureId must not be blank");
        }
        Objects.requireNonNull(frameProfile, "frameProfile");
        Objects.requireNonNull(overlayProfile, "overlayProfile");
        overlayReceiverBlockIds = List.copyOf(
                Objects.requireNonNull(overlayReceiverBlockIds, "overlayReceiverBlockIds"));
    }
}
