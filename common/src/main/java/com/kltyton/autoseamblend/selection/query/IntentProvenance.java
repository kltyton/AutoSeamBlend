package com.kltyton.autoseamblend.selection.query;

import com.kltyton.autoseamblend.engine.ownership.NativeRuleSource;
import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import java.util.Objects;
import java.util.Optional;

/** 中文：原生文档、配置选择器或隐式发现意图的结构化标识。 / English: Structured identity of a native document, config selector, or implicit discovery intent. */
public record IntentProvenance(
        String identity,
        SourceTier tier,
        Kind kind,
        Optional<String> engineId,
        Optional<String> packId,
        Optional<String> resourceId,
        Optional<String> selectorIdentity,
        int packPriority,
        int nativeOrdinal) {
    public IntentProvenance {
        requireText(identity, "identity");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(kind, "kind");
        engineId = Objects.requireNonNull(engineId, "engineId");
        packId = Objects.requireNonNull(packId, "packId");
        resourceId = Objects.requireNonNull(resourceId, "resourceId");
        selectorIdentity = Objects.requireNonNull(selectorIdentity, "selectorIdentity");
        if (kind == Kind.NATIVE_DOCUMENT) {
            if (engineId.isEmpty() || packId.isEmpty() || resourceId.isEmpty()
                    || selectorIdentity.isPresent() || nativeOrdinal < 0) {
                throw new IllegalArgumentException("native provenance requires document coordinates");
            }
        } else if (selectorIdentity.isEmpty()
                || engineId.isPresent() || packId.isPresent() || resourceId.isPresent()
                || nativeOrdinal != -1) {
            throw new IllegalArgumentException("selector provenance requires only a selector identity");
        }
    }

    public static IntentProvenance nativeDocument(NativeRuleSource source) {
        Objects.requireNonNull(source, "source");
        String identity = source.engineId() + '|' + source.tier().name() + '|' + source.packId() + '|'
                + source.resourceId() + '|' + source.packPriority() + '|' + source.nativeOrdinal();
        return new IntentProvenance(
                identity,
                source.tier(),
                Kind.NATIVE_DOCUMENT,
                Optional.of(source.engineId()),
                Optional.of(source.packId()),
                Optional.of(source.resourceId()),
                Optional.empty(),
                source.packPriority(),
                source.nativeOrdinal());
    }

    public static IntentProvenance selection(SelectionIntent intent) {
        Objects.requireNonNull(intent, "intent");
        return new IntentProvenance(
                intent.identity(),
                intent.sourceTier(),
                intent.implicit() ? Kind.IMPLICIT_DISCOVERY : Kind.CONFIG_SELECTOR,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(intent.identity()),
                Integer.MIN_VALUE,
                -1);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    public enum Kind {
        NATIVE_DOCUMENT,
        CONFIG_SELECTOR,
        IMPLICIT_DISCOVERY
    }
}
