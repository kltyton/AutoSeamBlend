package com.kltyton.autoseamblend.compat.ctm_mod.authoring.template;

import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringFile;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringRule;
import com.kltyton.autoseamblend.export.io.CanonicalJson;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 中文：创建 CTM Lib 原生 blockstate custom model 及其纹理槽模型。
 *
 * English:
 * Creates CTM Lib's native blockstate custom model and its referenced texture-slot model.
 */
public final class CtmModAuthoringTemplate {
    private static final String MODEL_TYPE =
            "ctm:connected_texture_model";

    private CtmModAuthoringTemplate() {}

    public static List<ManagedAuthoringFile> create(
            ManagedAuthoringRule rule) {
        String modelId = "autoseamblend:block/ctm_mod/"
                + rule.managedStem();
        String carrierPrefix =
                "autoseamblend:generated/ctm_mod/"
                        + rule.resolvedMethod()
                                .serializedName()
                        + '/'
                        + rule.managedStem();

        LinkedHashMap<String, Object> extension =
                new LinkedHashMap<>();
        extension.put("id", rule.targetBlockId());
        extension.put("selector", rule.targetBlockId());
        extension.put(
                "method",
                rule.requestedMethod()
                        .serializedName());
        extension.put(
                "compatibility",
                rule.compatibility());

        LinkedHashMap<String, Object> variant =
                new LinkedHashMap<>();
        variant.put("block", rule.targetBlockId());
        variant.put(
                "kind",
                nativeKind(rule.resolvedMethod()));
        variant.put("water_offset", false);

        LinkedHashMap<String, Object> customModel =
                new LinkedHashMap<>();
        customModel.put("type", MODEL_TYPE);
        customModel.put("model_location", modelId);
        customModel.put(
                "element",
                element());
        customModel.put(
                "connected_faces",
                List.of(
                        "down",
                        "up",
                        "north",
                        "south",
                        "west",
                        "east"));
        customModel.put(
                "render_overlay_on_all_faces",
                false);
        customModel.put("variant", variant);
        customModel.put("base_tint_index", -1);
        customModel.put("base_emissivity", 0);
        customModel.put("tint_index", -1);
        customModel.put("emissivity", 0);
        customModel.put("eldritch", false);

        LinkedHashMap<String, Object> variants =
                new LinkedHashMap<>();
        variants.put("", customModel);
        LinkedHashMap<String, Object> blockstate =
                new LinkedHashMap<>();
        blockstate.put("autoseamblend", extension);
        blockstate.put("variants", variants);

        LinkedHashMap<String, Object> textures =
                new LinkedHashMap<>();
        textures.put("particle", rule.sourceTextureId());
        textures.put(
                "base_texture",
                rule.sourceTextureId());
        addCarrierTextures(
                textures,
                rule.resolvedMethod(),
                carrierPrefix);
        LinkedHashMap<String, Object> model =
                new LinkedHashMap<>();
        model.put("parent", rule.originalModelId());
        model.put("textures", textures);

        return List.of(
                ManagedAuthoringFile.utf8(
                        "assets/"
                                + rule.targetNamespace()
                                + "/blockstates/"
                                + rule.targetPath()
                                + ".json",
                        CanonicalJson.stringify(blockstate)),
                ManagedAuthoringFile.utf8(
                        "assets/autoseamblend/models/block/ctm_mod/"
                                + rule.managedStem()
                                + ".json",
                        CanonicalJson.stringify(model)));
    }

    private static LinkedHashMap<String, Object> element() {
        LinkedHashMap<String, Object> element =
                new LinkedHashMap<>();
        element.put("min", List.of(0, 0, 0));
        element.put("max", List.of(16, 16, 16));
        return element;
    }

    private static String nativeKind(
            com.kltyton.autoseamblend.selection.method.ConnectionMethod
                    method) {
        return switch (method) {
            case HORIZONTAL -> "bookshelf";
            case VERTICAL -> "ctmv";
            case AUTO -> throw new IllegalArgumentException(
                    "auto must be resolved before CTM authoring");
            case RUNTIME_BLEND, CTM, CTM_COMPACT,
                    HORIZONTAL_VERTICAL,
                    VERTICAL_HORIZONTAL, TOP, OVERLAY,
                    OVERLAY_CTM, FIXED, NONE -> "standard";
        };
    }

    private static void addCarrierTextures(
            LinkedHashMap<String, Object> textures,
            com.kltyton.autoseamblend.selection.method.ConnectionMethod
                    method,
            String carrierPrefix) {
        switch (method) {
            case HORIZONTAL -> textures.put(
                    "overlay_horizontal",
                    carrierPrefix + "/horizontal");
            case VERTICAL -> {
                String carrier = carrierPrefix + "/vertical";
                textures.put("overlay_vertical", carrier);
                textures.put("overlay_side", carrier);
                textures.put("overlay_top", carrier);
                textures.put("overlay_bottom", carrier);
            }
            case TOP -> textures.put(
                    "base_texture",
                    carrierPrefix + "/top");
            case RUNTIME_BLEND, CTM, CTM_COMPACT,
                    HORIZONTAL_VERTICAL,
                    VERTICAL_HORIZONTAL, OVERLAY,
                    OVERLAY_CTM -> {
                textures.put(
                        "overlay_texture",
                        carrierPrefix + "/disconnected");
                textures.put(
                        "overlay_connected",
                        carrierPrefix + "/connected");
            }
            case FIXED -> textures.put(
                    "base_texture",
                    carrierPrefix + "/fixed");
            case NONE -> {
        // 中文：原生透传成功；CTM 模型只烘焙自身的基础面。
        // English: Successful native passthrough; the CTM model bakes only its base faces.
            }
            case AUTO -> throw new IllegalArgumentException(
                    "auto must be resolved before CTM authoring");
        }
    }
}
