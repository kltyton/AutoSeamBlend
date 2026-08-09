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
 *
 * @param textureId 中文：表面对应的纹理 ID。 / English: Texture id of the surface.
 * @param frameProfile 中文：冻结的纹理帧轮廓。 / English: Frozen texture frame profile.
 * @param overlayProfile 中文：冻结的覆盖层裁剪轮廓。 / English: Frozen overlay cutout profile.
 * @param overlayReceiverBlockIds 中文：覆盖层接收方方块 ID 列表。 / English: Block ids receiving the overlay.
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
