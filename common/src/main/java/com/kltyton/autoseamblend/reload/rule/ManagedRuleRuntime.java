package com.kltyton.autoseamblend.reload.rule;

import com.google.gson.JsonObject;
import com.kltyton.autoseamblend.authoring.selector.MinecraftNativeBlockSelectorResolver;
import com.kltyton.autoseamblend.authoring.storage.ManagedPackIdentity;
import com.kltyton.autoseamblend.authoring.storage.ManagedPackLayout;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.reload.rule.evidence.NativeSlotEvidenceResolver;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 中文：遍历 Managed 工作区、注入资源证据并发布 Common 规则快照。
 * English: Traverses the Managed workspace, injects resource evidence, and publishes a Common rule snapshot.
 */
public final class ManagedRuleRuntime {
    private static final int MAX_DOCUMENTS = 4096;
    private static final long MAX_DOCUMENT_BYTES =
            2L * 1024L * 1024L;

    private ManagedRuleRuntime() {}

    public static ManagedRuleSnapshot current() {
        return ReloadPublication.current()
                .managedRules();
    }

    /** 中文：扫描完整 Managed 候选但不发布；致命失败保留当前根代次。 / English: Scans a complete Managed candidate without publishing it; fatal failure retains the current root generation. */
    public static Optional<ManagedRuleSnapshot> prepare(
            Minecraft minecraft,
            long generation) {
        Objects.requireNonNull(minecraft, "minecraft");
        if (generation <= 0) {
            throw new IllegalArgumentException(
                    "generation must be positive");
        }
        ManagedRuleSnapshot previous = current();
        ArrayList<String> diagnostics = new ArrayList<>();
        ArrayList<ManagedRule> rules = new ArrayList<>();
        ArrayList<ManagedRuleDocument> documents = new ArrayList<>();
        Path root = ManagedPackLayout.current(minecraft).root();
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(root)) {
                diagnostics.add("MANAGED_WORKSPACE_UNSAFE");
            } else if (!scan(
                    root,
                    minecraft.getResourceManager(),
                    rules,
                    documents,
                    diagnostics)) {
                logDiagnostics(diagnostics);
                Constants.LOG.error(
                        "Retained Managed native authoring generation {} after fatal workspace scan failure",
                        previous.generation());
                return Optional.empty();
            }
        }
        if (diagnostics.contains("MANAGED_WORKSPACE_UNSAFE")) {
            logDiagnostics(diagnostics);
            Constants.LOG.error(
                    "Retained Managed native authoring generation {} because the workspace root is unsafe",
                    previous.generation());
            return Optional.empty();
        }
        ManagedRuleSnapshot next = ManagedRuleSnapshot.create(
                generation,
                managedPackPriority(minecraft.getResourceManager()),
                rules,
                documents,
                diagnostics);
        Constants.LOG.info(
                "Prepared Managed native authoring generation {}: rules={}, diagnostics={}",
                next.generation(),
                next.rules().size(),
                next.diagnostics().size());
        logDiagnostics(next.diagnostics());
        return Optional.of(next);
    }

    private static int managedPackPriority(ResourceManager resources) {
        int priority = 0;
        for (var pack : resources.listPacks().toList()) {
            if (ManagedPackIdentity.matches(pack)) {
                return priority;
            }
            priority++;
        }
        return 0;
    }

    private static boolean scan(
            Path root,
            ResourceManager resources,
            List<ManagedRule> rules,
            List<ManagedRuleDocument> managedDocuments,
            List<String> diagnostics) {
        try (var paths = Files.walk(root)) {
            List<Path> documents = paths
                    .filter(path -> Files.isRegularFile(
                            path,
                            LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> RuleDocumentCodec.recognizedManagedDocument(
                            relative(root, path)))
                    .sorted(Comparator.comparing(path -> relative(root, path)))
                    .limit(MAX_DOCUMENTS + 1L)
                    .toList();
            if (documents.size() > MAX_DOCUMENTS) {
                diagnostics.add("MANAGED_DOCUMENT_LIMIT_EXCEEDED");
                return false;
            }
            int order = 0;
            for (Path document : documents) {
                String relative = relative(root, document);
                try {
                    if (Files.size(document) > MAX_DOCUMENT_BYTES) {
                        diagnostics.add("MANAGED_DOCUMENT_TOO_LARGE:" + relative);
                        order++;
                        continue;
                    }
                    parse(
                            document,
                            relative,
                            resources,
                            order,
                            rules,
                            managedDocuments);
                } catch (IOException | RuntimeException exception) {
                    diagnostics.add(
                            "MANAGED_DOCUMENT_REJECTED:"
                                    + relative
                                    + ':'
                                    + exception.getClass().getSimpleName());
                }
                order++;
            }
            return true;
        } catch (IOException exception) {
            diagnostics.add(
                    "MANAGED_WORKSPACE_SCAN_FAILED:"
                            + exception.getClass().getSimpleName());
            return false;
        }
    }

    private static void parse(
            Path path,
            String relative,
            ResourceManager resources,
            int order,
            List<ManagedRule> rules,
            List<ManagedRuleDocument> managedDocuments)
            throws IOException {
        Optional<ParsedRuleDocument> parsed = RuleDocumentCodec.parseManaged(
                relative,
                Files.readAllBytes(path),
                MinecraftNativeBlockSelectorResolver.ALL_STATES);
        if (parsed.isEmpty()) {
            return;
        }
        ParsedRuleDocument document = parsed.orElseThrow();
        ManagedRuleDocument managedDocument =
                ParsedRuleProjection.managedDocument(document, order);
        if (document.family() != EngineFamily.FUSION) {
            managedDocuments.add(managedDocument);
        }
        rules.addAll(ParsedRuleProjection.managedRules(
                document,
                slots(document, resources),
                effectivePackId(resources, document.resourceId()),
                order));
        if (document.family() == EngineFamily.FUSION) {
            managedDocuments.add(managedDocument);
        }
    }

    private static List<NativeSlot> slots(
            ParsedRuleDocument document,
            ResourceManager resources) {
        Identifier resourceId = Identifier.tryParse(document.resourceId());
        if (resourceId == null) {
            throw new IllegalArgumentException(
                    "Managed resource id is invalid");
        }
        if (document.family() == EngineFamily.MCPATCHER) {
            return List.of();
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

    private static Optional<String> effectivePackId(
            ResourceManager resources,
            String resourceId) {
        Identifier id = Identifier.tryParse(resourceId);
        if (id == null) {
            return Optional.empty();
        }
        return resources.getResourceStack(id).stream()
                .reduce((first, second) -> second)
                .map(resource -> resource.sourcePackId());
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path)
                .toString()
                .replace('\\', '/');
    }

    private static void logDiagnostics(List<String> diagnostics) {
        diagnostics.forEach(value -> Constants.LOG.warn(
                "Managed native authoring: {}",
                value));
    }
}
