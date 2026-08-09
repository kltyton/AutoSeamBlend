package com.kltyton.autoseamblend.authoring.preview;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 中文：保存进程级预览场景 Quad 处理能力；第三方实现只从各自 compat 引导注册。
 *
 * English: Stores process-lifetime preview-scene quad processing capabilities;
 * third-party implementations register only from their compat bootstrap.
 */
public final class PreviewSceneQuadProcessorRegistry {
    private static final ConcurrentHashMap<String, PreviewSceneQuadProcessor>
            PROCESSORS = new ConcurrentHashMap<>();

    private PreviewSceneQuadProcessorRegistry() {}

    public static void register(
            PreviewSceneQuadProcessor processor) {
        PreviewSceneQuadProcessor checked =
                Objects.requireNonNull(
                        processor,
                        "processor");
        PreviewSceneQuadProcessor previous =
                PROCESSORS.putIfAbsent(
                        checked.engineId(),
                        checked);
        if (previous != null
                && previous != checked
                && !previous.getClass()
                        .equals(
                                checked.getClass())) {
            throw new IllegalStateException(
                    "preview scene quad processor already registered: "
                            + checked.engineId());
        }
    }

    public static Optional<PreviewSceneQuadProcessor>
            find(String engineId) {
        return Optional.ofNullable(
                PROCESSORS.get(
                        Objects.requireNonNull(
                                engineId,
                                "engineId")));
    }
}
