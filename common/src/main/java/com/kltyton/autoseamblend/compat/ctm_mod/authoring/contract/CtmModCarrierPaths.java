package com.kltyton.autoseamblend.compat.ctm_mod.authoring.contract;

import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringRule;
import java.util.Objects;

/** 中文：统一 CTM Mod 生成载体路径。 / English: Canonical paths for generated CTM Mod carriers. */
public final class CtmModCarrierPaths {
    private CtmModCarrierPaths() {}

    public static String generatedId(
            ManagedAuthoringRule rule,
            String role) {
        Objects.requireNonNull(rule, "rule");
        if (role == null || role.isBlank() || role.indexOf('/') >= 0) {
            throw new IllegalArgumentException("carrier role must be a single path segment");
        }
        return "autoseamblend:generated/ctm_mod/"
                + rule.resolvedMethod().serializedName()
                + '/'
                + rule.managedStem()
                + '/'
                + role;
    }
}
