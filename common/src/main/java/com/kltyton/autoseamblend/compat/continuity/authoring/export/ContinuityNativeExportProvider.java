package com.kltyton.autoseamblend.compat.continuity.authoring.export;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.export.managed.ManagedExportIr;
import com.kltyton.autoseamblend.authoring.export.ExportDraft;
import com.kltyton.autoseamblend.authoring.export.NativeExportProvider;
import java.io.IOException;

/** 中文：Continuity MCPatcher 格式的共享导出实现。 / English: Shared Continuity MCPatcher-format export implementation. */
public enum ContinuityNativeExportProvider
        implements NativeExportProvider {
    INSTANCE;

    @Override
    public String engineId() {
        return "continuity";
    }

    @Override
    public EngineFamily family() {
        return EngineFamily.MCPATCHER;
    }

    @Override
    public ManagedExportIr.Rule assemble(
            int order,
            ExportDraft draft)
            throws IOException {
        if (draft.targetless()) {
            return draft.targetlessRule(order);
        }
        return ContinuityManagedExportAssembler
                .assemble(
                        order,
                        draft.rule(),
                        draft.surface(),
                        draft.source(),
                        draft.topSource(),
                        draft.nativeDocument());
    }
}
