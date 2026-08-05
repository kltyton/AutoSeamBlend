package com.kltyton.autoseamblend.compat.ctm_mod.authoring.contract;

import com.kltyton.autoseamblend.authoring.export.NativeDocumentSnapshot;
import com.kltyton.autoseamblend.authoring.export.ManagedExportDocumentAssembly;
import com.kltyton.autoseamblend.authoring.document.NativeDocumentOperations;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringFile;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringRule;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.template.CtmModAuthoringTemplate;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.export.managed.ManagedExportIr;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.selection.method.MethodSlotDomain;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 中文：构造 CTM Mod 原生 authoring/baked 文档及其项目 IR；纹理像素仍由 Loader 载体桥接实体化。
 *
 * <p>English: Builds CTM Mod native authoring/baked documents and project IR; texture pixels are
 * still materialized by the Loader carrier bridge.</p>
 */
public final class CtmModManagedExportDocumentPlan {
    private CtmModManagedExportDocumentPlan() {}

    public static Result prepare(
            ManagedAuthoringRule rule,
            Optional<NativeDocumentSnapshot> captured)
            throws IOException {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(captured, "captured");
        List<ManagedAuthoringFile> templates = CtmModAuthoringTemplate.create(rule);
        ManagedAuthoringFile blockstateTemplate = templates.stream()
                .filter(file -> file.relativePath().contains("/blockstates/"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "CTM_MOD_BLOCKSTATE_TEMPLATE_MISSING"));
        ManagedAuthoringFile modelTemplate = templates.stream()
                .filter(file -> file.relativePath().contains("/models/"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "CTM_MOD_MODEL_TEMPLATE_MISSING"));

        NativeDocumentSnapshot principal = ManagedExportDocumentAssembly.principal(
                EngineFamily.CTM_MOD,
                blockstateTemplate,
                captured);
        ConnectionMethod method = rule.resolvedMethod();
        byte[] authoringBlockstate = CtmModNativeDocumentCodec.nativeExecutionView(
                principal.resolve(
                        CtmModNativeDocumentCodec.authoringExtension(
                                rule.requestedMethod(),
                                rule.compatibility()),
                        NativeDocumentOperations.shared()),
                method);
        byte[] bakedBlockstate = CtmModNativeDocumentCodec.nativeExecutionView(
                CtmModNativeDocumentCodec.stripAuthoringExtension(
                        principal.resolve(
                                CtmModNativeDocumentCodec.bakedExtension(),
                                NativeDocumentOperations.shared())),
                method);

        LinkedHashMap<String, ManagedExportIr.Document> documents = new LinkedHashMap<>();
        putDocument(documents, new ManagedExportIr.Document(
                principal.documentPath(),
                authoringBlockstate,
                bakedBlockstate));
        for (Map.Entry<String, byte[]> companion : principal.companionDocuments().entrySet()) {
            putDocument(documents, new ManagedExportIr.Document(
                    companion.getKey(),
                    companion.getValue(),
                    companion.getValue()));
        }
        Set<String> referencedModels = CtmModNativeDocumentCodec.referencedModels(authoringBlockstate);
        String templateModelId = CtmModNativeDocumentCodec.modelId(modelTemplate.relativePath());
        for (String referencedModel : referencedModels) {
            String referencedPath = CtmModNativeDocumentCodec.modelPath(referencedModel);
            if (documents.containsKey(referencedPath)) {
                continue;
            }
            if (!templateModelId.equals(referencedModel)) {
                throw new IOException("CTM_MOD_MODEL_COMPANION_MISSING:" + referencedPath);
            }
            putDocument(documents, new ManagedExportIr.Document(
                    modelTemplate.relativePath(),
                    modelTemplate.content(),
                    modelTemplate.content()));
        }
        List<Integer> logicalSlots = method == ConnectionMethod.NONE
                ? List.of()
                : MethodSlotDomain.of(method).slots();
        return new Result(
                principal,
                documents,
                logicalSlots,
                CtmModCarrierLayout.forMethod(method).kind());
    }

    private static void putDocument(
            Map<String, ManagedExportIr.Document> documents,
            ManagedExportIr.Document document)
            throws IOException {
        String path = document.authoring().orElseThrow().path();
        if (documents.putIfAbsent(path, document) != null) {
            throw new IOException("CTM_MOD_DOCUMENT_PATH_CONFLICT:" + path);
        }
    }

    public record Result(
            NativeDocumentSnapshot principal,
            Map<String, ManagedExportIr.Document> documents,
            List<Integer> logicalSlots,
            String kind) {
        public Result {
            Objects.requireNonNull(principal, "principal");
            documents = Collections.unmodifiableMap(
                    new LinkedHashMap<>(Objects.requireNonNull(documents, "documents")));
            logicalSlots = List.copyOf(Objects.requireNonNull(logicalSlots, "logicalSlots"));
            if (kind == null || kind.isBlank()) {
                throw new IllegalArgumentException("kind must not be blank");
            }
        }
    }
}
