package com.kltyton.autoseamblend.compat.athena.authoring.export;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.export.managed.ManagedExportIr;
import com.kltyton.autoseamblend.authoring.export.ExportDraft;
import com.kltyton.autoseamblend.authoring.export.NativeExportProvider;
import java.io.IOException;

/** 中文：链接 Athena 的原生 blockstate 与模型导出实现。 / English: Athena-linked native blockstate/model export implementation. */
public enum AthenaNativeExportProvider
        implements NativeExportProvider {
    INSTANCE;

    @Override
    public String engineId() {
        return "athena";
    }

    @Override
    public EngineFamily family() {
        return EngineFamily.ATHENA;
    }

    @Override
    public ManagedExportIr.Rule assemble(
            int order,
            ExportDraft draft)
            throws IOException {
        if (draft.targetless()) {
            return draft.targetlessRule(order);
        }
        return AthenaManagedExportAssembler
                .assemble(
                        order,
                        draft.rule(),
                        draft.surface(),
                        draft.source(),
                        draft.topSource(),
                        draft.nativeDocument());
    }
}
