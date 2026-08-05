package com.kltyton.autoseamblend.compat.ctm_mod.reload;

import com.kltyton.autoseamblend.authoring.selector.NativeBlockSelectorResolver;
import com.kltyton.autoseamblend.reload.rule.ParsedRuleDocument;
import com.kltyton.autoseamblend.reload.rule.RuleDocumentCodec;
import java.io.IOException;
import java.util.Optional;

/**
 * 中文：把 NeoForge 独占的 CTM Mod 规则文档解析接入公共 RuleDocumentCodec 扩展点。
 *
 * English: Connects the NeoForge-only CTM Mod rule-document parser to the shared
 * RuleDocumentCodec extension point.
 */
public enum CtmModRuleCodecExtension
        implements RuleDocumentCodec.Extension {
    INSTANCE;

    @Override
    public boolean recognizedManagedDocument(
            String documentPath) {
        return CtmModRuleDocumentCodec
                .recognizedManagedDocument(documentPath);
    }

    @Override
    public Optional<ParsedRuleDocument> parseManaged(
            String documentPath,
            byte[] source,
            NativeBlockSelectorResolver selectorResolver)
            throws IOException {
        return CtmModRuleDocumentCodec.parseManaged(
                documentPath,
                source);
    }

    @Override
    public Optional<ParsedRuleDocument> parseNative(
            String resourceId,
            byte[] source) {
        return CtmModRuleDocumentCodec.parseNative(
                resourceId,
                source);
    }
}
