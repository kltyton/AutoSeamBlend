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
 * 中文：RED 静态源码契约——pane 工厂
 * {@link FabricAthenaGeneratedPaneModelFactory} 通过
 * {@link net.fabricmc.fabric.api.client.renderer.v1.sprite.FabricMaterialBaker#spriteFinder}
 * 解析方块图集 SpriteFinder 时，必须传已注册的图集 ID {@code AtlasIds.BLOCKS}，禁止传图集
 * 纹理文件路径 {@code TextureAtlas.LOCATION_BLOCKS}
 * （minecraft:textures/atlas/blocks.png）。Fabric API 的 spriteFinder(Identifier) 实现按
 * AtlasIds.BLOCKS/ITEMS 匹配，其他 id 静默返回 MissingSpriteFinderImpl——pane 顶臂 UV 会
 * 落到 missing 贴图（不崩溃但画面错误）。
 *
 * <p>English: RED static source contract -- when
 * {@link FabricAthenaGeneratedPaneModelFactory} resolves the block-atlas SpriteFinder
 * through {@link net.fabricmc.fabric.api.client.renderer.v1.sprite.FabricMaterialBaker
 * #spriteFinder}, it must pass the registered atlas id {@code AtlasIds.BLOCKS}; the atlas
 * texture path {@code TextureAtlas.LOCATION_BLOCKS} (minecraft:textures/atlas/blocks.png)
 * must never be used. Fabric API's spriteFinder(Identifier) implementation matches
 * AtlasIds.BLOCKS/ITEMS and silently returns MissingSpriteFinderImpl for any other id --
 * pane top-arm UVs would land on the missing texture (visual corruption, no crash).
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
                        "spriteFinder(AtlasIds.BLOCKS)"),
                "FabricMaterialBaker.spriteFinder must be registered with the block atlas id "
                        + "AtlasIds.BLOCKS; the atlas texture path is not a registered atlas id "
                        + "and Fabric API silently falls back to MissingSpriteFinderImpl");
    }

    @Test
    void paneSpriteFinderNeverUsesAtlasTexturePath() {
        String source = productionPaneFactorySource()
                .replaceAll("\\s+", "");

        assertFalse(
                source.contains(
                        "spriteFinder(TextureAtlas.LOCATION_BLOCKS)"),
                "FabricMaterialBaker.spriteFinder registers atlas ids, not atlas texture "
                        + "paths; TextureAtlas.LOCATION_BLOCKS "
                        + "(minecraft:textures/atlas/blocks.png) must not be passed");
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
