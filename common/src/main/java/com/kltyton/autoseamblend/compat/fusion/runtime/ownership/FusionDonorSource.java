package com.kltyton.autoseamblend.compat.fusion.runtime.ownership;

import com.kltyton.autoseamblend.engine.ownership.fusion.FusionExactEvidenceKey;
import com.supermartijn642.fusion.api.model.custom.quad.QuadAccess;
import java.util.Objects;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** 中文：安装遍历使用的冻结供体源。 / English: Frozen donor source for install traversal. */
public record FusionDonorSource(
        FusionExactEvidenceKey evidenceKey,
        QuadAccess quad,
        TextureAtlasSprite sprite) {
    public FusionDonorSource {
        Objects.requireNonNull(evidenceKey, "evidenceKey");
        Objects.requireNonNull(quad, "quad");
        Objects.requireNonNull(sprite, "sprite");
    }
}
