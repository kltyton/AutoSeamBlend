package com.kltyton.autoseamblend.reload.rule.evidence;

import com.google.gson.JsonObject;
import com.kltyton.autoseamblend.compat.athena.evidence.AthenaSlotEvidenceResolver;
import com.kltyton.autoseamblend.compat.fusion.authoring.materialize.FusionNativeEvidenceLayout;
import com.kltyton.autoseamblend.compat.fusion.evidence.FusionSlotEvidenceResolver;
import com.kltyton.autoseamblend.compat.fusion.evidence.FusionSlotEvidenceResolver.FusionSheetLayout;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import com.kltyton.autoseamblend.engine.ownership.evidence.MinecraftNativeResourceSource;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 中文：把资源、图片与 Fusion 外部布局观察适配到公共槽位证据语义；CTM Mod 槽位证据由
 * Loader 注册的扩展提供。
 *
 * English: Adapts resources, images, and the external Fusion layout observation to the common
 * slot-evidence semantics. CTM Mod slot evidence comes from a Loader-registered extension.
 */
public final class NativeSlotEvidenceResolver {
    private static volatile CtmModEvidence ctmModEvidence;

    private NativeSlotEvidenceResolver() {}

    /**
     * 中文：注册 CTM Mod 家族的槽位证据扩展（仅 NeoForge/Forge 目标存在）。
     *
     * English: Registers the CTM Mod family slot-evidence extension, which exists
     * only on NeoForge/Forge targets.
     */
    public static void registerCtmMod(CtmModEvidence evidence) {
        ctmModEvidence = Objects.requireNonNull(evidence, "evidence");
    }

    public static List<NativeSlot> ctmMod(
            Identifier documentId,
            ConnectionMethod method,
            ResourceManager resources) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(resources, "resources");
        CtmModEvidence evidence = ctmModEvidence;
        if (evidence == null) {
            throw new IllegalStateException(
                    "CTM_MOD_EVIDENCE_EXTENSION_UNAVAILABLE");
        }
        return evidence.resolve(
                documentId,
                method,
                resources);
    }

    public static List<NativeSlot> athena(
            Identifier documentId,
            JsonObject root,
            ConnectionMethod method,
            ResourceManager resources) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(resources, "resources");
        return AthenaSlotEvidenceResolver.resolve(
                documentId.toString(),
                root,
                method,
                new MinecraftNativeResourceSource(resources));
    }

    public static List<NativeSlot> fusion(
            Identifier documentId,
            JsonObject root,
            ConnectionMethod method,
            ResourceManager resources) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(resources, "resources");
        return FusionSlotEvidenceResolver.resolve(
                documentId.toString(),
                root,
                method,
                new MinecraftNativeResourceSource(resources),
                (resolvedMethod, declaredLayout) ->
                        FusionNativeEvidenceLayout.resolve(
                                        resolvedMethod,
                                        declaredLayout)
                                .map(layout -> new FusionSheetLayout(
                                        layout.columns(),
                                        layout.rows(),
                                        layout.logicalCells().size())));
    }

    /**
     * 中文：CTM Mod 家族槽位证据的 Loader 注入契约。
     *
     * English: Loader-injected slot-evidence contract for the CTM Mod family.
     */
    @FunctionalInterface
    public interface CtmModEvidence {
        List<NativeSlot> resolve(
                Identifier documentId,
                ConnectionMethod method,
                ResourceManager resources);
    }
}
