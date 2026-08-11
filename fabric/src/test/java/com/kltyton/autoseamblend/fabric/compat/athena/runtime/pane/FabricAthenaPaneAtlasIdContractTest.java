package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 静态源码契约——1.20.1 pane 工厂
 * {@link FabricAthenaGeneratedPaneModelFactory} 生成的连接材质必须绑定到方块图集
 * {@code TextureAtlas.LOCATION_BLOCKS}（1.20.1 中该位置即注册的图集 id），并由调用方传入的
 * textureGetter 解析精灵；禁止使用 26.1.2 的 AtlasIds/spriteFinder API。
 *
 * <p>English: RED static source contract -- on 1.20.1 the pane factory
 * {@link FabricAthenaGeneratedPaneModelFactory} must bind generated connection materials
 * to the block atlas {@code TextureAtlas.LOCATION_BLOCKS} (the registered atlas id on
 * 1.20.1) and resolve sprites through the caller-provided textureGetter; the 26.1.2
 * AtlasIds/spriteFinder APIs must not appear.
 */
class FabricAthenaPaneAtlasIdContractTest {
    @Test
    void paneSpriteFinderRegistersBlockAtlasId() {
        // 中文：压缩空白后再断言，避免合法换行破坏“spriteFinder 必须使用图集 ID”的接线契约。
        // English: Whitespace is compacted before asserting so legal line wrapping never
        // breaks the wiring contract that spriteFinder must use the atlas id.
        String source = productionPaneFactorySource()
                .replaceAll("\\s+", "");

        assertTrue(
                source.contains(
                        "TextureAtlas.LOCATION_BLOCKS"),
                "pane generated materials must bind to the block atlas "
                        + "TextureAtlas.LOCATION_BLOCKS on 1.20.1");
        assertTrue(
                source.contains(
                        "textureGetter.apply("),
                "pane sprites must resolve through the caller textureGetter");
    }

    @Test
    void paneSpriteFinderNeverUsesAtlasTexturePath() {
        String source = productionPaneFactorySource()
                .replaceAll("\\s+", "");

        assertFalse(
                source.contains("AtlasIds"),
                "the 26.1.2 AtlasIds constant must not be referenced on 1.20.1");
    }

    /**
     * 中文：读取 pane 工厂当前源码（只读静态证据）；按测试工作目录依次尝试模块、工程与聚合
     * 仓库三种根位置，未找到时以明确断言失败。
     *
     * <p>English: Reads the current source of the Fabric pane factory (read-only static
     * evidence), trying the module, project, and aggregate-repository root positions in
     * order from the test working directory, and fails explicitly when absent.
     */
    private static String productionPaneFactorySource() {
        String relative =
                "com/kltyton/autoseamblend/fabric/compat/athena/runtime/pane/"
                        + "FabricAthenaGeneratedPaneModelFactory.java";
        List<Path> candidates = List.of(
                Path.of("src/main/java", relative),
                Path.of(
                        "fabric/src/main/java",
                        relative),
                Path.of(
                        "26.1.2/AutoSeamBlend-26.1.2/fabric/src/main/java",
                        relative));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                try {
                    return Files.readString(candidate);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            }
        }
        throw new AssertionError(
                "FabricAthenaGeneratedPaneModelFactory source not found from "
                        + Path.of("").toAbsolutePath());
    }
}
