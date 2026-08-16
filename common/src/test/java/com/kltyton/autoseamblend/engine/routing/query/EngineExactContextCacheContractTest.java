package com.kltyton.autoseamblend.engine.routing.query;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 合同——生成代次作用域的 exact-context 缓存：相同的精确查询只做一次
 * 不可变上下文准备（EXACT_CONTEXT_CACHE + StateQueryContext），而原生观察仍逐查询
 * 动态产生。1.21.1 当前 EngineQueryRouterCore 只有 SUMMARY_CACHE，缺少该缓存。
 *
 * <p>English: RED contract -- a generation-scoped exact-context cache prepares the
 * immutable query context exactly once across repeated exact queries
 * (EXACT_CONTEXT_CACHE + StateQueryContext) while native observations stay per
 * query. The current 1.21.1 EngineQueryRouterCore only carries SUMMARY_CACHE.
 */
class EngineExactContextCacheContractTest {
    @Test
    void routerDeclaresGenerationScopedExactContextCache()
            throws IOException {
        String source = Files.readString(
                routerSource(),
                StandardCharsets.UTF_8);

        assertTrue(
                source.matches(
                        "(?s).*EXACT_CONTEXT_CACHE\\s*=.*"
                                + "PublicationScopedCache<.*>.*"),
                "exact router must declare a generation-scoped "
                        + "EXACT_CONTEXT_CACHE; current source:\n"
                        + source);
    }

    @Test
    void exactQueryComputesStateContextOnceThroughStateQueryContext()
            throws IOException {
        String source = Files.readString(
                routerSource(),
                StandardCharsets.UTF_8);

        assertTrue(
                source.matches(
                        "(?s).*EXACT_CONTEXT_CACHE\\s*\\.entries\\([^)]*\\)"
                                + "\\s*\\.computeIfAbsent\\s*\\(\\s*state\\s*,"
                                + "\\s*key\\s*->\\s*StateQueryContext\\.create.*"),
                "exact queries must compute the immutable state context once "
                        + "through StateQueryContext.create; current source:\n"
                        + source);
    }

    @Test
    void nativeObservationStaysPerQueryOutsideContextCache()
            throws IOException {
        String source = Files.readString(
                routerSource(),
                StandardCharsets.UTF_8);

        assertTrue(
                source.matches(
                        "(?s).*contextFactory\\.create\\s*\\(.*"
                                + "EngineQueryRouting\\.observations\\s*\\(.*"),
                "native observations must remain per-query and must not be "
                        + "served from the immutable context cache; current source:\n"
                        + source);
    }

    private static Path routerSource() {
        Path relative = Paths.get(
                "com/kltyton/autoseamblend/engine/routing/query/"
                        + "EngineQueryRouterCore.java");
        List<Path> candidates = List.of(
                Paths.get("src/main/java").resolve(relative),
                Paths.get("common/src/main/java").resolve(relative),
                Paths.get("1.21.1/AutoSeamBlend-1.21.1/common/src/main/java")
                        .resolve(relative));
        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElse(candidates.get(0));
    }
}
