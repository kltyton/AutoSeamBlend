package com.kltyton.autoseamblend.authoring.template;

import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringFile;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringProject;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringRule;
import com.kltyton.autoseamblend.engine.EngineFamily;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 中文：首次保存原生创作文档的唯一格式家族分派器。 / English: Sole family dispatcher for first-save native authoring documents. */
public final class ManagedAuthoringTemplates {
    private ManagedAuthoringTemplates() {}

    public static ManagedAuthoringProject create(
            EngineFamily family,
            List<ManagedAuthoringRule> rules) {
        Objects.requireNonNull(family, "family");
        rules = List.copyOf(
                Objects.requireNonNull(rules, "rules"));
        if (rules.isEmpty()) {
            throw new IllegalArgumentException(
                    "at least one authoring rule is required");
        }
        FamilyTemplate extension =
                ManagedAuthoringTemplateExtensions.get(family);
        ArrayList<ManagedAuthoringFile> files =
                new ArrayList<>();
        for (ManagedAuthoringRule rule : rules) {
            files.addAll(switch (family) {
                case MCPATCHER ->
                        MCPatcherAuthoringTemplate.create(rule);
                case FUSION ->
                        FusionAuthoringTemplate.create(rule);
                case ATHENA ->
                        AthenaAuthoringTemplate.create(rule);
                default -> {
                    if (extension == null) {
                        throw new IllegalArgumentException(
                                "LOADER_EXCLUSIVE_TEMPLATE_REQUIRES_ADAPTER");
                    }
                    yield extension.create(rule);
                }
            });
        }
        return new ManagedAuthoringProject(
                family,
                files);
    }

    /**
     * 中文：注册 Loader 独占格式家族（如 NeoForge 的 CTM Mod）的 authoring 模板。
     *
     * English: Registers an authoring template for a Loader-exclusive format
     * family such as CTM Mod on NeoForge.
     */
    public static void registerFamily(
            EngineFamily family,
            FamilyTemplate template) {
        ManagedAuthoringTemplateExtensions.register(
                family,
                template);
    }

    /**
     * 中文：Loader 独占格式家族的 authoring 模板契约。
     *
     * English: Contract for a Loader-exclusive format-family authoring template.
     */
    @FunctionalInterface
    public interface FamilyTemplate {
        List<ManagedAuthoringFile> create(
                ManagedAuthoringRule rule);
    }
}
