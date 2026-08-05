package com.kltyton.autoseamblend.compat.ctm_mod.evidence;

import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import com.kltyton.autoseamblend.engine.ownership.evidence.MinecraftNativeResourceSource;
import com.kltyton.autoseamblend.engine.ownership.evidence.NativeResourceSource;
import com.kltyton.autoseamblend.engine.ownership.evidence.NativeSlotEvidence;
import com.kltyton.autoseamblend.engine.ownership.evidence.NativeSlotEvidence.Observation;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.contract.CtmModNativeDocument;
import com.kltyton.autoseamblend.reload.rule.evidence.NativeSlotEvidenceResolver;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 中文：把 NeoForge 独占的 CTM Mod 载体声明归一化为公共逐槽证据。
 *
 * English: Normalizes NeoForge-only CTM Mod carrier declarations into common per-slot evidence.
 */
public enum CtmModEvidenceExtension
        implements NativeSlotEvidenceResolver.CtmModEvidence {
    INSTANCE;

    @Override
    public List<NativeSlot> resolve(
            Identifier documentId,
            ConnectionMethod method,
            ResourceManager resources) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(resources, "resources");
        String path = documentId.getPath();
        if (!path.startsWith("blockstates/")
                || !path.endsWith(".json")) {
            return NativeSlotEvidence.unknown(
                    method,
                    NativeSlotEvidence.FULL_CTM_SLOTS);
        }
        NativeResourceSource source =
                new MinecraftNativeResourceSource(resources);
        List<Observation> evidence = CtmModNativeDocument
                .read(documentId, resources)
                .stream()
                .flatMap(model -> model.carriers().stream())
                .map(carrier -> CtmModSlotEvidenceResolver.observe(
                        carrier.spec().role(),
                        carrier.declared(),
                        carrier.textureId().map(Identifier::toString),
                        carrier.spec().columns(),
                        carrier.spec().rows(),
                        source))
                .toList();
        return CtmModSlotEvidenceResolver.resolve(
                method,
                evidence);
    }
}
