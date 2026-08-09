package com.kltyton.autoseamblend.authoring.project;

import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringDraft;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.reload.rule.ManagedRule;
import com.kltyton.autoseamblend.reload.rule.ManagedRuleDocument;
import com.kltyton.autoseamblend.reload.rule.ManagedRuleSnapshot;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * 中文：工作台目标条目的公共规划器；Loader 只提供当前引擎选择回调。
 * English: Common workbench target planner; the Loader supplies only engine-family selection.
 */
public final class WorkbenchTargetCatalog {
    private WorkbenchTargetCatalog() {}

    public static List<Entry> current(
            EngineFamily defaultFamily,
            ReloadPublication.Generation runtime,
            EngineFamilySelector selector) {
        Objects.requireNonNull(defaultFamily, "defaultFamily");
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(selector, "selector");
        ConnectionRuleSet<Block> configRules = runtime.selectors().rules();
        LinkedHashMap<String, ConfigTarget> configured = configured(configRules);
        LinkedHashSet<String> coveredTargets = new LinkedHashSet<>();
        ArrayList<Entry> result = new ArrayList<>();
        ManagedRuleSnapshot managed = runtime.managedRules();
        managed.documents().stream()
                .sorted(Comparator.comparingInt(ManagedRuleDocument::order))
                .forEach(document -> {
                    document.targetBlockIds().forEach(coveredTargets::add);
                    Optional<String> receiver = receiver(document);
                    Optional<ManagedAuthoringDraft> baseDraft = receiver.flatMap(
                            blockId -> draft(blockId, document.family(), runtime));
                    Optional<ManagedRule> rule = document.targetBlockIds().stream()
                            .flatMap(target -> managed.rules(document.family(), target).stream())
                            .filter(candidate -> candidate.documentPath()
                                    .equals(document.documentPath()))
                            .min(Comparator.comparingInt(ManagedRule::order));
                    ConnectionMethod method = rule.map(ManagedRule::requestedMethod)
                            .orElseGet(() -> baseDraft.map(ManagedAuthoringDraft::requestedMethod)
                                    .orElse(ConnectionMethod.AUTO));
                    boolean compatibility = rule.map(ManagedRule::compatibility).orElse(true);
                    Optional<ManagedAuthoringDraft> draft = baseDraft.map(base ->
                            new ManagedAuthoringDraft(
                                    base.targetBlockId(),
                                    base.sourceTextureId(),
                                    base.originalModelId(),
                                    method,
                                    method == ConnectionMethod.AUTO
                                            ? base.resolvedMethod()
                                            : method,
                                    compatibility,
                                    base.pane()));
                    boolean configuredAtOpen = document.targetBlockIds().stream()
                            .anyMatch(configured::containsKey);
                    result.add(new Entry(
                            document.key(),
                            document.entryId(),
                            receiver,
                            document.family(),
                            true,
                            configuredAtOpen,
                            document.documentPath(),
                            method,
                            compatibility,
                            draft));
                });
        configured.forEach((blockId, target) -> {
            if (coveredTargets.contains(blockId)) {
                return;
            }
            EngineFamily family = selector.select(target.block(), defaultFamily, runtime);
            Optional<ManagedAuthoringDraft> draft = draft(blockId, family, runtime)
                    .map(base -> new ManagedAuthoringDraft(
                            base.targetBlockId(),
                            base.sourceTextureId(),
                            base.originalModelId(),
                            target.method(),
                            target.method() == ConnectionMethod.AUTO
                                    ? base.resolvedMethod()
                                    : target.method(),
                            target.compatibility(),
                            base.pane()));
            result.add(new Entry(
                    "config:" + blockId,
                    blockId,
                    Optional.of(blockId),
                    family,
                    false,
                    true,
                    "",
                    target.method(),
                    target.compatibility(),
                    draft));
        });
        return List.copyOf(result);
    }

    public static Optional<Entry> newManaged(
            String blockId,
            EngineFamily family,
            ReloadPublication.Generation runtime) {
        Objects.requireNonNull(blockId, "blockId");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(runtime, "runtime");
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return Optional.empty();
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null || block.defaultBlockState().isAir()) {
            return Optional.empty();
        }
        Optional<ManagedAuthoringDraft> draft =
                com.kltyton.autoseamblend.authoring.model.ManagedAuthoringProjectDrafts
                        .forBlock(block, family, runtime);
        return draft.map(value -> new Entry(
                "new:" + family.formatId() + ':' + blockId,
                blockId,
                Optional.of(blockId),
                family,
                true,
                false,
                "",
                value.requestedMethod(),
                value.compatibility(),
                Optional.of(value)));
    }

    private static LinkedHashMap<String, ConfigTarget> configured(
            ConnectionRuleSet<Block> rules) {
        LinkedHashMap<String, ConfigTarget> result = new LinkedHashMap<>();
        for (ConnectionRuleSet.Target<Block> target : rules.targets()) {
            String blockId = BuiltInRegistries.BLOCK.getKey(target.value()).toString();
            result.putIfAbsent(
                    blockId,
                    new ConfigTarget(
                            target.value(),
                            target.method(),
                            target.resourcePackMode()
                                    == ConnectionRuleSet.ResourcePackMode.COMPATIBILITY));
        }
        return result;
    }

    private static Optional<String> receiver(ManagedRuleDocument document) {
        return document.targetBlockIds().stream()
                .filter(WorkbenchTargetCatalog::registered)
                .findFirst();
    }

    private static Optional<ManagedAuthoringDraft> draft(
            String blockId,
            EngineFamily family,
            ReloadPublication.Generation runtime) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null) {
            return Optional.empty();
        }
        return runtime.surfaces().representative(block)
                .map(candidate ->
                        com.kltyton.autoseamblend.authoring.model.ManagedAuthoringProjectDrafts
                                .forSurface(candidate.state(), candidate.surface(), family, runtime));
    }

    private static boolean registered(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id != null
                && BuiltInRegistries.BLOCK.containsKey(id)
                && BuiltInRegistries.BLOCK.getOptional(id).orElse(null) != Blocks.AIR;
    }

    @FunctionalInterface
    public interface EngineFamilySelector {
        EngineFamily select(
                Block block,
                EngineFamily fallback,
                ReloadPublication.Generation runtime);
    }

    public record Entry(
            String entryKey,
            String entryId,
            Optional<String> receiverBlockId,
            EngineFamily family,
            boolean managed,
            boolean configured,
            String documentPath,
            ConnectionMethod method,
            boolean compatibility,
            Optional<ManagedAuthoringDraft> draft) {
        public Entry {
            if (entryKey == null || entryKey.isBlank()
                    || entryId == null || entryId.isEmpty()) {
                throw new IllegalArgumentException("invalid workbench entry identity");
            }
            Optional<String> checkedReceiver = Objects.requireNonNull(
                    receiverBlockId,
                    "receiverBlockId");
            checkedReceiver.ifPresent(blockId -> {
                if (!registered(blockId)) {
                    throw new IllegalArgumentException("invalid workbench receiver");
                }
            });
            Objects.requireNonNull(family, "family");
            documentPath = Objects.requireNonNull(documentPath, "documentPath");
            Objects.requireNonNull(method, "method");
            Optional<ManagedAuthoringDraft> checkedDraft = Objects.requireNonNull(
                    draft,
                    "draft");
            checkedDraft.ifPresent(value -> {
                if (checkedReceiver.isEmpty()
                        || !checkedReceiver.orElseThrow().equals(value.targetBlockId())) {
                    throw new IllegalArgumentException(
                            "draft receiver differs from document receiver");
                }
            });
            receiverBlockId = checkedReceiver;
            draft = checkedDraft;
        }
    }

    private record ConfigTarget(
            Block block,
            ConnectionMethod method,
            boolean compatibility) {
        private ConfigTarget {
            Objects.requireNonNull(block, "block");
            Objects.requireNonNull(method, "method");
        }
    }
}
