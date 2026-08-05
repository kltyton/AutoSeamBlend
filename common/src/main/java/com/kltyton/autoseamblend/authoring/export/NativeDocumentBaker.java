package com.kltyton.autoseamblend.authoring.export;

import com.kltyton.autoseamblend.authoring.property.NativePropertyPatchApplier;
import com.kltyton.autoseamblend.engine.EngineFamily;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * 中文：原生格式的 baked 变换；仅处理 Common 文档快照和冻结字节。
 * English: Native-format baked transforms over Common document snapshots and frozen bytes.
 */
public final class NativeDocumentBaker {
    private NativeDocumentBaker() {}

    /** 中文：移除 AutoSeamBlend 扩展并保留具体原生 method。 / English: Removes AutoSeamBlend extensions while retaining a concrete native method. */
    public static byte[] bakedPassthrough(
            NativeDocumentSnapshot document) throws IOException {
        Objects.requireNonNull(document, "document");
        byte[] authoring = document.resolve();
        if (document.family() != EngineFamily.MCPATCHER
                && document.family() != EngineFamily.FUSION
                && document.family() != EngineFamily.ATHENA) {
            throw new IOException("LOADER_EXCLUSIVE_BAKER_REQUIRES_ADAPTER");
        }
        LinkedHashMap<String, Optional<String>> values = new LinkedHashMap<>();
        values.put("id", Optional.empty());
        values.put("compatibility", Optional.empty());
        if (document.family() != EngineFamily.MCPATCHER) {
            values.put("method", Optional.empty());
        } else {
            Properties properties = new Properties();
            properties.load(new StringReader(
                    new String(authoring, StandardCharsets.UTF_8)));
            String method = properties.getProperty("method", "");
            if (method.equals("auto") || method.equals("none")) {
                values.put("method", Optional.empty());
            }
        }
        return NativePropertyPatchApplier.resolve(
                document.family(),
                document.documentPath(),
                authoring,
                Collections.unmodifiableMap(values));
    }

    /** 中文：对 Fusion companion 去除运行时扩展。 / English: Removes runtime extensions from Fusion companion documents. */
    public static byte[] bakedCompanion(
            NativeDocumentSnapshot document,
            String path,
            byte[] source) throws IOException {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(path, "path");
        byte[] captured = Objects.requireNonNull(source, "source").clone();
        if (document.family() != EngineFamily.FUSION
                || (!path.endsWith(".json")
                        && !path.endsWith(".png.mcmeta"))) {
            return captured;
        }
        return NativePropertyPatchApplier.resolve(
                document.family(),
                path,
                captured,
                Map.of(
                        "method",
                        Optional.empty(),
                        "compatibility",
                        Optional.empty()));
    }

}
