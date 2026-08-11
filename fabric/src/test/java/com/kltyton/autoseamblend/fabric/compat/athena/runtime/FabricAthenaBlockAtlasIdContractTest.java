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
 * 中文：RED 静态源码契约——1.20.1 Fabric Athena 运行时捕获方块 quad 时，
 * {@link FabricAthenaConnectedBlockStateModel} 必须用 {@code SpriteFinder.get} 绑定到
 * {@code Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)} 解析方块图集
 * 精灵（1.20.1 中图集以 LOCATION_BLOCKS 注册）；禁止使用 26.1.2 的
 * {@code AtlasManager.getAtlasOrThrow}/{@code AtlasIds.BLOCKS}（1.20.1 不存在）。
 *
 * <p>English: RED static source contract -- on 1.20.1 the Fabric Athena runtime block-quad
 * capture must resolve the block atlas through {@code SpriteFinder.get} bound to
 * {@code Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)} (the atlas
 * is registered under LOCATION_BLOCKS on 1.20.1); the 26.1.2-only
 * {@code AtlasManager.getAtlasOrThrow}/{@code AtlasIds.BLOCKS} must not appear.
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
        // English: 1.20.1 registers the block atlas under TextureAtlas.LOCATION_BLOCKS and
        // resolves sprites via SpriteFinder.get(getTextureAtlas(LOCATION_BLOCKS)).
        assertTrue(
                source.contains(
                        "SpriteFinder.get(")
                        && source.contains(
                                "getAtlas(TextureAtlas.LOCATION_BLOCKS)"),
                "SpriteFinder must be bound to the registered block atlas "
                        + "getAtlas(TextureAtlas.LOCATION_BLOCKS) on 1.20.1");
    }

    @Test
    void blockAtlasLookupNeverUsesAtlasTexturePath() {
        String source = productionModelSource()
                .replaceAll("\\s+", "");

        // 中文：26.1.2 的 AtlasManager/AtlasIds API 在 1.20.1 不存在，禁止残留。
        // English: the 26.1.2 AtlasManager/AtlasIds APIs do not exist on 1.20.1 and must
        // not reappear.
        assertFalse(
                source.contains(
                        "AtlasManager"),
                "the 26.1.2 AtlasManager API must not be referenced on 1.20.1");
        assertFalse(
                source.contains(
                        "AtlasIds"),
                "the 26.1.2 AtlasIds constant must not be referenced on 1.20.1");
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
