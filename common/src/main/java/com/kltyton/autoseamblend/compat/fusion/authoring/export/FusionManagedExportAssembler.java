package com.kltyton.autoseamblend.compat.fusion.authoring.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringFile;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringProject;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringRule;
import com.kltyton.autoseamblend.authoring.template.ManagedAuthoringTemplates;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.export.managed.ManagedExportIr;
import com.kltyton.autoseamblend.authoring.export.ExportSurfaceSnapshot;
import com.kltyton.autoseamblend.authoring.export.ManagedExportDocumentAssembly;
import com.kltyton.autoseamblend.authoring.export.NativeDocumentSnapshot;
import com.kltyton.autoseamblend.authoring.export.NativeDocumentBaker;
import com.kltyton.autoseamblend.authoring.materialize.TextureSourceSnapshot;
import com.kltyton.autoseamblend.authoring.materialize.TextureSourceSnapshot.MaterializedTexture;
import com.kltyton.autoseamblend.authoring.materialize.TextureSourceSnapshot.SheetTile;
import com.kltyton.autoseamblend.compat.fusion.texture.generation.FusionNativeSheetPlan;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 中文：为一个表面构建 Fusion 原生可编辑资源和独立 baked 资源。 / English: Builds Fusion-native editable and standalone baked resources for one surface. */
public final class FusionManagedExportAssembler {
    private FusionManagedExportAssembler() {}

    public static ManagedExportIr.Rule assemble(
            int order,
            ManagedAuthoringRule rule,
            ExportSurfaceSnapshot surface,
            TextureSourceSnapshot source,
            Optional<TextureSourceSnapshot>
                    topSource,
            Optional<NativeDocumentSnapshot>
                    nativeDocument)
            throws IOException {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(topSource, "topSource");
        Objects.requireNonNull(
                nativeDocument,
                "nativeDocument");
        if (!source.sourceTextureId()
                .equals(rule.sourceTextureId())) {
            throw new IllegalArgumentException(
                    "EXPORT_SOURCE_TEXTURE_CHANGED");
        }

        ManagedAuthoringProject template =
                ManagedAuthoringTemplates.create(
                        EngineFamily.FUSION,
                        List.of(rule));
        ManagedAuthoringFile modifier = find(
                template,
                "/fusion/model_modifiers/blocks/");
        NativeDocumentSnapshot principal =
                ManagedExportDocumentAssembly.principal(
                        EngineFamily.FUSION,
                        modifier,
                        nativeDocument);
        if (rule.resolvedMethod()
                == ConnectionMethod.NONE) {
            return none(
                    order,
                    rule,
                    principal);
        }

        List<Integer> slots =
                FusionNativeSheetPlan.logicalSlots(
                        rule.resolvedMethod());
        String sheetResource = sheetResource(rule);
        String sheetPath = "assets/autoseamblend/textures/"
                + sheetResource.substring(
                        sheetResource.indexOf(':') + 1)
                + ".png";
        MaterializedTexture materialized;
        String layout = null;
        if (rule.resolvedMethod()
                == ConnectionMethod.FIXED) {
            materialized = source.materialize(
                    GeneratedTileRecipe.Source.INSTANCE);
        } else {
            FusionNativeSheetPlan sheet =
                    FusionNativeSheetPlan.create(
                            rule.resolvedMethod());
            layout = sheet.layout();
            materialized = source.materializeCompositeSheet(
                    sheet.tileColumns(),
                    sheet.tileRows(),
                    compositeTiles(
                            rule.resolvedMethod(),
                            source,
                            topSource,
                            sheet,
                            surface.overlayProfile()));
        }

        ArrayList<String> blockers =
                new ArrayList<>();
        if (rule.sourceTextureKeys().isEmpty()) {
            blockers.add(
                    "FUSION_SOURCE_TEXTURE_KEY_UNRESOLVED");
        }
        ArrayList<ManagedExportIr.Document> documents =
                documents(
                        rule,
                        template,
                        principal,
                        sheetResource,
                        layout,
                        materialized,
                        source.sourceMetadata());
        ManagedExportIr.Tile tile =
                new ManagedExportIr.Tile(
                        sheetPath,
                        sheetPath,
                        slots,
                        ManagedExportIr.Tile.Source.GENERATED,
                        materialized.png());
        return new ManagedExportIr.Rule(
                order,
                rule.targetBlockId(),
                rule.targetBlockId(),
                documents,
                rule.requestedMethod()
                        .serializedName(),
                rule.resolvedMethod()
                        .serializedName(),
                List.of(
                        "fusion-native-layout:"
                                + (layout == null
                                        ? "base"
                                        : layout)),
                slots,
                Map.of(),
                slots,
                blockers,
                List.of(tile));
    }

    private static List<Optional<SheetTile>>
            compositeTiles(
                    ConnectionMethod method,
                    TextureSourceSnapshot source,
                    Optional<TextureSourceSnapshot>
                            topSource,
                    FusionNativeSheetPlan sheet,
                    OverlayCutoutProfile overlayProfile) {
        TextureSourceSnapshot
                resolvedTop = method
                                == ConnectionMethod.TOP
                        ? topSource.orElseThrow(
                                () -> new IllegalStateException(
                                        "TOP_SOURCE_SURFACE_UNRESOLVED"))
                        : source;
        ArrayList<Optional<SheetTile>> tiles =
                new ArrayList<>(
                        sheet.tileRecipes().size());
        for (int tile = 0;
                tile < sheet.tileRecipes().size();
                tile++) {
            Optional<GeneratedTileRecipe> recipe =
                    sheet.tileRecipes().get(tile);
            TextureSourceSnapshot
                    tileSource =
                            sheet.topSourceTiles()
                                            .get(tile)
                                    ? resolvedTop
                                    : source;
            tiles.add(recipe.map(value ->
                    new SheetTile(
                            tileSource,
                            value,
                            overlayProfile)));
        }
        return List.copyOf(tiles);
    }

    private static ManagedExportIr.Rule none(
            int order,
            ManagedAuthoringRule rule,
            NativeDocumentSnapshot principal)
            throws IOException {
        byte[] authoring = principal.resolve(
                Map.of(
                        "method",
                        Optional.of(jsonString(
                                rule.requestedMethod()
                                        .serializedName())),
                        "compatibility",
                        Optional.of(Boolean.toString(
                                rule.compatibility()))));
        byte[] baked = NativeDocumentBaker.bakedPassthrough(principal);
        ArrayList<ManagedExportIr.Document> documents =
                new ArrayList<>();
        documents.add(new ManagedExportIr.Document(
                principal.documentPath(),
                authoring,
                baked));
        ManagedExportDocumentAssembly.appendCompanionDocuments(
                documents,
                principal,
                Set.of(),
                NativeDocumentBaker::bakedCompanion);
        return new ManagedExportIr.Rule(
                order,
                rule.targetBlockId(),
                rule.targetBlockId(),
                documents,
                rule.requestedMethod()
                        .serializedName(),
                rule.resolvedMethod()
                        .serializedName(),
                List.of("explicit-none"),
                List.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static ArrayList<ManagedExportIr.Document>
            documents(
            ManagedAuthoringRule rule,
            ManagedAuthoringProject template,
            NativeDocumentSnapshot principal,
            String sheetResource,
            String layout,
            MaterializedTexture materialized,
            byte[] sourceMetadata)
            throws IOException {
        ManagedAuthoringFile modelFile = find(
                template,
                "/models/block/");

        JsonObject authoringModel = sourceModel(
                principal,
                modelFile);
        authoringModel.addProperty(
                "type",
                layout == null
                        ? "base"
                        : "connecting");
        authoringModel.add(
                "textures",
                textures(
                        rule.sourceTextureKeys(),
                        sheetResource));
        if (layout == null) {
            authoringModel.remove("connections");
        } else {
            authoringModel.add(
                    "connections",
                    connectionPredicate());
        }
        JsonObject bakedModel =
                authoringModel.deepCopy();
        stripExtensions(bakedModel);

        JsonObject authoringModifier = parse(
                principal.resolve(
                        Map.of(
                                "method",
                                Optional.of(jsonString(
                                        rule.requestedMethod()
                                                .serializedName())),
                                "compatibility",
                                Optional.of(Boolean.toString(
                                        rule.compatibility())))));
        JsonObject bakedModifier = parse(
                    NativeDocumentBaker.bakedPassthrough(principal));
        JsonArray generatedModels = new JsonArray();
        generatedModels.add(
                modelResource(
                        modelFile.relativePath()));
        authoringModifier.add(
                "default_model_overrides",
                generatedModels.deepCopy());
        bakedModifier.add(
                "default_model_overrides",
                generatedModels);

        JsonObject authoringMetadata =
                textureMetadata(
                        rule,
                        layout,
                        materialized,
                        sourceMetadata,
                        true);
        JsonObject bakedMetadata =
                textureMetadata(
                        rule,
                        layout,
                        materialized,
                        sourceMetadata,
                        false);
        String metadataPath =
                "assets/autoseamblend/textures/"
                        + sheetResource.substring(
                                sheetResource.indexOf(':') + 1)
                        + ".png.mcmeta";

        ArrayList<ManagedExportIr.Document> documents =
                new ArrayList<>();
        documents.add(new ManagedExportIr.Document(
                metadataPath,
                bytes(authoringMetadata),
                metadataPath,
                bytes(bakedMetadata)));
        documents.add(new ManagedExportIr.Document(
                modelFile.relativePath(),
                bytes(authoringModel),
                modelFile.relativePath(),
                bytes(bakedModel)));
        documents.add(new ManagedExportIr.Document(
                principal.documentPath(),
                bytes(authoringModifier),
                principal.documentPath(),
                bytes(bakedModifier)));
        ManagedExportDocumentAssembly.appendCompanionDocuments(
                documents,
                principal,
                Set.of(
                        metadataPath,
                        modelFile.relativePath()),
                NativeDocumentBaker::bakedCompanion);
        return documents;
    }

    private static JsonObject sourceModel(
            NativeDocumentSnapshot principal,
            ManagedAuthoringFile fallback)
            throws IOException {
        JsonObject modifier = parse(
                principal.resolve());
        JsonElement overrides = modifier.get(
                "default_model_overrides");
        if (overrides instanceof JsonArray values) {
            for (JsonElement value : values) {
                if (!value.isJsonPrimitive()
                        || !value.getAsJsonPrimitive()
                                .isString()) {
                    continue;
                }
                String modelPath = modelPath(
                        value.getAsString(),
                        namespace(
                                principal.documentPath()));
                byte[] captured =
                        principal.companionDocuments()
                                .get(modelPath);
                if (captured != null) {
                    return parse(captured);
                }
            }
        }
        return parse(fallback.content());
    }

    private static String modelPath(
            String modelId,
            String defaultNamespace) {
        int separator = modelId.indexOf(':');
        String namespace = separator < 0
                ? defaultNamespace
                : modelId.substring(0, separator);
        String path = separator < 0
                ? modelId
                : modelId.substring(separator + 1);
        return "assets/"
                + namespace
                + "/models/"
                + path
                + ".json";
    }

    private static String namespace(
            String documentPath) {
        String[] parts = documentPath.split("/", 3);
        if (parts.length < 3
                || !parts[0].equals("assets")) {
            throw new IllegalArgumentException(
                    "FUSION_DOCUMENT_RESOURCE_PATH_INVALID");
        }
        return parts[1];
    }

    private static String jsonString(String value) {
        return new com.google.gson.JsonPrimitive(value)
                .toString();
    }

    private static JsonObject textureMetadata(
            ManagedAuthoringRule rule,
            String layout,
            MaterializedTexture materialized,
            byte[] sourceMetadata,
            boolean authoring) {
        JsonObject root = sourceMetadata.length == 0
                ? new JsonObject()
                : parse(sourceMetadata);
        root.remove("fusion");
        JsonObject fusion = new JsonObject();
        fusion.addProperty(
                "type",
                layout == null
                        ? "base"
                        : "connecting");
        if (layout != null) {
            fusion.addProperty("layout", layout);
            fusion.add(
                    "connections",
                    connectionPredicate());
        }
        if (authoring) {
            fusion.addProperty(
                    "method",
                    rule.requestedMethod()
                            .serializedName());
            fusion.addProperty(
                    "compatibility",
                    rule.compatibility());
        }
        root.add("fusion", fusion);
        JsonElement animation = root.get("animation");
        if (materialized.animated()
                && animation instanceof JsonObject object) {
            object.addProperty(
                    "width",
                    materialized.frameWidth());
            object.addProperty(
                    "height",
                    materialized.frameHeight());
        }
        return root;
    }

    private static JsonObject textures(
            List<String> keys,
            String sheetResource) {
        JsonObject textures = new JsonObject();
        for (String key : keys) {
            textures.addProperty(
                    key,
                    sheetResource);
        }
        return textures;
    }

    private static JsonObject connectionPredicate() {
        JsonObject predicate = new JsonObject();
        predicate.addProperty(
                "type",
                "is_same_block");
        return predicate;
    }

    private static ManagedAuthoringFile find(
            ManagedAuthoringProject project,
            String marker) {
        return project.documents()
                .stream()
                .filter(file ->
                        file.relativePath()
                                .contains(marker))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Fusion template is missing "
                                        + marker));
    }

    private static String modelResource(
            String path) {
        String[] parts = path.split("/", 4);
        if (parts.length != 4
                || !parts[0].equals("assets")
                || !parts[2].equals("models")
                || !parts[3].endsWith(".json")) {
            throw new IllegalArgumentException(
                    "FUSION_MODEL_RESOURCE_PATH_INVALID");
        }
        return parts[1]
                + ':'
                + parts[3].substring(
                        0,
                        parts[3].length()
                                - ".json".length());
    }

    private static JsonObject parse(
            byte[] bytes) {
        JsonElement parsed = JsonParser.parseString(
                new String(
                        bytes,
                        StandardCharsets.UTF_8));
        if (parsed instanceof JsonObject object) {
            return object;
        }
        throw new IllegalArgumentException(
                "FUSION_DOCUMENT_NOT_OBJECT");
    }

    private static byte[] bytes(
            JsonObject object) {
        return object.toString()
                .getBytes(StandardCharsets.UTF_8);
    }

    private static void stripExtensions(
            JsonObject object) {
        object.remove("method");
        object.remove("compatibility");
    }

    private static String sheetResource(
            ManagedAuthoringRule rule) {
        return "autoseamblend:generated/"
                + rule.resolvedMethod()
                        .serializedName()
                + '/'
                + rule.managedStem()
                + "/sheet";
    }
}
