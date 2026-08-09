package com.kltyton.autoseamblend.neoforge.compat.fusion.runtime;

import com.kltyton.autoseamblend.mixin.fusion.BlockModelModifierPropertiesAccessor;
import com.kltyton.autoseamblend.compat.fusion.runtime.FusionAcceptedModifierDocumentCatalog.Snapshot;
import com.kltyton.autoseamblend.compat.fusion.runtime.FusionModifierDocumentLocation;
import com.kltyton.autoseamblend.engine.query.NativeDocumentIdentity;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：只暂存 Fusion 1.3.12 原生 parser 已接受的方块状态与顶层文档身份。
 *
 * English:
 * Stages only block-state/top-level-document identities accepted by Fusion 1.3.12's native
 * parser.
 */
public final class FusionAcceptedModifierDocumentCatalog {
    private FusionAcceptedModifierDocumentCatalog() {}

    /**
     * 中文：记录原生 listMatchingResources 选中的顶层文件及来源资源包。
     *
     * English: Records the winning top-level files and source packs selected by native resource
     * listing.
     */
    public static synchronized void beginReload(
            Map<Identifier, Resource> resources) {
        LinkedHashMap<Identifier, NativeDocumentIdentity>
                documents = new LinkedHashMap<>();
        Objects.requireNonNull(resources, "resources")
                .forEach((resourceId, resource) ->
                        documents.put(
                                Objects.requireNonNull(
                                        resourceId,
                                        "resourceId"),
                                new NativeDocumentIdentity(
                                        java.util.Optional.of(
                                                Objects.requireNonNull(
                                                                resource,
                                                                "resource")
                                                        .sourcePackId()),
                                        resourceId.toString())));
        com.kltyton.autoseamblend.compat.fusion.runtime
                .FusionAcceptedModifierDocumentCatalog.beginReload(documents);
    }

    /**
     * 中文：在 Fusion 清空 modifiers 前以 Properties.location 与顶层 Resource 交叉，冻结真正接受的文档候选。
     *
     * English: Intersects parsed Properties.location values with top-level Resources before Fusion
     * clears modifiers, then freezes the accepted-document candidate.
     */
    public static synchronized void publishAccepted(
            Map<BlockState, ? extends List<?>> modifiers) {
        LinkedHashMap<BlockState, List<NativeDocumentIdentity>> accepted =
                new LinkedHashMap<>();
        Objects.requireNonNull(modifiers, "modifiers")
                .forEach((state, properties) -> {
                    LinkedHashSet<NativeDocumentIdentity>
                            documents = new LinkedHashSet<>();
                    for (Object property : properties) {
                        Identifier stripped =
                                ((BlockModelModifierPropertiesAccessor)
                                                property)
                                        .autoseamblend$location();
                        Identifier resourceId =
                                FusionModifierDocumentLocation
                                        .resourceId(stripped);
                        // 中文：身份映射由 common catalog 保存，Loader 只读取当前 reload 胜者。
                        // English: The common catalog stores identity mappings; the Loader only reads the current winner.
                        com.kltyton.autoseamblend.compat.fusion.runtime
                                .FusionAcceptedModifierDocumentCatalog.reloadingDocument(resourceId)
                                .ifPresent(documents::add);
                    }
                    if (!documents.isEmpty()) {
                        accepted.put(Objects.requireNonNull(state, "state"), List.copyOf(documents));
                    }
                });
        com.kltyton.autoseamblend.compat.fusion.runtime
                .FusionAcceptedModifierDocumentCatalog.publishAccepted(accepted);
    }

    public static synchronized Optional<Snapshot> staged() {
        return com.kltyton.autoseamblend.compat.fusion.runtime
                .FusionAcceptedModifierDocumentCatalog.staged();
    }

    public static synchronized void abortStaged() {
        com.kltyton.autoseamblend.compat.fusion.runtime
                .FusionAcceptedModifierDocumentCatalog.abortStaged();
    }
}
