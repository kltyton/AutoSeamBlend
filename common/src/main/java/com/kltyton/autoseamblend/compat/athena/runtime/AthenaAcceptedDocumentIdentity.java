package com.kltyton.autoseamblend.compat.athena.runtime;

import com.google.gson.JsonObject;
import com.kltyton.autoseamblend.engine.query.NativeDocumentIdentity;
import earth.terrarium.athena.impl.loading.AthenaResourceLoader;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import net.minecraft.resources.Identifier;

/**
 * 中文：把 Athena 4.7.3 已接受的精确原生文档解析为文档身份，由 1.21.1 a71485e 已验收
 * common 逻辑移植并适配 26.1.2 Identifier API。锁定 4.7.3 键空间：公开静态
 * {@code AthenaResourceLoader.getData(Identifier, Identifier)} 以 (loaderId, blockId)
 * 查询 loader 表，loaderId 由调用方按 4.7.3 键空间提供（如 athena:ctm、athena:pane_ctm）；
 * 按 loaderIds 顺序求值，首个非 null 命中即把 blockId 归一为
 * {@code namespace:blockstates/<path>.json} 文档身份（26.1.2 已验收的 blockstates 文档
 * 身份形式）。本类不反射、不 mixin、不捕获 Throwable。
 *
 * <p>English: Resolves Athena 4.7.3's accepted exact native document into a document
 * identity, ported from the accepted 1.21.1 a71485e common logic and adapted to the 26.1.2
 * Identifier API. Locks the 4.7.3 key space: the public static
 * {@code AthenaResourceLoader.getData(Identifier, Identifier)} queries the loader table with
 * (loaderId, blockId); loaderIds are caller-supplied in the 4.7.3 key space (for example
 * athena:ctm, athena:pane_ctm). Loader ids are evaluated in order and the first non-null hit
 * normalizes blockId into the {@code namespace:blockstates/<path>.json} document identity,
 * the 26.1.2-accepted blockstates identity form. This class uses no reflection, no mixins,
 * and never catches Throwable.
 */
public final class AthenaAcceptedDocumentIdentity {
    private AthenaAcceptedDocumentIdentity() {}

    /**
     * 中文：按 4.7.3 原生 loader 表解析 blockId 的精确文档身份；无命中返回 empty。
     *
     * <p>English: Resolves the exact document identity of blockId through the 4.7.3 native
     * loader table; returns empty when nothing is accepted.
     */
    public static Optional<NativeDocumentIdentity> resolve(
            Identifier blockId,
            List<Identifier> loaderIds) {
        return resolve(
                blockId,
                loaderIds,
                AthenaResourceLoader::getData);
    }

    /**
     * 中文：可注入查找器的 package-private 重载，仅供同包合同测试使用；生产路径委托
     * 给公开静态 {@code AthenaResourceLoader.getData}。查找器按 (loaderId, blockId)
     * 返回原生已接受文档或 null。
     *
     * <p>English: Package-private overload with an injectable lookup, used only by the
     * same-package contract tests; the production path delegates to the public static
     * {@code AthenaResourceLoader.getData}. The lookup returns the accepted native document
     * or null for (loaderId, blockId).
     */
    static Optional<NativeDocumentIdentity> resolve(
            Identifier blockId,
            List<Identifier> loaderIds,
            BiFunction<
                            Identifier,
                            Identifier,
                            JsonObject>
                    lookup) {
        Objects.requireNonNull(blockId, "blockId");
        Objects.requireNonNull(loaderIds, "loaderIds");
        Objects.requireNonNull(lookup, "lookup");
        for (Identifier loaderId : loaderIds) {
            Objects.requireNonNull(loaderId, "loaderId");
            if (lookup.apply(loaderId, blockId) != null) {
                return Optional.of(
                        NativeDocumentIdentity.resourceOnly(
                                blockId.getNamespace()
                                        + ":blockstates/"
                                        + blockId.getPath()
                                        + ".json"));
            }
        }
        return Optional.empty();
    }
}
