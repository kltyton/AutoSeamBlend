package com.kltyton.autoseamblend.fabric.compat.fusion.runtime;

import com.kltyton.autoseamblend.engine.query.NativeDocumentIdentity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.client.resources.model.ModelResourceLocation;

/**
 * 中文：只暂存 Fusion 1.3.12 原生 parser 已接受的方块状态与顶层文档身份。
 *
 * English: Stages only block-state/top-level-document identities accepted by
 * Fusion 1.3.12's native parser.
 */
public final class FusionAcceptedModifierDocumentCatalog {
    private static final FileToIdConverter ID_CONVERTER =
            FileToIdConverter.json(
                    "fusion/model_modifiers/blocks");

    private FusionAcceptedModifierDocumentCatalog() {}

    public static synchronized void beginReload(
            Map<ResourceLocation, Resource> resources) {
        LinkedHashMap<ResourceLocation, NativeDocumentIdentity>
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
            Map<ModelResourceLocation, ? extends List<?>>
                    modifiers) {
        com.kltyton.autoseamblend.compat.fusion.runtime
                .FusionAcceptedModifierDocumentCatalog
                .publishAcceptedModels(0L, modifiers);
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
