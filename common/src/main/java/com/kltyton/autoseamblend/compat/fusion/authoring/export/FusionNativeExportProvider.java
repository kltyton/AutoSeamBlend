package com.kltyton.autoseamblend.compat.fusion.authoring.export;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.export.managed.ManagedExportIr;
import com.kltyton.autoseamblend.authoring.export.ExportDraft;
import com.kltyton.autoseamblend.authoring.export.NativeExportProvider;
import java.io.IOException;

/** 中文：链接 Fusion 的原生 JSON 与模型导出实现。 / English: Fusion-linked native JSON/model export implementation. */
public enum FusionNativeExportProvider
        implements NativeExportProvider {
    INSTANCE;

    @Override
    public String engineId() {
        return "fusion";
    }

    @Override
    public EngineFamily family() {
        return EngineFamily.FUSION;
    }

    @Override
    public ManagedExportIr.Rule assemble(
            int order,
            ExportDraft draft)
            throws IOException {
        if (draft.targetless()) {
            return draft.targetlessRule(order);
        }
        return FusionManagedExportAssembler
                .assemble(
                        order,
                        draft.rule(),
                        draft.surface(),
                        draft.source(),
                        draft.topSource(),
                        draft.nativeDocument());
    }
}
