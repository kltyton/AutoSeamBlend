package com.kltyton.autoseamblend.compat.athena.document;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：读取和生成 Athena 原生 JSON 中的 AutoSeamBlend 扩展视图。
 * English: Reads and produces AutoSeamBlend extension views in an Athena native JSON document.
 *
 * <p>Only JSON values and the project method enum cross this boundary; Athena's external model
 * classes remain in the Loader-specific compat packages.</p>
 */
public final class AthenaDocumentExtensions {
    public static final String METHOD = "method";
    public static final String COMPATIBILITY = "compatibility";
    public static final String LOADER = "athena:loader";
    public static final String PANE_LOADER = "athena:pane_ctm";
    public static final String CONNECT_CORNERS = "connect_corners";

    private AthenaDocumentExtensions() {}

    /**
     * 中文：解析 authoring 扩展；缺少两个键时返回空，不把普通 Athena 文档误判为项目文档。
     * English: Parses the authoring extension; an absent pair stays empty so ordinary Athena
     * documents are not mistaken for project-authored documents.
     */
    public static Optional<AuthorExtensions> read(JsonObject document) {
        Objects.requireNonNull(document, "document");
        JsonElement methodElement = document.get(METHOD);
        JsonElement compatibilityElement = document.get(COMPATIBILITY);
        if (methodElement == null && compatibilityElement == null) {
            return Optional.empty();
        }
        if (methodElement == null
                || !methodElement.isJsonPrimitive()
                || !methodElement.getAsJsonPrimitive().isString()
                || compatibilityElement == null
                || !compatibilityElement.isJsonPrimitive()
                || !compatibilityElement.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(
                    "Athena extensions require string method and boolean compatibility");
        }
        String serializedMethod = methodElement.getAsString();
        ConnectionMethod method = ConnectionMethod.parse(serializedMethod)
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown AutoSeamBlend method: " + serializedMethod));
        return Optional.of(new AuthorExtensions(
                method,
                compatibilityElement.getAsBoolean()));
    }

    /**
     * 中文：authoring 保留扩展；执行视图解析 concrete method、移除扩展，并为 pane 启用原生角连接。
     * English: Authoring retains extensions; the execution view resolves a concrete method,
     * removes the extensions, and enables Athena's native pane-corner path.
     */
    public static JsonObject executionView(
            JsonObject authoring,
            ConnectionMethod resolvedMethod) {
        Objects.requireNonNull(authoring, "authoring");
        Objects.requireNonNull(resolvedMethod, "resolvedMethod");
        if (resolvedMethod == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException("resolvedMethod must be concrete");
        }
        JsonObject execution = authoring.deepCopy();
        execution.remove(METHOD);
        execution.remove(COMPATIBILITY);
        if (PANE_LOADER.equals(string(execution, LOADER))) {
            execution.addProperty(CONNECT_CORNERS, true);
        }
        return execution;
    }

    /** 中文：判断当前文档是否是 Athena 原生 pane_ctm。 / English: Tests whether a document is an Athena native pane_ctm document. */
    public static boolean isPane(JsonObject document) {
        Objects.requireNonNull(document, "document");
        return PANE_LOADER.equals(string(document, LOADER));
    }

    /**
     * 中文：判断 Athena 文档是否使用四切片纹理对象。
     * English: Tests whether an Athena document uses the four-slice texture object form.
     */
    public static boolean isFourSlice(JsonObject textures) {
        Objects.requireNonNull(textures, "textures");
        return textures.has("center")
                || textures.has("vertical")
                || textures.has("horizontal")
                || textures.has("empty");
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : "";
    }

    public record AuthorExtensions(
            ConnectionMethod requestedMethod,
            boolean compatibility) {
        public AuthorExtensions {
            Objects.requireNonNull(requestedMethod, "requestedMethod");
        }
    }
}
