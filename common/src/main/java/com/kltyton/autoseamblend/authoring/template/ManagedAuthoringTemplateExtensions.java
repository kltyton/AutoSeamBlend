package com.kltyton.autoseamblend.authoring.template;

import com.kltyton.autoseamblend.engine.EngineFamily;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 中文：Loader 独占格式家族的 authoring 模板注册表。
 *
 * English: Registry for Loader-exclusive format-family authoring templates.
 */
final class ManagedAuthoringTemplateExtensions {
    private static final ConcurrentMap<
                    EngineFamily,
                    ManagedAuthoringTemplates.FamilyTemplate>
            EXTENSIONS = new ConcurrentHashMap<>();

    private ManagedAuthoringTemplateExtensions() {}

    static void register(
            EngineFamily family,
            ManagedAuthoringTemplates.FamilyTemplate template) {
        EXTENSIONS.put(
                Objects.requireNonNull(family, "family"),
                Objects.requireNonNull(template, "template"));
    }

    static ManagedAuthoringTemplates.FamilyTemplate get(
            EngineFamily family) {
        return EXTENSIONS.get(family);
    }
}
