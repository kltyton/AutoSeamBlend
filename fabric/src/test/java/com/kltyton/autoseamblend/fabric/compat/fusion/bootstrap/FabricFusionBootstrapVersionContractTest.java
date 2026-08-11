package com.kltyton.autoseamblend.fabric.compat.fusion.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 中文：源码合同——锁定 Fabric Fusion bootstrap 精确版本门禁。FusionMixinPlugin 必须通过
 * MixinPluginSelector.exactVersion 要求 Fusion 精确为 1.3.12；fabric.mod.json 的 suggests
 * 必须为 fusion=1.3.12；已删除的 FusionPublicSurfaceSupport 死类以及生产源码中的 1.3.5
 * 旧版本残留不得回归。测试读取生产源码/资源文本（只读静态证据），与
 * FabricFusionRuntimeIsolationContractTest 同一模式，不引入额外依赖。
 *
 * <p>English: Source contract -- locks the exact-version Fabric Fusion bootstrap gate.
 * FusionMixinPlugin must require Fusion exactly 1.3.12 through
 * MixinPluginSelector.exactVersion; fabric.mod.json must suggest fusion=1.3.12; the deleted
 * FusionPublicSurfaceSupport dead class and any 1.3.5 residue in Fusion production sources
 * must not regress. The test reads production source/resource text (read-only static
 * evidence), following the same pattern as FabricFusionRuntimeIsolationContractTest without
 * new dependencies.
 */
class FabricFusionBootstrapVersionContractTest {
    @Test
    void fusionMixinPluginRequiresExactVersionOneThreeTwelve() {
        String source = productionSource(
                "fabric",
                "main",
                "java",
                "com/kltyton/autoseamblend/fabric/compat/fusion/bootstrap/FusionMixinPlugin.java");
        assertTrue(
                source.contains("MixinPluginSelector.exactVersion("),
                "FusionMixinPlugin must gate through MixinPluginSelector.exactVersion; "
                        + "isModLoaded alone cannot enforce the 1.3.12 registry pin");
        assertTrue(
                source.contains("expectedVersion(\"fusion\")"),
                "FusionMixinPlugin must pin the exact version through the shared "
                        + "FabricEngineRegistry.expectedVersion(\"fusion\") registry pin");
    }

    @Test
    void fabricModJsonSuggestsFusionOneThreeTwelve() {
        String modJson = productionSource(
                "fabric", "main", "resources", "fabric.mod.json");
        assertTrue(
                modJson.contains("\"fusion\": \"1.3.12\""),
                "fabric.mod.json must suggest fusion 1.3.12 (the registry-exact version)");
        assertFalse(
                modJson.contains("\"fusion\": \"*\""),
                "fabric.mod.json must not suggest an unbounded fusion version");
        assertTrue(
                modJson.contains("\"continuity\": \"*\""),
                "other engine suggestions must stay unchanged");
        assertTrue(
                modJson.contains("\"athena\": \"*\""),
                "other engine suggestions must stay unchanged");
    }

    @Test
    void deadClassAndOldVersionResidueRemoved() {
        Path deadClass = projectRoot()
                .resolve("common/src/main/java/com/kltyton/autoseamblend/compat/fusion/preview/FusionPublicSurfaceSupport.java");
        assertFalse(
                Files.exists(deadClass),
                "FusionPublicSurfaceSupport.java is a zero-reference dead class carrying the "
                        + "outdated 1.3.5 state string and must stay deleted");

        List<Path> fusionProductionDirs = List.of(
                projectRoot().resolve("fabric/src/main/java/com/kltyton/autoseamblend/fabric/compat/fusion"),
                projectRoot().resolve("common/src/main/java/com/kltyton/autoseamblend/compat/fusion"));
        for (Path directory : fusionProductionDirs) {
            assertTrue(
                    Files.isDirectory(directory),
                    "missing Fusion production source directory: " + directory);
            try (Stream<Path> walk = Files.walk(directory)) {
                List<Path> offenders = walk
                        .filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".java"))
                        .filter(path -> containsText(path, "1.3.5"))
                        .toList();
                assertTrue(
                        offenders.isEmpty(),
                        "outdated Fusion 1.3.5 residue must not regress: " + offenders);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }

    private static boolean containsText(Path file, String fragment) {
        try {
            return Files.readString(file).contains(fragment);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * 中文：从测试工作目录按模块/工程/聚合仓库三种根位置解析生产文件路径（只读静态证据）。
     *
     * English: Resolves a production file from the test working directory across module,
     * project, and aggregate-repository root positions (read-only static evidence).
     */
    private static Path productionFile(
            String module,
            String sourceSet,
            String kind,
            String relative) {
        String moduleRelative =
                module + "/src/" + sourceSet + "/" + kind + "/" + relative;
        List<Path> candidates = List.of(
                Path.of("src", sourceSet, kind, relative),
                Path.of(moduleRelative),
                Path.of("..", moduleRelative),
                Path.of("26.1.2/AutoSeamBlend-26.1.2", moduleRelative));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError(
                "production file not found from "
                        + Path.of("").toAbsolutePath()
                        + " (tried " + candidates + ")");
    }

    private static String productionSource(
            String module,
            String sourceSet,
            String kind,
            String relative) {
        Path file = productionFile(module, sourceSet, kind, relative);
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Path projectRoot() {
        List<Path> candidates = List.of(
                Path.of(".."),
                Path.of(""),
                Path.of("26.1.2/AutoSeamBlend-26.1.2"));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))
                    && Files.isDirectory(candidate.resolve("common"))) {
                return candidate;
            }
        }
        throw new AssertionError(
                "AutoSeamBlend-26.1.2 project root not found from "
                        + Path.of("").toAbsolutePath());
    }
}
