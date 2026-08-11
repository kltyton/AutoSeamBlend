package com.kltyton.autoseamblend.authoring.preview;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import com.kltyton.autoseamblend.texture.mask.TextureFrameProfile;
import java.util.Objects;

/**
 * 中文：Loader 适配器提供给公共面预览组合器的不可变供体样本。
 *
 * English: Immutable donor sample supplied by a Loader adapter to the common
 * face-preview composer.
 *
 * @param sourceKey 中文：供体来源键。 / English: Donor source key.
 * @param connections 中文：邻接状态。 / English: Neighbor connections.
 * @param renderMethod 中文：渲染方法（必须为具体方法）。 / English: Render method (must be concrete).
 * @param frameProfile 中文：帧轮廓。 / English: Frame profile.
 * @param overlayProfile 中文：覆盖层轮廓。 / English: Overlay profile.
 * @param tint 中文：ARGB 着色值。 / English: ARGB tint value.
 */
public record PreviewCompositionSample(
        String sourceKey,
        NeighborConnections connections,
        ConnectionMethod renderMethod,
        TextureFrameProfile frameProfile,
        OverlayCutoutProfile overlayProfile,
        int tint) {
    public PreviewCompositionSample {
        if (sourceKey == null || sourceKey.isBlank()) {
            throw new IllegalArgumentException(
                    "preview sourceKey must not be blank");
        }
        connections = Objects.requireNonNull(
                connections,
                "connections");
        renderMethod = Objects.requireNonNull(
                renderMethod,
                "renderMethod");
        if (renderMethod == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException(
                    "preview render method must be resolved");
        }
        frameProfile = Objects.requireNonNull(
                frameProfile,
                "frameProfile");
        overlayProfile = Objects.requireNonNull(
                overlayProfile,
                "overlayProfile");
    }
}
