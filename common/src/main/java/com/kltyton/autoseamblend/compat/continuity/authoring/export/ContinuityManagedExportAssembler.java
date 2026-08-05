package com.kltyton.autoseamblend.compat.continuity.authoring.export;

import com.kltyton.autoseamblend.authoring.format.mcpatcher.MCPatcherNativeProperties;
import com.kltyton.autoseamblend.authoring.format.mcpatcher.MCPatcherNativeProperties.AuthoringDocument;
import com.kltyton.autoseamblend.authoring.format.mcpatcher.MCPatcherNativeProperties.CapturedDocument;
import com.kltyton.autoseamblend.authoring.format.mcpatcher.MCPatcherNativeProperties.FrozenPredicate;
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
import com.kltyton.autoseamblend.compat.continuity.authoring.export.ContinuityManagedExportPlan;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 中文：从冻结的源表面构建一条 MCPatcher 原生 authoring 或 baked 规则。 / English: Builds one MCPatcher-native authoring/baked rule from a frozen source surface. */
public final class ContinuityManagedExportAssembler {
    private ContinuityManagedExportAssembler() {}

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
        ContinuityManagedExportPlan.requireSource(
                rule.sourceTextureId(),
                source.sourceTextureId());

        ManagedAuthoringProject authoring =
                ManagedAuthoringTemplates.create(
                        EngineFamily.MCPATCHER,
                        List.of(rule));
        ManagedAuthoringFile properties =
                authoring.documents().getFirst();
        NativeDocumentSnapshot principal =
                ManagedExportDocumentAssembly.principal(
                        EngineFamily.MCPATCHER,
                        properties,
                        nativeDocument);
        AuthoringDocument authoringDocument = MCPatcherNativeProperties.authoring(
                rule,
                Optional.of(new CapturedDocument(
                        principal.documentPath(),
                        principal.resolve())));
        ContinuityManagedExportPlan.SlotPlan slotPlan =
                ContinuityManagedExportPlan.forRule(rule);
        Map<Integer, String> tileExpressions = slotPlan.tileExpressions();
        Optional<byte[]> bakedDocument = MCPatcherNativeProperties.baked(
                authoringDocument,
                rule,
                new FrozenPredicate(surface.overlayReceiverBlockIds()),
                tileExpressions);
        if (rule.resolvedMethod()
                == ConnectionMethod.NONE) {
            return none(
                    order,
                    rule,
                    principal,
                    authoringDocument.source(),
                    bakedDocument);
        }
        ArrayList<ManagedExportIr.Document> documents =
                new ArrayList<>();
        documents.add(new ManagedExportIr.Document(
                principal.documentPath(),
                authoringDocument.source(),
                bakedDocument.orElseThrow(() -> new IllegalStateException(
                        "non-NONE MCPatcher export requires a baked document"))));

        List<Integer> slots = slotPlan.indexes();
        Set<String> generatedPaths = slotPlan.generatedDocumentPaths();
        ManagedExportDocumentAssembly.appendCompanionDocuments(
                documents,
                principal,
                generatedPaths,
                NativeDocumentBaker::bakedCompanion);
        ArrayList<ManagedExportIr.Tile> tiles =
                new ArrayList<>();
        TextureSourceSnapshot tileSource =
                ContinuityManagedExportPlan.sourceFor(
                        rule.resolvedMethod(),
                        source,
                        topSource);
        for (ContinuityManagedExportPlan.Slot slot : slotPlan.slots()) {
            String tilePath = slot.texturePath();
            byte[] png = tileSource.materialize(
                            slot.recipe(),
                            surface.overlayProfile())
                    .png();
            tiles.add(new ManagedExportIr.Tile(
                    tilePath,
                    tilePath,
                    slot.index(),
                    ManagedExportIr.Tile.Source.GENERATED,
                    png));
            byte[] metadata =
                    tileSource.sourceMetadata();
            ManagedExportDocumentAssembly.appendMetadata(
                    documents,
                    tilePath,
                    metadata);
        }

        return new ManagedExportIr.Rule(
                order,
                rule.targetBlockId(),
                rule.targetBlockId(),
                ContinuityManagedExportPlan.deduplicateDocuments(documents),
                rule.requestedMethod()
                        .serializedName(),
                rule.resolvedMethod()
                        .serializedName(),
                List.of(
                        "surface-catalog:"
                                + rule.resolvedMethod()
                                        .serializedName()),
                slots,
                java.util.Map.of(),
                slots,
                List.of(),
                tiles);
    }

    private static ManagedExportIr.Rule none(
            int order,
            ManagedAuthoringRule rule,
            NativeDocumentSnapshot principal,
            byte[] authoringDocument,
            Optional<byte[]> bakedDocument)
            throws IOException {
        ArrayList<ManagedExportIr.Document> documents =
                new ArrayList<>();
        documents.add(new ManagedExportIr.Document(
                principal.documentPath(),
                authoringDocument,
                bakedDocument.orElse(null)));
        ManagedExportDocumentAssembly.appendCompanionDocuments(
                documents,
                principal,
                Set.of(),
                NativeDocumentBaker::bakedCompanion);
        return new ManagedExportIr.Rule(
                order,
                rule.targetBlockId(),
                rule.targetBlockId(),
                ContinuityManagedExportPlan.deduplicateDocuments(documents),
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

}
