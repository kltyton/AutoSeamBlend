package com.kltyton.autoseamblend.fabric.compat.fusion.runtime;

import com.kltyton.autoseamblend.engine.query.NativeDocumentIdentity;
import com.kltyton.autoseamblend.compat.fusion.runtime.FusionModifierDocumentLocation;
import com.kltyton.autoseamblend.mixin.fusion.BlockModelModifierPropertiesAccessor;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：只暂存 Fusion 1.3.12 原生 parser 已接受的方块状态与顶层文档身份。
 *
 * English: Stages only block-state/top-level-document identities accepted by
 * Fusion 1.3.12's native parser.
 */
public final class FusionAcceptedModifierDocumentCatalog {
    private FusionAcceptedModifierDocumentCatalog() {}

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
                .FusionAcceptedModifierDocumentCatalog
                .beginReload(documents);
    }

    public static synchronized void publishAccepted(
            Map<BlockState, ? extends List<?>>
                    modifiers) {
        LinkedHashMap<BlockState, List<NativeDocumentIdentity>>
                accepted = new LinkedHashMap<>();
        Objects.requireNonNull(modifiers, "modifiers")
                .forEach((state, properties) -> {
                    LinkedHashSet<NativeDocumentIdentity>
                            documents =
                                    new LinkedHashSet<>();
                    for (Object property
                            : properties) {
                        Identifier location =
                                ((BlockModelModifierPropertiesAccessor)
                                                property)
                                        .autoseamblend$location();
                        // 中文：stripped 模型 ID 必须归一化为完整文件 ID 才能命中 accepted 文档键。
                        // English: The stripped model ID must be normalized to the full file ID to hit accepted-document keys.
                        Identifier resourceId =
                                FusionModifierDocumentLocation
                                        .resourceId(location);
                        com.kltyton.autoseamblend.compat
                                .fusion.runtime
                                .FusionAcceptedModifierDocumentCatalog
                                .reloadingDocument(resourceId)
                                .ifPresent(documents::add);
                    }
                    if (!documents.isEmpty()) {
                        accepted.put(
                                Objects.requireNonNull(
                                        state,
                                        "state"),
                                List.copyOf(documents));
                    }
                });
        com.kltyton.autoseamblend.compat.fusion.runtime
                .FusionAcceptedModifierDocumentCatalog
                .publishAccepted(accepted);
    }

    public static java.util.Optional<
                    com.kltyton.autoseamblend.compat.fusion.runtime
                            .FusionAcceptedModifierDocumentCatalog.Snapshot>
            staged() {
        return com.kltyton.autoseamblend.compat.fusion.runtime
                .FusionAcceptedModifierDocumentCatalog.staged();
    }

    public static void abortStaged() {
        com.kltyton.autoseamblend.compat.fusion.runtime
                .FusionAcceptedModifierDocumentCatalog
                .abortStaged();
    }
}
