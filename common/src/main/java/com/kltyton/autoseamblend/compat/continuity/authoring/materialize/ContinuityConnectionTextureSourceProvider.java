package com.kltyton.autoseamblend.compat.continuity.authoring.materialize;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import com.kltyton.autoseamblend.engine.ownership.NativeSlotIntent;
import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureSet;
import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureDraftContext;
import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureSet.CarrierKind;
import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureSources;
import com.kltyton.autoseamblend.authoring.materialize.TextureSourceSnapshot;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringDraft;
import com.kltyton.autoseamblend.authoring.property.NativePropertyDocumentLoader;
import com.kltyton.autoseamblend.compat.continuity.document.ContinuityNativeDocumentCatalog;
import com.kltyton.autoseamblend.compat.continuity.authoring.materialize.ContinuityMaterializePathPlanner;
import com.kltyton.autoseamblend.compat.continuity.authoring.materialize.ContinuitySlotRecipeDomain;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;

/**
 * 中文：复用 NeoContinuity 原生槽位解析，并把缺失槽位投影为可编辑的 AutoBlend 像素结果。
 *
 * English:
 * Reuses NeoContinuity's native slot parsing and projects missing slots into
 * editable AutoBlend pixel results.
 */
public enum ContinuityConnectionTextureSourceProvider
        implements ConnectionTextureSources.Provider {
    INSTANCE;

    @Override
    public EngineFamily family() {
        return EngineFamily.MCPATCHER;
    }

    @Override
    public ConnectionTextureSet capture(
            Minecraft minecraft,
            ManagedAuthoringDraft draft,
            NativePropertyDocumentLoader document)
            throws IOException {
        ConnectionMethod method =
                draft.resolvedMethod();
        List<Integer> domain =
                ContinuitySlotRecipeDomain.slots(
                        method);
        ConnectionTextureDraftContext.requireSlots(
                method,
                !domain.isEmpty());
        ConnectionTextureDraftContext.DraftInputs inputs =
                ConnectionTextureDraftContext.draftInputs(
                        minecraft,
                        draft);
        TextureSourceSnapshot base =
                inputs.source();
        Map<Integer, NativeSlot> nativeSlots =
                nativeSlots(
                        minecraft,
                        document);
        boolean authoringTemplate =
                ConnectionTextureSources
                        .managedAuthoringTemplate(
                                draft,
                                document);
        ArrayList<ConnectionTextureSet.Slot>
                slots = new ArrayList<>(
                        domain.size());
        for (int slot : domain) {
            NativeSlot evidence =
                    nativeSlots.getOrDefault(
                            slot,
                            new NativeSlot(
                                    slot,
                                    authoringTemplate
                                            ? NativeSlotIntent.OMITTED
                                            : NativeSlotIntent.UNKNOWN,
                                    java.util.Optional.empty()));
            boolean present =
                    evidence.intent()
                            == NativeSlotIntent.PRESENT;
            String outputPath = evidence.spriteId()
                    .filter(ignored -> present
                            || evidence.intent()
                                    == NativeSlotIntent.DECLARED_MISSING)
                    .map(ContinuityMaterializePathPlanner
                            ::nativeTexturePath)
                    .orElseGet(() ->
                            ContinuityMaterializePathPlanner
                                    .generatedTexturePath(
                                            document.documentPath(),
                                            slot));
            TextureSourceSnapshot source =
                    present
                            ? nativeSource(
                                    minecraft,
                                    evidence)
                            : base.transformTo(
                                    ContinuityMaterializePathPlanner
                                            .editorTextureId(
                                                    draft,
                                                    method,
                                                    slot),
                                    ContinuitySlotRecipeDomain
                                            .recipe(
                                                    method,
                                                    slot),
                                    inputs.surface()
                                            .overlayProfile());
            slots.add(new ConnectionTextureSet.Slot(
                    slot,
                    slot,
                    0,
                    CarrierKind.INDEPENDENT_PNG,
                    outputPath,
                    0,
                    0,
                    source.frameWidth(),
                    source.frameHeight(),
                    evidence.intent(),
                    evidence.intent()
                                    == NativeSlotIntent.OMITTED
                            || evidence.intent()
                                    == NativeSlotIntent.DECLARED_MISSING,
                    source));
        }
        return new ConnectionTextureSet(
                family(),
                ContinuityMaterializePathPlanner
                        .tilesExpression(domain),
                slots);
    }

    private static Map<Integer, NativeSlot>
            nativeSlots(
                    Minecraft minecraft,
                    NativePropertyDocumentLoader document) {
        Identifier resourceId =
                ContinuityMaterializePathPlanner
                        .documentResourceId(
                                document.sourceDocumentPath());
        LinkedHashMap<Integer, NativeSlot>
                slots = new LinkedHashMap<>();
        ContinuityNativeDocumentCatalog
                .document(
                        minecraft.getResourceManager(),
                        resourceId)
                .ifPresent(nativeDocument ->
                        nativeDocument.slots()
                                .forEach(slot ->
                                        slots.put(
                                                slot.index(),
                                                slot)));
        return Map.copyOf(slots);
    }

    private static TextureSourceSnapshot
            nativeSource(
                    Minecraft minecraft,
                    NativeSlot slot)
                    throws IOException {
        Identifier spriteId = Identifier.parse(
                slot.spriteId()
                        .orElseThrow());
        TextureAtlasSprite sprite =
                minecraft.getAtlasManager()
                        .getAtlasOrThrow(
                                AtlasIds.BLOCKS)
                        .getSprite(spriteId);
        if (!spriteId.equals(
                sprite.contents().name())) {
            throw new IOException(
                    "NATIVE_SLOT_ATLAS_UNAVAILABLE:"
                            + spriteId);
        }
        return TextureSourceSnapshot.capture(
                sprite.contents(),
                minecraft.getResourceManager());
    }

}
