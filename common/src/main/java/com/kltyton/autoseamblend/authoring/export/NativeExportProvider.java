package com.kltyton.autoseamblend.authoring.export;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.export.managed.ManagedExportIr;
import java.io.IOException;

/** 中文：原生引擎导出的通用服务提供器。 / English: Loader-neutral provider for native engine exports. */
public interface NativeExportProvider {
    String engineId();

    EngineFamily family();

    ManagedExportIr.Rule assemble(
            int order,
            ExportDraft draft)
            throws IOException;
}
