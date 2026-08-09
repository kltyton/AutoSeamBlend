package com.kltyton.autoseamblend.compat.continuity.runtime;

import com.kltyton.autoseamblend.authoring.format.mcpatcher.MCPatcherAuthorExtension;
import com.kltyton.autoseamblend.authoring.format.mcpatcher.MCPatcherNativeProperties.CapturedDocument;
import com.kltyton.autoseamblend.engine.ownership.AutoBlendPolicy;
import com.kltyton.autoseamblend.engine.ownership.NativeRuleSource;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import com.kltyton.autoseamblend.engine.ownership.NativeSlotIntent;
import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import com.kltyton.autoseamblend.engine.query.AcceptedNativeDocument;
import com.kltyton.autoseamblend.engine.query.NativeDocumentIdentity;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：跨 Loader 共用的已接受 Continuity holder 元数据；不持有 Continuity processor 或
 * Loader 资源对象。
 *
 * English: Loader-neutral metadata for one accepted Continuity holder; it owns no Continuity
 * processor or Loader resource object.
 */
public record ContinuityAcceptedHolderEvidence(
        SourceTier sourceTier,
        Optional<AutoBlendPolicy> strategyPolicy,
        String sourcePackId,
        String sourceResourceId,
        int packPriority,
        Optional<ConnectionMethod> requestedMethod,
        Optional<ConnectionMethod> resolvedMethod,
        List<NativeSlot> nativeSlots,
        Optional<NativeDocumentIdentity> queryIdentity,
        Optional<CapturedDocument> capturedDocument) {
    public ContinuityAcceptedHolderEvidence {
        Objects.requireNonNull(sourceTier, "sourceTier");
        strategyPolicy = Objects.requireNonNull(strategyPolicy, "strategyPolicy");
        requireText(sourcePackId, "sourcePackId");
        requireText(sourceResourceId, "sourceResourceId");
        if (packPriority < 0) {
            throw new IllegalArgumentException("packPriority must be non-negative");
        }
        requestedMethod = Objects.requireNonNull(requestedMethod, "requestedMethod");
        resolvedMethod = Objects.requireNonNull(resolvedMethod, "resolvedMethod");
        if (resolvedMethod.filter(method -> method == ConnectionMethod.AUTO).isPresent()) {
            throw new IllegalArgumentException("resolved method must be concrete");
        }
        nativeSlots = List.copyOf(Objects.requireNonNull(nativeSlots, "nativeSlots"));
        queryIdentity = Objects.requireNonNull(queryIdentity, "queryIdentity");
        capturedDocument = Objects.requireNonNull(capturedDocument, "capturedDocument");
        switch (sourceTier) {
            case NATIVE_AUTHOR -> {
                // Unextended author documents intentionally carry no policy.
            }
            case MANAGED_COMPATIBILITY -> {
                if (strategyPolicy.filter(AutoBlendPolicy::allowsCompletion).isEmpty()) {
                    throw new IllegalArgumentException(
                            "managed compatibility evidence requires completion policy");
                }
            }
            case MANAGED_NON_COMPATIBILITY -> {
                if (strategyPolicy.filter(policy -> !policy.allowsCompletion()).isEmpty()) {
                    throw new IllegalArgumentException(
                            "managed non-compatibility evidence requires native-exclusive policy");
                }
            }
            case CONFIG_COMPATIBILITY, CONFIG_NON_COMPATIBILITY ->
                    throw new IllegalArgumentException(
                            "accepted Continuity holders cannot carry config provenance");
        }
    }

    /**
     * Builds the shared evidence from a parsed MCPatcher extension and Loader-extracted fallback
     * identity. The Loader supplies only the raw properties values and native method result.
     */
    public static ContinuityAcceptedHolderEvidence from(
            Optional<MCPatcherAuthorExtension> extension,
            String fallbackPackId,
            String fallbackResourceId,
            int fallbackPackPriority,
            Optional<ConnectionMethod> nativeMethod,
            List<NativeSlot> nativeSlots,
            Optional<CapturedDocument> capturedDocument) {
        return from(
                extension,
                fallbackPackId,
                fallbackResourceId,
                fallbackPackPriority,
                nativeMethod,
                nativeSlots,
                capturedDocument,
                false);
    }

    /**
     * Variant for the Fabric exact-surface path, where an unconstrained AUTO extension keeps its
     * resolved method absent until the exact surface is known.
     */
    public static ContinuityAcceptedHolderEvidence from(
            Optional<MCPatcherAuthorExtension> extension,
            String fallbackPackId,
            String fallbackResourceId,
            int fallbackPackPriority,
            Optional<ConnectionMethod> nativeMethod,
            List<NativeSlot> nativeSlots,
            Optional<CapturedDocument> capturedDocument,
            boolean preserveExactSurfaceResolution) {
        extension = Objects.requireNonNull(extension, "extension");
        nativeMethod = Objects.requireNonNull(nativeMethod, "nativeMethod");
        Optional<MCPatcherAuthorExtension> value = extension;
        return new ContinuityAcceptedHolderEvidence(
                value.map(MCPatcherAuthorExtension::sourceTier)
                        .orElse(SourceTier.NATIVE_AUTHOR),
                value.flatMap(MCPatcherAuthorExtension::strategyPolicy),
                value.map(MCPatcherAuthorExtension::packId)
                        .orElse(Objects.requireNonNull(fallbackPackId, "fallbackPackId")),
                value.map(extensionValue -> extensionValue.resourceId().toString())
                        .orElse(Objects.requireNonNull(fallbackResourceId, "fallbackResourceId")),
                value.map(MCPatcherAuthorExtension::packPriority)
                        .orElse(fallbackPackPriority),
                value.map(extensionValue ->
                                Optional.of(extensionValue.requestedMethod()))
                        .orElse(nativeMethod),
                value.map(extensionValue -> preserveExactSurfaceResolution
                        && extensionValue.exactSurfaceResolutionRequired()
                                ? Optional.<ConnectionMethod>empty()
                                : Optional.of(extensionValue.resolvedMethod()))
                        .orElse(nativeMethod.filter(method -> method != ConnectionMethod.AUTO)),
                nativeSlots,
                value.map(extensionValue -> new NativeDocumentIdentity(
                        Optional.of(extensionValue.packId()),
                        extensionValue.resourceId().toString())),
                capturedDocument);
    }

    public NativeRuleSource source(int nativeOrdinal) {
        return new NativeRuleSource(
                "continuity",
                sourceTier,
                strategyPolicy,
                sourcePackId,
                sourceResourceId,
                packPriority,
                nativeOrdinal);
    }

    public ContinuityAcceptedDocument continuityDocument(int nativeOrdinal) {
        return new ContinuityAcceptedDocument(
                source(nativeOrdinal),
                requestedMethod,
                resolvedMethod,
                nativeSlots,
                capturedDocument);
    }

    public Optional<AcceptedNativeDocument> acceptedQueryDocument(int documentOrder) {
        if (queryIdentity.isEmpty()
                || requestedMethod.isEmpty()
                || resolvedMethod.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AcceptedNativeDocument(
                queryIdentity.orElseThrow(),
                Optional.of(new AcceptedNativeDocument.AcceptedEvidence(
                        sourceTier,
                        strategyPolicy,
                        requestedMethod.orElseThrow(),
                        resolvedMethod.orElseThrow(),
                        nativeSlots,
                        packPriority,
                        documentOrder))));
    }

    public boolean nativeSpritesPresent() {
        return nativeSlots.stream()
                .map(NativeSlot::intent)
                .anyMatch(intent -> intent == NativeSlotIntent.PRESENT
                        || intent == NativeSlotIntent.DEFAULT
                        || intent == NativeSlotIntent.SKIP);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
