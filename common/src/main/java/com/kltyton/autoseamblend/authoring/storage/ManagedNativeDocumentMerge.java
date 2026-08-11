package com.kltyton.autoseamblend.authoring.storage;

import com.kltyton.autoseamblend.authoring.document.NativeDocumentMerge;
import com.kltyton.autoseamblend.authoring.document.NativeDocumentOperations;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringFile;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringProject;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.texture.budget.TextureInputBudget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 中文：收敛 Managed 原生文档的路径解析、有界读取和无损合并。
 *
 * English: Centralizes Managed native-document path resolution, bounded reads,
 * and lossless merging.
 *
 * <p>The implementation uses only Java NIO and common authoring types. Loader
 * code supplies a frozen {@link ManagedPackWriteLayout}; no Minecraft object or
 * reload API crosses this boundary.</p>
 */
public final class ManagedNativeDocumentMerge {
    private ManagedNativeDocumentMerge() {}

    /**
     * 中文：把一个项目合并到现有事务，保留同一保存批次内的文档顺序。
     *
     * English: Merge one project into an existing transaction while preserving
     * document order within the save batch.
     */
    public static void mergeInto(
            ManagedPackWriteLayout layout,
            ManagedAuthoringProject project,
            Map<String, byte[]> transaction)
            throws IOException {
        mergeInto(layout, project, transaction, NativeDocumentOperations.shared());
    }

    public static void mergeInto(
            ManagedPackWriteLayout layout,
            ManagedAuthoringProject project,
            Map<String, byte[]> transaction,
            NativeDocumentOperations operations)
            throws IOException {
        ManagedPackWriteLayout checkedLayout = Objects.requireNonNull(layout, "layout");
        ManagedAuthoringProject checkedProject = Objects.requireNonNull(project, "project");
        Map<String, byte[]> checkedTransaction = Objects.requireNonNull(transaction, "transaction");
        NativeDocumentOperations resolvedOperations = Objects.requireNonNull(
                operations,
                "operations");
        ManagedPathPolicy.rejectWorkspaceRoot(
                checkedLayout.resourcePacksRoot(),
                checkedLayout.root());
        for (ManagedAuthoringFile document : checkedProject.documents()) {
            String relative = ManagedPathPolicy.validateRelative(document.relativePath());
            Path existing = ManagedPathPolicy.resolveContained(
                    checkedLayout.resourcePacksRoot(),
                    checkedLayout.root(),
                    relative);
            byte[] desired = document.content();
            byte[] pending = checkedTransaction.get(relative);
            byte[] resolved = pending != null
                    ? resolvedOperations.mergeSource(
                            checkedProject.family(), relative, pending, desired)
                    : Files.isRegularFile(existing, LinkOption.NOFOLLOW_LINKS)
                            ? resolvedOperations.mergeSource(
                                    checkedProject.family(),
                                    relative,
                                    readExisting(existing, relative),
                                    desired)
                            : desired.clone();
            checkedTransaction.put(relative, resolved);
        }
    }

    /**
     * 中文：创建一个独立的、按项目顺序聚合的文档事务。
     *
     * English: Build an independent document transaction aggregated in project
     * order.
     */
    public static Map<String, byte[]> merge(
            ManagedPackWriteLayout layout,
            ManagedAuthoringProject project)
            throws IOException {
        LinkedHashMap<String, byte[]> merged = new LinkedHashMap<>();
        mergeInto(layout, project, merged);
        return merged;
    }

    /**
     * 中文：读取既有文档时按资源后缀施加有界输入预算。
     *
     * English: Apply the bounded input budget by resource suffix when reading
     * an existing native document.
     */
    private static byte[] readExisting(Path existing, String relativePath)
            throws IOException {
        return TextureInputBudget.DEFAULT.read(
                existing,
                inputKind(relativePath),
                "managed-existing:" + relativePath);
    }

    /**
     * 中文：PNG、pack 元数据和其他原生文档使用各自固定上限。
     *
     * English: PNGs, pack metadata, and other native documents use their fixed
     * limits.
     */
    private static TextureInputBudget.InputKind inputKind(String relativePath) {
        String normalized = relativePath.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".png")) {
            return TextureInputBudget.InputKind.PNG;
        }
        if (normalized.endsWith(".mcmeta")) {
            return TextureInputBudget.InputKind.METADATA;
        }
        return TextureInputBudget.InputKind.NATIVE_DOCUMENT;
    }

    /**
     * 中文：保留给需要直接合并字节的公共调用点。
     *
     * English: Shared byte-level entry point for callers that already have the
     * existing document bytes.
     */
    public static byte[] mergeSource(
            EngineFamily family,
            String path,
            byte[] existing,
            byte[] desired)
            throws IOException {
        return NativeDocumentMerge.mergeSource(family, path, existing, desired);
    }
}
