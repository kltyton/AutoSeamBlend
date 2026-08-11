package com.kltyton.autoseamblend.compat.fusion.runtime;

import java.util.Objects;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：统一把 Fusion 文档定位归一化为完整文件 ID；Fabric 与 NeoForge 共享同一转换语义。
 *
 * English:
 * Normalizes a Fusion document location to its full file ID so Fabric and NeoForge share one
 * conversion semantic.
 */
public final class FusionModifierDocumentLocation {
    private static final FileToIdConverter ID_CONVERTER =
            FileToIdConverter.json(
                    "fusion/model_modifiers/blocks");

    private FusionModifierDocumentLocation() {}

    public static ResourceLocation resourceId(ResourceLocation id) {
        return ID_CONVERTER.idToFile(
                Objects.requireNonNull(
                        id,
                        "id"));
    }
}
