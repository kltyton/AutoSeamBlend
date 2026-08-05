package com.kltyton.autoseamblend.engine.registry;

import com.kltyton.autoseamblend.engine.EngineDescriptor;
import com.kltyton.autoseamblend.engine.EngineFamily;
import java.util.List;
import java.util.Objects;

/**
 * 中文：一个可选引擎的中立描述与链接门；不携带任何 Loader 或第三方类型。
 * English: Loader-neutral description and linkage gate for one optional engine; it carries no
 * loader or third-party types.
 */
public record EngineDefinition(EngineDescriptor descriptor, List<String> hooks) {
    public EngineDefinition {
        Objects.requireNonNull(descriptor, "descriptor");
        hooks = List.copyOf(Objects.requireNonNull(hooks, "hooks"));
        if (hooks.stream().anyMatch(hook -> hook == null || hook.isBlank())) {
            throw new IllegalArgumentException("hooks must contain non-blank paths");
        }
    }

    /**
     * 中文：集中构造稳定描述，避免各 Loader 复制描述符拼装逻辑。
     * English: Centralizes stable descriptor construction so loaders do not duplicate it.
     */
    public static EngineDefinition of(
            String engineId,
            EngineFamily family,
            String formatId,
            String expectedVersion,
            String hookContract,
            String... hooks) {
        return new EngineDefinition(
                new EngineDescriptor(
                        engineId,
                        family,
                        formatId,
                        engineId,
                        expectedVersion,
                        hookContract),
                List.of(hooks));
    }
}
