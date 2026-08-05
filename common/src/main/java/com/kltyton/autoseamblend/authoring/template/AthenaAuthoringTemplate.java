package com.kltyton.autoseamblend.authoring.template;

import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringFile;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringRule;
import com.kltyton.autoseamblend.export.io.CanonicalJson;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 中文：在 Athena 自有资源目录下创建 Athena 4.7.3 原生文档。 / English: Creates an Athena 4.7.3 native document under its own resource directory. */
final class AthenaAuthoringTemplate {
    private AthenaAuthoringTemplate() {}

    static List<ManagedAuthoringFile> create(
            ManagedAuthoringRule rule) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", rule.targetBlockId());
        root.put(
                "athena:loader",
                rule.pane()
                        ? "athena:pane_ctm"
                        : "athena:ctm");
        root.put(
                "ctm_textures",
                rule.pane()
                        ? paneTextures(rule.sourceTextureId())
                        : regularTextures(rule));
        root.put(
                "connect_to",
                Map.of("type", "sameBlock"));
        root.put(
                "method",
                rule.requestedMethod().serializedName());
        root.put(
                "compatibility",
                rule.compatibility());
        return List.of(
                ManagedAuthoringFile.utf8(
                        "assets/" + rule.targetNamespace()
                                + "/athena/"
                                + rule.targetPath() + ".json",
                        CanonicalJson.stringify(root)));
    }

    private static Object regularTextures(
            ManagedAuthoringRule rule) {
        return rule.sourceTextureId();
    }

    private static Map<String, Object> paneTextures(
            String sourceTextureId) {
        LinkedHashMap<String, Object> textures =
                new LinkedHashMap<>();
        for (String key : List.of(
                "particle",
                "empty",
                "center",
                "vertical",
                "horizontal",
                "edge",
                "side_edge")) {
            textures.put(key, sourceTextureId);
        }
        return textures;
    }
}
