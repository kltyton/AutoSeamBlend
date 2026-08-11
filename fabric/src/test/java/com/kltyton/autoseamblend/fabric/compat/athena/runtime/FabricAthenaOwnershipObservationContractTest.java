package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.engine.query.AcceptedNativeDocument;
import com.kltyton.autoseamblend.engine.query.NativeDocumentIdentity;
import com.kltyton.autoseamblend.engine.query.NativeQueryObservation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 中文：Athena Fabric 所有权观察契约。前半部分仅使用 common API，可在任意引擎测试配置
 * 下运行，锁定 26.1.2/NeoForge 已修复语义的 API 边界：exact(empty) 构造被 API 本身拒绝
 * （旧 FabricAthenaNativeModelOwnershipProvider 恰好调用 exact(List.of())，运行时必然抛
 * IllegalArgumentException），因此修复后的 provider 只能发布带至少一个文档的 exact、
 * 明确的 unknown 或 noMatch。后半部分是静态源码契约：Fabric 生产 provider 必须实际引用
 * common AthenaNativeOwnershipPolicy（missingSprite、ownsByCandidateSprites、
 * resolveObservation）与 AthenaAcceptedDocumentIdentity.resolve，Loader 内只保留
 * AthenaBakedModel/material/FactoryManager loaderIds/blockId 快照与 Fabric/Mixin API 映射，
 * 且不得再直接构造 exact（尤其 exact(empty)）。
 *
 * <p>English: Athena Fabric ownership-observation contract. The first half uses common API
 * only and runs under any engine test configuration, locking the API boundary of the
 * 26.1.2/NeoForge-fixed semantics: exact(empty) is rejected by the API itself (the old
 * FabricAthenaNativeModelOwnershipProvider called exact(List.of()) and therefore always
 * threw IllegalArgumentException at runtime), so the fixed provider can only publish exact
 * with at least one document, an explicit unknown, or noMatch. The second half is a static
 * source contract: the Fabric production provider must actually reference common
 * AthenaNativeOwnershipPolicy (missingSprite, ownsByCandidateSprites, resolveObservation)
 * and AthenaAcceptedDocumentIdentity.resolve, keep only the AthenaBakedModel/material/
 * FactoryManager loaderIds/blockId snapshots plus the Fabric/Mixin API mapping in the
 * Loader, and never construct exact directly (especially exact(empty)).
 */
class FabricAthenaOwnershipObservationContractTest {
    @Test
    void exactRejectsEmptyDocumentList() {
        assertThrows(
                IllegalArgumentException.class,
                () -> NativeQueryObservation.exact(
                        List.of()),
                "exact(empty) fake ownership must be impossible at the API level");
    }

    @Test
    void exactAcceptsIdentityOnlyDocument() {
        NativeQueryObservation observed =
                NativeQueryObservation.exact(
                        List.of(
                                AcceptedNativeDocument.identityOnly(
                                        NativeDocumentIdentity.resourceOnly(
                                                "minecraft:blockstates/glass.json"))));
        assertEquals(
                1,
                observed.acceptedDocuments().size(),
                "one identity-only document must be published");
        assertTrue(
                observed.unknownDiagnostic().isEmpty(),
                "exact must not carry an unknown diagnostic");
    }

    @Test
    void noMatchAndUnknownShapesAreDistinct() {
        NativeQueryObservation noMatch =
                NativeQueryObservation.noMatch();
        assertTrue(
                noMatch.acceptedDocuments().isEmpty(),
                "noMatch must carry no documents");
        assertTrue(
                noMatch.unknownDiagnostic().isEmpty(),
                "noMatch must carry no unknown diagnostic");

        NativeQueryObservation unknown =
                NativeQueryObservation.unknown(
                        "ATHENA_ACCEPTED_MODEL_DOCUMENT_IDENTITY_UNAVAILABLE");
        assertTrue(
                unknown.acceptedDocuments().isEmpty(),
                "unknown must carry no documents");
        assertEquals(
                "ATHENA_ACCEPTED_MODEL_DOCUMENT_IDENTITY_UNAVAILABLE",
                unknown.unknownDiagnostic().orElseThrow(),
                "unknown must name the diagnostic explicitly");
    }

    @Test
    void productionOwnershipDelegatesGenericAdjudicationToCommon() {
        // 中文：压缩空白后再断言，避免合法换行破坏“必须引用 common”的接线契约。
        // English: Whitespace is compacted before asserting so legal line wrapping never
        // breaks the wiring contract that common must be referenced.
        String source = productionOwnershipSource()
                .replaceAll("\\s+", "");

        // 中文：缺失精灵判定、候选同名 owns 与观察结果裁决必须委托 common 策略类。
        // English: Missing-sprite detection, same-name candidate ownership, and the
        // observation verdict must delegate to the common policy class.
        assertTrue(
                source.contains(
                        "AthenaNativeOwnershipPolicy.missingSprite"),
                "missing-sprite adjudication must call common");
        assertTrue(
                source.contains(
                        "AthenaNativeOwnershipPolicy.ownsByCandidateSprites"),
                "same-name candidate ownership must call common");
        assertTrue(
                source.contains(
                        "AthenaNativeOwnershipPolicy.resolveObservation"),
                "the observation verdict must call common");

        // 中文：精确文档身份解析必须委托 common，并由捕获时的 blockId/loaderIds 快照喂入。
        // English: Exact document-identity resolution must delegate to common, fed by the
        // blockId/loaderIds snapshots taken at capture time.
        assertTrue(
                source.contains(
                        "AthenaAcceptedDocumentIdentity.resolve"),
                "exact document identity must call common");
        assertTrue(
                source.contains("loaderIds()"),
                "the Loader keeps the Athena 3.1.2 loaderIds key-space snapshot");
        assertTrue(
                source.contains("blockId(state)"),
                "the blockId snapshot must feed common identity resolution");
        assertTrue(
                source.contains("loaderIds())"),
                "the loaderIds snapshot must feed common identity resolution");

        // 中文：Loader 侧保留 AthenaBakedModel/material 快照与 Fabric Mixin 访问器映射。
        // English: The Loader keeps the AthenaBakedModel/material snapshots and the Fabric
        // mixin accessor mapping.
        assertTrue(
                source.contains(
                        "autoseamblend$getModel()"),
                "the AthenaBakedModel snapshot must stay in the Loader");
        assertTrue(
                source.contains(
                        "autoseamblend$getTextures()"),
                "the texture snapshot must stay in the Loader");
    }

    @Test
    void productionOwnershipNeverConstructsExactInLoader() {
        String source = productionOwnershipSource()
                .replaceAll("\\s+", "");

        // 中文：exact 构造（含 exact(empty)）必须彻底离开 Loader，只允许 common
        // resolveObservation 在身份可解析时发布。
        // English: exact construction (including exact(empty)) must leave the Loader
        // entirely; only common resolveObservation may publish exact when identity resolves.
        assertFalse(
                source.contains(
                        "NativeQueryObservation.exact("),
                "exact construction must leave the Loader "
                        + "(exact(empty) must be impossible)");
        assertFalse(
                source.contains("AcceptedNativeDocument"),
                "document publication must be delegated to common");
        assertFalse(
                source.contains("MissingTextureAtlasSprite"),
                "the missing-sprite check must be delegated to common");
        assertFalse(
                source.contains(
                        "AthenaResourceLoaderAccessor"),
                "loader data-table identity probing must be delegated to common");
        assertFalse(
                source.contains("acceptedIdentity("),
                "the duplicated Loader identity resolver must be removed");
    }

    /**
     * 中文：读取 Fabric 生产 ownership provider 的当前源码（只读静态证据）；按测试工作
     * 目录依次尝试模块/工程/聚合仓库三种根位置，未找到时以明确断言失败。
     *
     * <p>English: Reads the current source of the Fabric production ownership provider
     * (read-only static evidence), trying the module, project, and aggregate-repository root
     * positions in order from the test working directory, and fails explicitly when absent.
     */
    private static String productionOwnershipSource() {
        String relative =
                "com/kltyton/autoseamblend/fabric/compat/athena/runtime/"
                        + "FabricAthenaNativeModelOwnershipProvider.java";
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
                "FabricAthenaNativeModelOwnershipProvider source not found from "
                        + Path.of("").toAbsolutePath());
    }
}
