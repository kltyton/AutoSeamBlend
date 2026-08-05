package com.kltyton.autoseamblend.compat.ctm_mod.authoring.template;

import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringFile;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringRule;
import com.kltyton.autoseamblend.authoring.template.ManagedAuthoringTemplates;
import java.util.List;

/**
 * 中文：把 NeoForge 独占 CTM Mod 的 authoring 模板接入公共
 * ManagedAuthoringTemplates 家族注册点。
 *
 * English: Connects the NeoForge-only CTM Mod authoring template to the shared
 * ManagedAuthoringTemplates family registry.
 */
public enum CtmModAuthoringTemplateExtension
        implements ManagedAuthoringTemplates.FamilyTemplate {
    INSTANCE;

    @Override
    public List<ManagedAuthoringFile> create(
            ManagedAuthoringRule rule) {
        return CtmModAuthoringTemplate.create(rule);
    }
}
