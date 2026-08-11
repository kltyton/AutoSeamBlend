package com.kltyton.autoseamblend.authoring.storage;

import java.nio.charset.StandardCharsets;

/**
 * 中文：AutoSeamBlend Managed 工作区的公共 pack 元数据；Loader 只提供布局与激活。
 *
 * English:
 * Common pack metadata for the AutoSeamBlend Managed workspace; a Loader
 * supplies only the layout and activation.
 */
public final class ManagedPackMetadata {
    public static final int RESOURCE_PACK_FORMAT = 15;

    private ManagedPackMetadata() {}

    public static byte[] defaultPackMetadata() {
        return ("{\n"
                        + "  \"pack\": {\n"
                        + "    \"pack_format\": "
                        + RESOURCE_PACK_FORMAT
                        + ",\n"
                        + "    \"description\": \"AutoSeamBlend Managed\"\n"
                        + "  }\n"
                        + "}\n")
                .getBytes(StandardCharsets.UTF_8);
    }
}
