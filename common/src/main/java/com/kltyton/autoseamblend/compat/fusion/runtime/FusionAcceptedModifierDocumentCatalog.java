package com.kltyton.autoseamblend.compat.fusion.runtime;

import com.kltyton.autoseamblend.engine.query.NativeDocumentIdentity;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：集中管理 Fusion 原生 parser 已接受文档的重载暂存、快照发布和清理，不读取 Loader
 * 私有 modifier/accessor 类型。
 *
 * English: Centralizes reload staging, snapshot publication, and cleanup for Fusion documents
 * accepted by the native parser without depending on Loader-private modifier/accessor types.
 */
public final class FusionAcceptedModifierDocumentCatalog {
    private static final Map<Long, Map<Identifier, NativeDocumentIdentity>>
            reloadingDocuments = new LinkedHashMap<>();
    private static final Map<Long, Snapshot> stagedAccepted = new LinkedHashMap<>();

    private FusionAcceptedModifierDocumentCatalog() {}

    /**
     * 中文：由 Loader 适配器提供原生资源胜者及其来源身份。
     * English: Receives winning native resources and their source identities from a Loader adapter.
     */
    public static synchronized void beginReload(
            Map<Identifier, NativeDocumentIdentity> documents) {
        beginReload(0L, documents);
    }

    /**
     * 中文：Fabric 可同时暂存多个 reload token；scope 保持每个 token 的资源胜者互不覆盖。
     * English: Fabric may stage multiple reload tokens, so each scope keeps its resource winners
     * isolated from the others.
     */
    public static synchronized void beginReload(
            long scope,
            Map<Identifier, NativeDocumentIdentity> documents) {
        reloadingDocuments.put(scope, immutableDocuments(documents));
        stagedAccepted.remove(scope);
    }

    /**
     * 中文：按 Fusion parser 实际提供的 location 结果发布状态到文档身份的不可变映射。
     * English: Publishes an immutable state-to-document mapping from the locations actually
     * accepted by Fusion's parser.
     */
    public static synchronized Snapshot publishAccepted(
            Map<BlockState, ? extends List<NativeDocumentIdentity>> accepted) {
        return publishAccepted(0L, accepted);
    }

    public static synchronized Snapshot publishAccepted(
            long scope,
            Map<BlockState, ? extends List<NativeDocumentIdentity>> accepted) {
        LinkedHashMap<BlockState, List<NativeDocumentIdentity>> copy = new LinkedHashMap<>();
        Map<Identifier, NativeDocumentIdentity> currentDocuments =
                reloadingDocuments.getOrDefault(scope, Map.of());
        Objects.requireNonNull(accepted, "accepted").forEach((state, identities) -> {
            LinkedHashSet<NativeDocumentIdentity> unique = new LinkedHashSet<>();
            for (NativeDocumentIdentity identity : Objects.requireNonNull(identities, "identities")) {
                if (!currentDocuments.isEmpty() && !currentDocuments.containsValue(identity)) {
                    continue;
                }
                unique.add(Objects.requireNonNull(identity, "identity"));
            }
            if (!unique.isEmpty()) {
                copy.put(Objects.requireNonNull(state, "state"), List.copyOf(unique));
            }
        });
        Snapshot snapshot = new Snapshot(0, copy);
        stagedAccepted.put(scope, snapshot);
        reloadingDocuments.remove(scope);
        return snapshot;
    }

    public static synchronized Optional<Snapshot> staged() {
        return staged(0L);
    }

    public static synchronized Optional<Snapshot> staged(long scope) {
        return Optional.ofNullable(stagedAccepted.get(scope));
    }

    /**
     * 中文：在发布前按资源 ID 取得当前重载胜者，供 Loader parser 适配器完成 native
     * location 到统一身份的转换。
     *
     * English: Resolves the current reload winner by resource ID so a Loader parser adapter can
     * convert its native location into the shared identity before publication.
     */
    public static synchronized Optional<NativeDocumentIdentity> reloadingDocument(
            Identifier resourceId) {
        return reloadingDocument(0L, resourceId);
    }

    public static synchronized Optional<NativeDocumentIdentity> reloadingDocument(
            long scope,
            Identifier resourceId) {
        return Optional.ofNullable(
                reloadingDocuments
                        .getOrDefault(scope, Map.of())
                        .get(Objects.requireNonNull(resourceId, "resourceId")));
    }

    public static synchronized void abortStaged() {
        abortStaged(0L);
    }

    public static synchronized void abortStaged(long scope) {
        stagedAccepted.remove(scope);
        reloadingDocuments.remove(scope);
    }

    public static synchronized void purgeUnselected() {
        stagedAccepted.clear();
        reloadingDocuments.clear();
    }

    private static Map<Identifier, NativeDocumentIdentity> immutableDocuments(
            Map<Identifier, NativeDocumentIdentity> documents) {
        LinkedHashMap<Identifier, NativeDocumentIdentity> copy = new LinkedHashMap<>();
        Objects.requireNonNull(documents, "documents").forEach((resourceId, identity) ->
                copy.put(
                        Objects.requireNonNull(resourceId, "resourceId"),
                        Objects.requireNonNull(identity, "identity")));
        return Collections.unmodifiableMap(copy);
    }

    /** 中文：不可变的已接受文档代次。 / English: Immutable accepted-document generation. */
    public record Snapshot(
            long generation,
            Map<BlockState, List<NativeDocumentIdentity>> documents) {
        public Snapshot {
            if (generation < 0) {
                throw new IllegalArgumentException("document generation must be non-negative");
            }
            LinkedHashMap<BlockState, List<NativeDocumentIdentity>> copy = new LinkedHashMap<>();
            Objects.requireNonNull(documents, "documents").forEach((state, identities) ->
                    copy.put(
                            Objects.requireNonNull(state, "state"),
                            List.copyOf(Objects.requireNonNull(identities, "identities"))));
            documents = Collections.unmodifiableMap(copy);
        }

        public static Snapshot empty() {
            return new Snapshot(0, Map.of());
        }

        public List<NativeDocumentIdentity> documents(BlockState state) {
            return documents.getOrDefault(Objects.requireNonNull(state, "state"), List.of());
        }
    }
}
