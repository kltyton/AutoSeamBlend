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
