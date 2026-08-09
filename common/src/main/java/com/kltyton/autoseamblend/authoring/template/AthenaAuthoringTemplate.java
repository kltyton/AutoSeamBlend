package com.kltyton.autoseamblend.authoring.template;

import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringFile;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringRule;
import com.kltyton.autoseamblend.export.io.CanonicalJson;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 中文：在 Athena 自有资源目录下创建 Athena 4.0.6 原生文档。 / English: Creates an Athena 4.0.6 native document under its own resource directory. */
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
        // 中文：保留 26.1.2 已验收的原生连接合同：同方块自连接；Athena 4.0.6 的
        // CtmUtils.parseCondition 仍解析 connect_to，不能随五角色载体迁移删除。
        // English: Preserves the 26.1.2-accepted native connection contract: same-block
        // self-connection; Athena 4.0.6's CtmUtils.parseCondition still parses connect_to,
        // so it must not be dropped during the five-role carrier migration.
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
        LinkedHashMap<String, Object> textures =
                new LinkedHashMap<>();
        for (String key : List.of(
                "particle",
                "empty",
                "center",
                "vertical",
                "horizontal")) {
            textures.put(key, rule.sourceTextureId());
        }
        return textures;
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
