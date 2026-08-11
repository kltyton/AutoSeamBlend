package com.kltyton.autoseamblend.authoring.storage;

import java.util.Objects;
import net.minecraft.server.packs.PackResources;

/**
 * 中文：唯一持久 AutoSeamBlend 创作资源包的 Loader 中立规范标识。
 *
 * English: Loader-neutral canonical identity of the one persistent
 * AutoSeamBlend authoring pack.
 */
public final class ManagedPackIdentity {
    public static final String DISPLAY_NAME = "AutoSeamBlend Managed";
    public static final String REPOSITORY_ID = "file/" + DISPLAY_NAME;

    private ManagedPackIdentity() {}

    public static boolean matches(PackResources pack) {
        Objects.requireNonNull(pack, "pack");
        // 1.20.1 PackResources has no location(); packId() is the canonical identity string.
        return matchesId(pack.packId());
    }

    public static boolean matchesId(String packId) {
        Objects.requireNonNull(packId, "packId");
        return packId.equals(REPOSITORY_ID)
                || packId.equals(DISPLAY_NAME)
                || packId.endsWith('/' + DISPLAY_NAME);
    }
}
