package com.kltyton.autoseamblend.authoring.preview;

import com.kltyton.autoseamblend.authoring.preview.PreviewFaceResult;
import com.kltyton.autoseamblend.engine.EngineFamily;
import java.util.List;
import java.util.Optional;

/** 中文：不依赖第三方类型的预览 SPI；实现保留在各自引擎的 compat 包中。 / English: Third-party-free preview SPI. Implementations remain inside their engine compat package. */
public interface PreviewProvider {
    String engineId();

    EngineFamily family();

    List<PreviewSample> sample(
            PreviewQuery query);

    /**
     * 中文：返回引擎原生处理器为本次精确查询实际选择的最终面图层。
     *
     * English:
     * Returns the final face layers actually selected by the engine's native
     * processor for this exact query.
     */
    default Optional<PreviewFaceResult>
            exactFace(
                    PreviewQuery query,
                    List<PreviewSample>
                            samples) {
        return Optional.empty();
    }
}
