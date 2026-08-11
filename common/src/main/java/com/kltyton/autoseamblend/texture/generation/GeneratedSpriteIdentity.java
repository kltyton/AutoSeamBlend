package com.kltyton.autoseamblend.texture.generation;

import com.kltyton.autoseamblend.foundation.Constants;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：识别 AutoSeamBlend 自有生成精灵，阻止生成资源递归作为源材质。
 *
 * English: Identifies AutoSeamBlend-generated sprites so generated resources cannot recursively
 * become source material.
 */
public final class GeneratedSpriteIdentity {
    private GeneratedSpriteIdentity() {}

    public static boolean isGenerated(ResourceLocation source) {
        ResourceLocation checked = Objects.requireNonNull(source, "source");
        return Constants.MOD_ID.equals(checked.getNamespace())
                && checked.getPath().startsWith("generated/");
    }
}
