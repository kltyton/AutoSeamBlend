package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 静态源码契约——Fabric Athena 运行时捕获方块 quad 时，
 * {@link FabricAthenaConnectedBlockStateModel} 的 {@code emitQuads} 必须用已注册的方块图集 ID
 * {@code AtlasIds.BLOCKS} 调用 {@code AtlasManager.getAtlasOrThrow}；禁止把图集纹理文件
 * 路径 {@code TextureAtlas.LOCATION_BLOCKS}（minecraft:textures/atlas/blocks.png）当图集 ID
 * 传入，否则运行时抛 {@code IllegalArgumentException: Invalid atlas id}（26.1.2 Fabric
 * Athena 首次区块网格化崩溃的直接原因）。生产代码已于 2026-08-09 13:21 改为
 * {@code AtlasIds.BLOCKS}，本测试作为该回归的绿灯锁定。
 *
 * <p>English: RED static source contract -- when the Fabric Athena runtime captures block
 * quads, {@link FabricAthenaConnectedBlockStateModel} {@code emitQuads} must call
 * {@code AtlasManager.getAtlasOrThrow} with the registered block atlas id
 * {@code AtlasIds.BLOCKS}; the atlas texture path {@code TextureAtlas.LOCATION_BLOCKS}
 * (minecraft:textures/atlas/blocks.png) must never be passed as an atlas id, or runtime
 * throws {@code IllegalArgumentException: Invalid atlas id} (the direct cause of the 26.1.2
 * Fabric Athena first-chunk-meshing crash). Production now uses {@code AtlasIds.BLOCKS}
 * (2026-08-09 13:21), and this test stays green as the regression lock.
 */
class FabricAthenaBlockAtlasIdContractTest {
    @Test
    void blockAtlasLookupRegistersBlockAtlasId() {
        // 中文：压缩空白后再断言，避免合法换行破坏“getAtlasOrThrow 必须使用图集 ID”的接线契约。
        // English: Whitespace is compacted before asserting so legal line wrapping never
        // breaks the wiring contract that getAtlasOrThrow must use the atlas id.
        String source = productionModelSource()
                .replaceAll("\\s+", "");

        // 中文：AtlasManager 按注册 ID 查找图集，必须传 AtlasIds.BLOCKS，而不是纹理文件路径。
        // English: AtlasManager looks atlases up by registered id, so AtlasIds.BLOCKS must be
        // passed, not the atlas texture path.
        assertTrue(
                source.contains(
                        "getAtlasOrThrow(AtlasIds.BLOCKS)"),
                "AtlasManager.getAtlasOrThrow must be registered with the block atlas id "
                        + "AtlasIds.BLOCKS; an atlas texture path is not a registered atlas id");
    }

    @Test
    void blockAtlasLookupNeverUsesAtlasTexturePath() {
        String source = productionModelSource()
                .replaceAll("\\s+", "");

        // 中文：纹理路径是图集文件的位置，不是 AtlasManager 注册表中的图集 ID；传它必然
        // IllegalArgumentException: Invalid atlas id: minecraft:textures/atlas/blocks.png。
        // English: The texture path is the atlas file location, not an AtlasManager-registered
        // atlas id; passing it always yields
        // IllegalArgumentException: Invalid atlas id: minecraft:textures/atlas/blocks.png.
        assertFalse(
                source.contains(
                        "TextureAtlas.LOCATION_BLOCKS"),
                "AtlasManager registers atlas ids, not atlas texture paths; "
                        + "TextureAtlas.LOCATION_BLOCKS "
                        + "(minecraft:textures/atlas/blocks.png) must not be passed to "
                        + "getAtlasOrThrow");
    }

    /**
     * 中文：读取 Fabric 生产模型的当前源码（只读静态证据）；按测试工作目录依次尝试模块、
     * 工程与聚合仓库三种根位置，未找到时以明确断言失败。
     *
     * <p>English: Reads the current source of the Fabric production model (read-only static
     * evidence), trying the module, project, and aggregate-repository root positions in
     * order from the test working directory, and fails explicitly when absent.
     */
    private static String productionModelSource() {
        String relative =
                "com/kltyton/autoseamblend/fabric/compat/athena/runtime/"
                        + "FabricAthenaConnectedBlockStateModel.java";
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
                "FabricAthenaConnectedBlockStateModel source not found from "
                        + Path.of("").toAbsolutePath());
    }
}
