package com.kltyton.autoseamblend.compat.ctm_mod.authoring.export;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kltyton.autoseamblend.authoring.export.NativeDocumentSnapshot;
import com.kltyton.autoseamblend.authoring.document.NativeDocumentOperations;
import com.kltyton.autoseamblend.engine.EngineFamily;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 中文：移除 NeoForge 独占 CTM Mod 文档的 AutoSeamBlend 创作扩展。
 * English: Removes AutoSeamBlend authoring extensions from NeoForge-only CTM Mod documents.
 */
public final class CtmModNativeDocumentBaker {
    private CtmModNativeDocumentBaker() {}

    public static byte[] bakedPassthrough(NativeDocumentSnapshot document) throws IOException {
        Objects.requireNonNull(document, "document");
        if (document.family() != EngineFamily.CTM_MOD) {
            throw new IllegalArgumentException("CTM_MOD_DOCUMENT_REQUIRED");
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(
                    StandardCharsets.UTF_8.newDecoder()
                            .decode(ByteBuffer.wrap(document.resolve(
                                    NativeDocumentOperations.shared())))
                            .toString());
        } catch (CharacterCodingException | RuntimeException exception) {
            throw new IOException("CTM_MOD_DOCUMENT_JSON_INVALID", exception);
        }
        if (!(parsed instanceof JsonObject root)) {
            throw new IOException("CTM_MOD_DOCUMENT_JSON_INVALID");
        }
        root.remove("autoseamblend");
        return (root.toString() + '\n').getBytes(StandardCharsets.UTF_8);
    }
}
