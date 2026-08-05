package com.kltyton.autoseamblend.compat.ctm_mod.authoring.property;

import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringFile;
import com.kltyton.autoseamblend.authoring.property.NativePropertyCompanionCollector;
import com.kltyton.autoseamblend.authoring.property.NativePropertyDocument;
import com.kltyton.autoseamblend.authoring.property.NativePropertyDocumentLoader;
import com.kltyton.autoseamblend.authoring.selector.NativeBlockSelectorResolver;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 中文：把 NeoForge 独占 CTM Mod 的属性文档 principal/companion/parse 接入公共
 * NativePropertyDocumentLoader 家族注册点。
 *
 * English: Connects NeoForge-only CTM Mod property-document principal,
 * companion, and parse logic to the shared NativePropertyDocumentLoader
 * family registry.
 */
public enum CtmModNativePropertyExtension
        implements NativePropertyDocumentLoader.FamilyExtension {
    INSTANCE;

    @Override
    public ManagedAuthoringFile principal(
            List<ManagedAuthoringFile> documents) {
        return CtmModNativePropertyCompanions.principal(documents);
    }

    @Override
    public Map<String, byte[]> collect(
            String sourcePath,
            byte[] source,
            List<ManagedAuthoringFile> templateDocuments,
            String templatePrincipalPath,
            NativePropertyCompanionCollector.DocumentReader reader)
            throws IOException {
        return CtmModNativePropertyCompanions.collect(
                sourcePath,
                source,
                templateDocuments,
                templatePrincipalPath,
                reader);
    }

    @Override
    public NativePropertyDocument parse(
            Optional<String> targetBlockId,
            String documentPath,
            String sourceDocumentPath,
            byte[] sourceDocument,
            Map<String, byte[]> companionDocuments,
            ConnectionMethod fallbackMethod,
            boolean fallbackCompatibility,
            NativeBlockSelectorResolver selectorResolver)
            throws IOException {
        return CtmModNativePropertyDocumentParser.parse(
                targetBlockId,
                documentPath,
                sourceDocumentPath,
                sourceDocument,
                companionDocuments,
                fallbackMethod,
                fallbackCompatibility,
                selectorResolver);
    }
}
