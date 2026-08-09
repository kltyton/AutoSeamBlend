package com.kltyton.autoseamblend.fabric.compat.fusion.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 合同——Fusion 运行时 wrapper 不得链接 Continuity 包名。
 * FabricFusionConnectedBlockStateModel 目前硬引用
 * me.pepperbell.continuity.client.util.RenderUtil（BufferingEmitter.emit 内），Fusion-only
 * 安装没有 Continuity，wrapper 一旦启用即抛 NoClassDefFoundError。测试读取生产源码并断言
 * 不存在该包名；失败消息明确 Fusion-only 场景。测试类路径始终带 Continuity，无法用运行时
 * 链接复现，因此采用源码依赖合同。
 *
 * <p>English: RED contract -- the Fusion runtime wrapper must not link Continuity package
 * names. FabricFusionConnectedBlockStateModel currently hard-references
 * me.pepperbell.continuity.client.util.RenderUtil inside BufferingEmitter.emit; Fusion-only
 * installs ship no Continuity, so activating the wrapper raises NoClassDefFoundError. The
 * test reads the production source and asserts the package name is absent; the failure
 * message names the Fusion-only scenario. Continuity is always present on the test
 * classpath, so a runtime linkage test cannot reproduce the defect; a source dependency
 * contract is used instead.
 */
class FabricFusionRuntimeIsolationContractTest {
    @Test
    void fusionWrapperMustNotLinkContinuityPackage() {
        String source = productionSource(
                "FabricFusionConnectedBlockStateModel.java");
        assertFalse(
                source.contains(
                        "me.pepperbell.continuity"),
                "Fusion-only installs never ship Continuity; the hard reference to "
                        + "me.pepperbell.continuity.client.util.RenderUtil in "
                        + "FabricFusionConnectedBlockStateModel raises NoClassDefFoundError "
                        + "once the Fusion wrapper activates");
    }

    /**
     * 中文：读取 Fabric 生产 wrapper 的当前源码（只读静态证据）；按测试工作目录依次尝试
     * 模块/工程/聚合仓库三种根位置，未找到时以明确断言失败。
     *
     * <p>English: Reads the current source of the Fabric production wrapper (read-only static
     * evidence), trying the module, project, and aggregate-repository root positions in order
     * from the test working directory, and fails explicitly when absent.
     */
    private static String productionSource(
            String fileName) {
        String relative =
                "com/kltyton/autoseamblend/fabric/compat/fusion/runtime/"
                        + fileName;
        List<Path> candidates = List.of(
                Path.of("src/main/java", relative),
                Path.of(
                        "fabric/src/main/java",
                        relative),
                Path.of(
                        "26.1.2/AutoSeamBlend-26.1.2/"
                                + "fabric/src/main/java",
                        relative));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                try {
                    return Files.readString(candidate);
                } catch (IOException exception) {
                    throw new UncheckedIOException(
                            exception);
                }
            }
        }
        throw new AssertionError(
                "production source not found from "
                        + Path.of("").toAbsolutePath());
    }
}
