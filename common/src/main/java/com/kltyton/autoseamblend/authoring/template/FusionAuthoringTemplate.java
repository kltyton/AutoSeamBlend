package com.kltyton.autoseamblend.authoring.template;

import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringFile;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringRule;
import com.kltyton.autoseamblend.export.io.CanonicalJson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 中文：创建 Fusion 原生纹理元数据、模型和模型修改器文档。 / English: Creates Fusion-native texture metadata, model, and model-modifier documents. */
final class FusionAuthoringTemplate {
    private FusionAuthoringTemplate() {}

    static List<ManagedAuthoringFile> create(
            ManagedAuthoringRule rule) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("loader", "fusion:model");
        model.put("type", "base");
        model.put("parent", rule.originalModelId());
        if (!rule.sourceTextureKeys().isEmpty()) {
            LinkedHashMap<String, Object> textures =
                    new LinkedHashMap<>();
            for (String key
                    : rule.sourceTextureKeys()) {
                textures.put(
                        key,
                        rule.sourceTextureId());
            }
            model.put("textures", textures);
        }
        model.put(
                "method",
                rule.requestedMethod().serializedName());
        model.put(
                "compatibility",
                rule.compatibility());

        String modelId = "autoseamblend:block/"
                + rule.managedStem();
        Map<String, Object> modifier = new LinkedHashMap<>();
        modifier.put("id", rule.targetBlockId());
        modifier.put("targets", List.of(rule.targetBlockId()));
        modifier.put(
                "default_model_overrides",
                List.of(modelId));
        modifier.put(
                "method",
                rule.requestedMethod().serializedName());
        modifier.put(
                "compatibility",
                rule.compatibility());

        String stem = rule.managedStem();
        ArrayList<ManagedAuthoringFile> documents =
                new ArrayList<>();
        Map<String, Object> fusion =
                new LinkedHashMap<>();
        fusion.put("type", "base");
        fusion.put(
                "method",
                rule.requestedMethod().serializedName());
        fusion.put(
                "compatibility",
                rule.compatibility());
        documents.add(ManagedAuthoringFile.utf8(
                "assets/" + rule.textureNamespace()
                        + "/textures/"
                        + rule.texturePath()
                        + ".png.mcmeta",
                CanonicalJson.stringify(
                        Map.of("fusion", fusion))));
        documents.add(
                ManagedAuthoringFile.utf8(
                        "assets/autoseamblend/models/block/"
                                + stem + ".json",
                        CanonicalJson.stringify(model)));
        documents.add(
                ManagedAuthoringFile.utf8(
                        "assets/autoseamblend/fusion/model_modifiers/blocks/"
                                + stem + ".json",
                        CanonicalJson.stringify(modifier)));
        return List.copyOf(documents);
    }

}
