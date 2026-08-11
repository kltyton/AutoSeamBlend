package com.kltyton.autoseamblend.forge.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 中文：锁定可选引擎 Mixin 在早期启动阶段的版本来源回退语义。
 * English: Locks the optional-engine Mixin version-source fallback during early startup.
 */
class OptionalEngineMixinPluginVersionFallbackTest {

    @Test
    void usesLoadingVersionWhileRuntimeModListIsNotPopulated() {
        assertEquals(
                Optional.of("1.3.12"),
                OptionalEngineMixinPlugin.selectVersion(
                        Optional.empty(),
                        Optional.of("1.3.12")));
    }

    @Test
    void keepsPresentRuntimeVersionAuthoritative() {
        assertEquals(
                Optional.of("runtime"),
                OptionalEngineMixinPlugin.selectVersion(
                        Optional.of("runtime"),
                        Optional.of("loading")));
    }

    @Test
    void remainsEmptyWhenNeitherListContainsTheEngine() {
        assertTrue(OptionalEngineMixinPlugin.selectVersion(
                        Optional.empty(),
                        Optional.empty())
                .isEmpty());
    }
}
