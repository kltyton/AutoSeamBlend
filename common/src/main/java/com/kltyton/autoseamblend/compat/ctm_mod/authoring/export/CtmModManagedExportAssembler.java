package com.kltyton.autoseamblend.compat.ctm_mod.authoring.export;

import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringRule;
import com.kltyton.autoseamblend.authoring.export.NativeDocumentSnapshot;
import com.kltyton.autoseamblend.authoring.export.ManagedExportDocumentAssembly;
import com.kltyton.autoseamblend.export.managed.ManagedExportIr;
import com.kltyton.autoseamblend.authoring.export.ExportSurfaceSnapshot;
import com.kltyton.autoseamblend.authoring.materialize.TextureSourceSnapshot;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.contract.CtmModCarrierLayout;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.contract.CtmModCarrierLayout.CarrierSpec;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.contract.CtmModCarrierPaths;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.contract.CtmModNativeDocument;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.contract.CtmModManagedExportDocumentPlan;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.materialize.CtmModCarrierSynthesis;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/** 中文：构建 CTM Lib 方块状态、模型文档及其原生载体精灵。 / English: Builds CTM Lib blockstate/model documents and their native carrier sprites. */
public final class CtmModManagedExportAssembler {
    private CtmModManagedExportAssembler() {}

    public static ManagedExportIr.Rule assemble(
            int order,
            ManagedAuthoringRule rule,
            ExportSurfaceSnapshot surface,
            TextureSourceSnapshot source,
            Optional<TextureSourceSnapshot> topSource,
            Optional<NativeDocumentSnapshot> nativeDocument)
            throws IOException {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(topSource, "topSource");
        Objects.requireNonNull(nativeDocument, "nativeDocument");
        if (!source.sourceTextureId().equals(
                rule.sourceTextureId())) {
            throw new IllegalArgumentException(
                    "EXPORT_SOURCE_TEXTURE_CHANGED");
        }

        CtmModManagedExportDocumentPlan.Result documentPlan =
                CtmModManagedExportDocumentPlan.prepare(
                        rule,
                        nativeDocument);
        LinkedHashMap<String, ManagedExportIr.Document> documents =
                new LinkedHashMap<>(documentPlan.documents());

        ArrayList<ManagedExportIr.Tile> tiles =
                new ArrayList<>();
        ConnectionMethod method = rule.resolvedMethod();
        List<Integer> logicalSlots = documentPlan.logicalSlots();
        TextureSourceSnapshot carrierSource =
                method == ConnectionMethod.TOP
                        ? topSource.orElseThrow(() ->
                                new IOException(
                                        "TOP_SOURCE_SURFACE_UNRESOLVED"))
                        : source;
        for (CarrierSpec spec : CtmModCarrierLayout
                .forMethod(method)
                .carriers()) {
            Identifier carrierId = Identifier.tryParse(
                    CtmModCarrierPaths.generatedId(rule, spec.role()));
            TextureSourceSnapshot carrier =
                    CtmModCarrierSynthesis.create(
                            carrierSource,
                            carrierId.toString(),
                            method,
                            spec,
                            surface.overlayProfile());
            String path = CtmModNativeDocument.texturePath(
                    carrierId);
            tiles.add(new ManagedExportIr.Tile(
                    path,
                    path,
                    logicalSlots,
                    ManagedExportIr.Tile.Source.GENERATED,
                    carrier.materializeCarrier().png()));
            Optional<ManagedExportIr.Document> metadata =
                    ManagedExportDocumentAssembly.metadataDocument(
                            path,
                            carrier.sourceMetadata());
            if (metadata.isPresent()) {
                putDocument(documents, metadata.orElseThrow());
            }
        }

        return new ManagedExportIr.Rule(
                order,
                rule.targetBlockId(),
                rule.targetBlockId(),
                List.copyOf(documents.values()),
                rule.requestedMethod().serializedName(),
                method.serializedName(),
                List.of("ctm-lib-" + documentPlan.kind()),
                logicalSlots,
                Map.of(),
                logicalSlots,
                List.of(),
                tiles);
    }

    private static void putDocument(
            Map<String, ManagedExportIr.Document> documents,
            ManagedExportIr.Document document)
            throws IOException {
        String path = document.authoring()
                .orElseThrow()
                .path();
        if (documents.putIfAbsent(path, document) != null) {
            throw new IOException(
                    "CTM_MOD_DOCUMENT_PATH_CONFLICT:" + path);
        }
    }

}
