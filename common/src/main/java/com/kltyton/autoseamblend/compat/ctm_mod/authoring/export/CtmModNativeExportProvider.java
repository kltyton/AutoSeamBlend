package com.kltyton.autoseamblend.compat.ctm_mod.authoring.export;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.export.CtmModManagedExportAssembler;
import com.kltyton.autoseamblend.export.managed.ManagedExportIr;
import com.kltyton.autoseamblend.authoring.export.ExportDraft;
import com.kltyton.autoseamblend.authoring.export.NativeExportProvider;
import java.io.IOException;

/** 中文：不链接 CTM Mod 实现类的 CTM Mod 导出边界。 / English: CTM Mod export boundary without linking CTM Mod implementation classes. */
public enum CtmModNativeExportProvider
        implements NativeExportProvider {
    INSTANCE;

    @Override
    public String engineId() {
        return "ctm";
    }

    @Override
    public EngineFamily family() {
        return EngineFamily.CTM_MOD;
    }

    @Override
    public ManagedExportIr.Rule assemble(
            int order,
            ExportDraft draft)
            throws IOException {
        if (draft.targetless()) {
            return draft.targetlessRule(order, CtmModNativeDocumentBaker::bakedPassthrough);
        }
        return CtmModManagedExportAssembler
                .assemble(
                        order,
                        draft.rule(),
                        draft.surface(),
                        draft.source(),
                        draft.topSource(),
                        draft.nativeDocument());
    }
}
