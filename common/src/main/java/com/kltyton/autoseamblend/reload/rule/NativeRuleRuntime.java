package com.kltyton.autoseamblend.reload.rule;

import com.google.gson.JsonObject;
import com.kltyton.autoseamblend.authoring.storage.ManagedPackIdentity;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.reload.rule.evidence.NativeSlotEvidenceResolver;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 中文：遍历原生作者资源包、注入槽位证据并构造 Common 规则快照。
 * English: Traverses native-author packs, injects slot evidence, and builds a Common rule snapshot.
 */
public final class NativeRuleRuntime {
    private static final int MAX_DOCUMENTS = 4096;
    private static final int MAX_DOCUMENT_BYTES =
            2 * 1024 * 1024;

    private NativeRuleRuntime() {}

    public static NativeRuleSnapshot current() {
        return ReloadPublication.current()
                .nativeRules();
    }

    /** 中文：构造完整原生扩展候选但不发布。 / English: Builds one complete native-extension candidate without publishing it. */
    public static NativeRuleSnapshot prepare(
            ResourceManager resources,
            long generation) {
        Objects.requireNonNull(resources, "resources");
        if (generation <= 0) {
            throw new IllegalArgumentException(
                    "generation must be positive");
        }
        ArrayList<NativeRule> candidates = new ArrayList<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        int packPriority = 0;
        for (PackResources pack : resources.listPacks().toList()) {
            if (!ManagedPackIdentity.matches(pack)) {
                capture(
                        pack,
                        resources,
                        packPriority,
                        candidates,
                        diagnostics);
            }
            packPriority++;
        }
        NativeRuleSnapshot next = NativeRuleSnapshot.create(
                generation,
                candidates,
                candidate -> effectiveDocument(resources, candidate),
                diagnostics);
        logPrepared(next, "Prepared");
        return next;
    }

    private static boolean effectiveDocument(
            ResourceManager resources,
            NativeRule candidate) {
        ResourceLocation resourceId = ResourceLocation.tryParse(
                candidate.resourceId());
        return resourceId != null
                && resources.getResourceStack(resourceId).stream()
                        .reduce((first, second) -> second)
                        .map(resource -> resource.sourcePackId()
                                .equals(candidate.packId()))
                        .orElse(false);
    }

    private static void logPrepared(
            NativeRuleSnapshot next,
            String action) {
        Constants.LOG.info(
                "{} native-author extension generation {}: rules={}, diagnostics={}",
                action,
                next.generation(),
                next.rules().size(),
                next.diagnostics().size());
    }

    private static void capture(
            PackResources pack,
            ResourceManager resources,
            int packPriority,
            List<NativeRule> output,
            List<String> diagnostics) {
        if (output.size() >= MAX_DOCUMENTS) {
            diagnostics.add("NATIVE_DOCUMENT_LIMIT_EXCEEDED");
            return;
        }
        for (String namespace : pack.getNamespaces(PackType.CLIENT_RESOURCES)
                .stream()
                .sorted()
                .toList()) {
            collect(
                    pack,
                    resources,
                    namespace,
                    "textures",
                    packPriority,
                    output,
                    diagnostics);
            collect(
                    pack,
                    resources,
                    namespace,
                    "fusion/model_modifiers/blocks",
                    packPriority,
                    output,
                    diagnostics);
            collect(
                    pack,
                    resources,
                    namespace,
                    "athena",
                    packPriority,
                    output,
                    diagnostics);
            collect(
                    pack,
                    resources,
                    namespace,
                    "blockstates",
                    packPriority,
                    output,
                    diagnostics);
        }
    }

    private static void collect(
            PackResources pack,
            ResourceManager resources,
            String namespace,
            String prefix,
            int packPriority,
            List<NativeRule> output,
            List<String> diagnostics) {
        pack.listResources(
                PackType.CLIENT_RESOURCES,
                namespace,
                prefix,
                (resourceId, supplier) -> {
                    if (output.size() >= MAX_DOCUMENTS
                            || !RuleDocumentCodec.recognizedNativeDocument(
                                    resourceId.toString())) {
                        return;
                    }
                    try (InputStream input = supplier.get()) {
                        byte[] bytes = input.readNBytes(
                                MAX_DOCUMENT_BYTES + 1);
                        if (bytes.length > MAX_DOCUMENT_BYTES) {
                            diagnostics.add(
                                    "NATIVE_DOCUMENT_TOO_LARGE:"
                                            + pack.packId()
                                            + ':'
                                            + resourceId);
                            return;
                        }
                        parse(
                                resourceId,
                                bytes,
                                resources,
                                pack.packId(),
                                packPriority,
                                output.size())
                                .forEach(output::add);
                    } catch (IOException | RuntimeException exception) {
                        diagnostics.add(
                                "NATIVE_DOCUMENT_REJECTED:"
                                        + pack.packId()
                                        + ':'
                                        + resourceId
                                        + ':'
                                        + exception.getClass().getSimpleName());
                    }
                });
    }

    private static List<NativeRule> parse(
            ResourceLocation resourceId,
            byte[] bytes,
            ResourceManager resources,
            String packId,
            int packPriority,
            int order) {
        Optional<ParsedRuleDocument> parsed = RuleDocumentCodec.parseNative(
                resourceId.toString(),
                bytes);
        if (parsed.isEmpty()) {
            return List.of();
        }
        ParsedRuleDocument document = parsed.orElseThrow();
        return ParsedRuleProjection.nativeRules(
                document,
                slots(document, resources),
                packId,
                packPriority,
                order);
    }

    private static List<NativeSlot> slots(
            ParsedRuleDocument document,
            ResourceManager resources) {
        ResourceLocation resourceId = ResourceLocation.tryParse(document.resourceId());
        if (resourceId == null) {
            throw new IllegalArgumentException(
                    "native resource id is invalid");
        }
        JsonObject json = document.jsonObject().orElseThrow();
        return switch (document.family()) {
            case CTM_MOD -> NativeSlotEvidenceResolver.ctmMod(
                    resourceId,
                    document.requestedMethod(),
                    resources);
            case FUSION -> NativeSlotEvidenceResolver.fusion(
                    resourceId,
                    json,
                    document.requestedMethod(),
                    resources);
            case ATHENA -> NativeSlotEvidenceResolver.athena(
                    resourceId,
                    json,
                    document.requestedMethod(),
                    resources);
            case MCPATCHER -> List.of();
        };
    }
}
