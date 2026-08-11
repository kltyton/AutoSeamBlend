package com.kltyton.autoseamblend.compat.athena.authoring.export;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kltyton.autoseamblend.compat.athena.authoring.AthenaPaneTilePlan;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringFile;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringProject;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringRule;
import com.kltyton.autoseamblend.authoring.template.ManagedAuthoringTemplates;
import com.kltyton.autoseamblend.compat.athena.authoring.AthenaPhysicalTilePlan;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.export.managed.ManagedExportIr;
import com.kltyton.autoseamblend.authoring.export.ExportSurfaceSnapshot;
import com.kltyton.autoseamblend.authoring.export.ManagedExportDocumentAssembly;
import com.kltyton.autoseamblend.authoring.export.NativeDocumentSnapshot;
import com.kltyton.autoseamblend.authoring.export.NativeDocumentBaker;
import com.kltyton.autoseamblend.authoring.materialize.TextureSourceSnapshot;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/** 中文：构建 Athena 原生创作资源和可独立加载的 baked 资源。 / English: Builds Athena-native authoring and standalone baked resources. */
public final class AthenaManagedExportAssembler {

    private AthenaManagedExportAssembler() {}

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
        ManagedAuthoringProject project =
                ManagedAuthoringTemplates.create(
                        EngineFamily.ATHENA,
                        List.of(rule));
        ManagedAuthoringFile file =
                project.documents().get(0);
        NativeDocumentSnapshot principal =
                ManagedExportDocumentAssembly.principal(
                        EngineFamily.ATHENA,
                        file,
                        nativeDocument);
        if (rule.resolvedMethod()
                == ConnectionMethod.NONE) {
            return none(
                    order,
                    rule,
                    principal);
        }
        return rule.pane()
                ? pane(
                        order,
                        rule,
                        principal,
                        surface,
                        source,
                        topSource)
                : regular(
                        order,
                        rule,
                        principal,
                        surface,
                        source,
                        topSource);
    }

    private static ManagedExportIr.Rule regular(
            int order,
            ManagedAuthoringRule rule,
            NativeDocumentSnapshot principal,
            ExportSurfaceSnapshot surface,
            TextureSourceSnapshot source,
            Optional<TextureSourceSnapshot>
                    topSource)
            throws IOException {
        JsonObject authoring = authoring(
                principal,
                rule);
        String resourcePrefix =
                resourcePrefix(rule);
        JsonObject textures = new JsonObject();
        for (AthenaPhysicalTilePlan.Role role
                : AthenaPhysicalTilePlan.Role.values()) {
            textures.addProperty(
                    role.jsonKey(),
                    resourcePrefix
                            + '/'
                            + role.jsonKey());
        }
        authoring.add(
                "ctm_textures",
                textures);
        JsonObject baked = authoring.deepCopy();
        stripExtensions(baked);

        AthenaPhysicalTilePlan
                plan =
                AthenaPhysicalTilePlan.forNativeCarrier(
                        rule.resolvedMethod());
        TextureSourceSnapshot
                resolvedTop =
                        rule.resolvedMethod()
                                        == ConnectionMethod.TOP
                                ? topSource.orElseThrow(
                                        () -> new IllegalStateException(
                                                "TOP_SOURCE_SURFACE_UNRESOLVED"))
                                : source;
        List<Integer> slots = IntStream.range(
                        0,
                        AthenaPhysicalTilePlan.ROLE_COUNT)
                .boxed()
                .toList();
        ArrayList<ManagedExportIr.Document> documents =
                new ArrayList<>();
        documents.add(new ManagedExportIr.Document(
                principal.documentPath(),
                bytes(authoring),
                principal.documentPath(),
                bytes(baked)));
        ArrayList<ManagedExportIr.Tile> tiles =
                new ArrayList<>();
        LinkedHashSet<String> generatedPaths =
                new LinkedHashSet<>();
        for (int slot : slots) {
            AthenaPhysicalTilePlan.Role role =
                    AthenaPhysicalTilePlan.Role.values()[slot];
            String path = physicalPrefix(rule)
                    + '/'
                    + role.jsonKey()
                    + ".png";
            generatedPaths.add(path);
            generatedPaths.add(path + ".mcmeta");
            TextureSourceSnapshot
                    tileSource = rule.resolvedMethod()
                                    == ConnectionMethod.TOP
                            ? resolvedTop
                            : source;
            tiles.add(new ManagedExportIr.Tile(
                    path,
                    path,
                    slot,
                    ManagedExportIr.Tile.Source.GENERATED,
                    tileSource.materialize(
                                    plan.recipes()
                                            .get(slot),
                                    surface.overlayProfile())
                            .png()));
            ManagedExportDocumentAssembly.appendMetadata(
                    documents,
                    path,
                    tileSource.sourceMetadata());
        }
        ManagedExportDocumentAssembly.appendCompanionDocuments(
                documents,
                principal,
                generatedPaths,
                NativeDocumentBaker::bakedCompanion);
        return rule(
                order,
                rule,
                documents,
                slots,
                tiles,
                "athena-native-five-role");
    }

    private static ManagedExportIr.Rule pane(
            int order,
            ManagedAuthoringRule rule,
            NativeDocumentSnapshot principal,
            ExportSurfaceSnapshot surface,
            TextureSourceSnapshot source,
            Optional<TextureSourceSnapshot>
                    topSource)
            throws IOException {
        Objects.requireNonNull(topSource, "topSource");
        JsonObject authoring = authoring(
                principal,
                rule);
        JsonObject textures = new JsonObject();
        String resourcePrefix =
                resourcePrefix(rule);
        List<AthenaPaneTilePlan.PaneTile> paneTiles =
                AthenaPaneTilePlan.forMethod(rule.resolvedMethod());
        for (AthenaPaneTilePlan.PaneTile tile : paneTiles) {
            textures.addProperty(
                    tile.role().jsonKey(),
                    resourcePrefix
                            + '/'
                            + tile.role().jsonKey());
        }
        authoring.add(
                "ctm_textures",
                textures);
        JsonObject baked = authoring.deepCopy();
        stripExtensions(baked);

        ArrayList<ManagedExportIr.Document> documents =
                new ArrayList<>();
        documents.add(new ManagedExportIr.Document(
                principal.documentPath(),
                bytes(authoring),
                principal.documentPath(),
                bytes(baked)));
        ArrayList<ManagedExportIr.Tile> tiles =
                new ArrayList<>();
        ArrayList<Integer> slots =
                new ArrayList<>();
        LinkedHashSet<String> generatedPaths =
                new LinkedHashSet<>();
        for (int slot = 0; slot < paneTiles.size(); slot++) {
            AthenaPaneTilePlan.PaneTile tile = paneTiles.get(slot);
            String path = physicalPrefix(rule)
                    + '/'
                    + tile.role().jsonKey()
                    + ".png";
            generatedPaths.add(path);
            generatedPaths.add(path + ".mcmeta");
            slots.add(slot);
            tiles.add(new ManagedExportIr.Tile(
                    path,
                    path,
                    slot,
                    ManagedExportIr.Tile.Source.GENERATED,
                    source.materialize(tile.recipe())
                            .png()));
            ManagedExportDocumentAssembly.appendMetadata(
                    documents,
                    path,
                    source.sourceMetadata());
        }
        ManagedExportDocumentAssembly.appendCompanionDocuments(
                documents,
                principal,
                generatedPaths,
                NativeDocumentBaker::bakedCompanion);
        return rule(
                order,
                rule,
                documents,
                slots,
                tiles,
                "athena-native-pane");
    }

    private static ManagedExportIr.Rule none(
            int order,
            ManagedAuthoringRule rule,
            NativeDocumentSnapshot principal)
            throws IOException {
        ArrayList<ManagedExportIr.Document> documents =
                new ArrayList<>();
        documents.add(new ManagedExportIr.Document(
                principal.documentPath(),
                principal.resolve(
                        Map.of(
                                "method",
                                Optional.of(jsonString(
                                        rule.requestedMethod()
                                                .serializedName())),
                                "compatibility",
                                Optional.of(Boolean.toString(
                                        rule.compatibility())))),
                NativeDocumentBaker.bakedPassthrough(principal)));
        ManagedExportDocumentAssembly.appendCompanionDocuments(
                documents,
                principal,
                Set.of(),
                NativeDocumentBaker::bakedCompanion);
        return rule(
                order,
                rule,
                documents,
                List.of(),
                List.of(),
                "explicit-none");
    }

    private static ManagedExportIr.Rule rule(
            int order,
            ManagedAuthoringRule rule,
            List<ManagedExportIr.Document> documents,
            List<Integer> slots,
            List<ManagedExportIr.Tile> tiles,
            String reason) {
        return new ManagedExportIr.Rule(
                order,
                rule.targetBlockId(),
                rule.targetBlockId(),
                documents,
                rule.requestedMethod()
                        .serializedName(),
                rule.resolvedMethod()
                        .serializedName(),
                List.of(reason),
                slots,
                Map.of(),
                slots,
                List.of(),
                tiles);
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
                "ATHENA_DOCUMENT_NOT_OBJECT");
    }

    private static byte[] bytes(
            JsonObject object) {
        return object.toString()
                .getBytes(StandardCharsets.UTF_8);
    }

    private static void stripExtensions(
            JsonObject object) {
        object.remove("id");
        object.remove("method");
        object.remove("compatibility");
    }

    private static JsonObject authoring(
            NativeDocumentSnapshot principal,
            ManagedAuthoringRule rule)
            throws IOException {
        return parse(principal.resolve(
                Map.of(
                        "method",
                        Optional.of(jsonString(
                                rule.requestedMethod()
                                        .serializedName())),
                        "compatibility",
                        Optional.of(Boolean.toString(
                                rule.compatibility())))));
    }

    private static String jsonString(String value) {
        return new com.google.gson.JsonPrimitive(value)
                .toString();
    }

    private static String resourcePrefix(
            ManagedAuthoringRule rule) {
        return "autoseamblend:generated/"
                + rule.resolvedMethod()
                        .serializedName()
                + '/'
                + rule.managedStem();
    }

    private static String physicalPrefix(
            ManagedAuthoringRule rule) {
        return "assets/autoseamblend/textures/generated/"
                + rule.resolvedMethod()
                        .serializedName()
                + '/'
                + rule.managedStem();
    }

}
