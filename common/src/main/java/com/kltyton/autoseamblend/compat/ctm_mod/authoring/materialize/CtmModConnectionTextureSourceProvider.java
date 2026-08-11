package com.kltyton.autoseamblend.compat.ctm_mod.authoring.materialize;

import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringRule;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.ownership.NativeSlotIntent;
import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureSet;
import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureDraftContext;
import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureSet.CarrierKind;
import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureSources;
import com.kltyton.autoseamblend.authoring.materialize.TextureSourceSnapshot;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringProjectDrafts;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringDraft;
import com.kltyton.autoseamblend.authoring.property.NativePropertyDocumentLoader;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.contract.CtmModCarrierLayout;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.contract.CtmModCarrierLayout.CarrierSpec;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.contract.CtmModCarrierPaths;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.contract.CtmModCarrierSlotPlan;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.contract.CtmModNativeDocument;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.contract.CtmModNativeDocument.ResolvedCarrier;
import com.kltyton.autoseamblend.compat.ctm_mod.authoring.materialize.CtmModCarrierSynthesis;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.ResourceLocation;

/** 中文：暴露 CTM Lib 的真实载体精灵及其精灵内物理单元。 / English: Exposes CTM Lib's real carrier sprites and their in-sprite physical cells. */
public enum CtmModConnectionTextureSourceProvider
        implements ConnectionTextureSources.Provider {
    INSTANCE;

    @Override
    public EngineFamily family() {
        return EngineFamily.CTM_MOD;
    }

    @Override
    public ConnectionTextureSet capture(
            Minecraft minecraft,
            ManagedAuthoringDraft draft,
            NativePropertyDocumentLoader document)
            throws IOException {
        ConnectionMethod method = draft.resolvedMethod();
        ConnectionTextureDraftContext.requireSlots(
                method,
                method != ConnectionMethod.NONE);
        ConnectionTextureDraftContext.DraftInputs inputs =
                ConnectionTextureDraftContext.draftInputs(
                        minecraft,
                        draft);
        boolean authoringTemplate =
                ConnectionTextureSources
                        .managedAuthoringTemplate(
                                draft,
                                document);
        List<ResolvedCarrier> declared = authoringTemplate
                ? List.of()
                : CtmModNativeDocument.resourceId(
                                document.sourceDocumentPath())
                        .map(id -> CtmModNativeDocument.read(
                                id,
                                document.sourceDocument(),
                                minecraft.getResourceManager()))
                        .stream()
                        .flatMap(List::stream)
                        .flatMap(model -> model.carriers().stream())
                        .filter(carrier -> carrier.declared()
                                || carrier.textureId().isPresent())
                        .toList();
        if (declared.isEmpty()) {
            declared = CtmModCarrierLayout
                    .forMethod(method)
                    .carriers()
                    .stream()
                    .map(spec -> new ResolvedCarrier(
                            spec,
                            false,
                            Optional.empty()))
                    .toList();
        }
        ManagedAuthoringRule rule =
                ManagedAuthoringProjectDrafts.createRule(draft);
        LinkedHashMap<String, CarrierDraft> carriers =
                new LinkedHashMap<>();
        for (ResolvedCarrier carrier : declared) {
            CarrierDraft candidate = carrier(
                    minecraft,
                    rule,
                    method,
                    inputs,
                    carrier,
                    authoringTemplate);
            CarrierDraft previous = carriers.putIfAbsent(
                    candidate.identity(),
                    candidate);
            if (previous != null
                    && (previous.spec().columns()
                                    != candidate.spec().columns()
                            || previous.spec().rows()
                                    != candidate.spec().rows())) {
                throw new IOException(
                        "CTM_MOD_TEXTURE_CARRIER_LAYOUT_CONFLICT:"
                                + candidate.outputPath());
            }
            if (previous != null
                    && !previous.source().sameCarrierContent(
                            candidate.source())) {
                throw new IOException(
                        "CTM_MOD_TEXTURE_CARRIER_CONFLICT:"
                                + candidate.outputPath());
            }
        }
        ArrayList<ConnectionTextureSet.Slot> slots =
                new ArrayList<>();
        int navigationIndex = 0;
        for (CarrierDraft carrier : carriers.values()) {
            for (CtmModCarrierSlotPlan.Cell cell : CtmModCarrierSlotPlan.cells(
                    carrier.spec(),
                    carrier.source().frameWidth(),
                    carrier.source().frameHeight())) {
                slots.add(new ConnectionTextureSet.Slot(
                        navigationIndex,
                        navigationIndex,
                        cell.physicalIndex(),
                        cell.kind() == CtmModCarrierSlotPlan.Kind.INDEPENDENT_PNG
                                ? CarrierKind.INDEPENDENT_PNG
                                : CarrierKind.SHARED_SHEET,
                        carrier.outputPath(),
                        cell.x(),
                        cell.y(),
                        cell.width(),
                        cell.height(),
                        carrier.intent(),
                        carrier.synthetic(),
                        carrier.source()));
                navigationIndex++;
            }
        }
        ConnectionTextureDraftContext.requireSlots(method, !slots.isEmpty());
        return new ConnectionTextureSet(
                family(),
                carriers.values().stream()
                        .map(value -> value.spec().role())
                        .distinct()
                        .reduce((left, right) -> left + ' ' + right)
                        .orElseThrow(),
                slots);
    }

    private static CarrierDraft carrier(
            Minecraft minecraft,
            ManagedAuthoringRule rule,
            ConnectionMethod method,
            ConnectionTextureDraftContext.DraftInputs inputs,
            ResolvedCarrier resolved,
            boolean authoringTemplate) {
        CarrierSpec spec = resolved.spec();
        ResourceLocation outputId = resolved.textureId()
                .orElseGet(() -> generatedId(
                        rule,
                        spec.role()));
        boolean resourcePresent = resolved.textureId()
                .map(id -> minecraft.getResourceManager()
                        .getResource(
                                SpriteSource.TEXTURE_ID_CONVERTER
                                        .idToFile(id))
                        .isPresent())
                .orElse(false);
        Optional<TextureSourceSnapshot> captured =
                resolved.textureId().flatMap(id -> capture(
                        minecraft,
                        id,
                        spec));
        NativeSlotIntent intent = resolveCarrierIntent(
                captured.isPresent(),
                resolved.declared(),
                resolved.textureId().isPresent(),
                resourcePresent,
                authoringTemplate);
        TextureSourceSnapshot source = captured
                .orElseGet(() -> synthetic(
                        inputs,
                        outputId,
                        method,
                        spec));
        return new CarrierDraft(
                CtmModNativeDocument.texturePath(outputId),
                spec,
                intent,
                captured.isEmpty(),
                source);
    }

    /**
     * 中文：载体意图分类（26.1.2 已验收语义）。已捕获→PRESENT；已声明但纹理缺失→
     * DECLARED_MISSING（无纹理引用时保持 UNKNOWN）；authoring 模板→OMITTED；已声明或
     * 带纹理引用但无法归类→UNKNOWN（保护作者意图）；未声明且无纹理引用的合成载体
     * （布局回退）→OMITTED，使其可填充、可编辑，恢复 CTM 编辑连接纹理能力。
     *
     * <p>English: Carrier-intent classification (accepted 26.1.2 semantics). Captured →
     * PRESENT; declared with missing texture → DECLARED_MISSING (UNKNOWN when no texture
     * reference); authoring template → OMITTED; declared or texture-referencing but
     * unclassifiable → UNKNOWN (author intent protected); undeclared textureless synthetic
     * carriers (layout fallback) → OMITTED so they stay fillable/editable, restoring CTM
     * connected-texture editing.
     */
    static NativeSlotIntent resolveCarrierIntent(
            boolean captured,
            boolean declared,
            boolean hasTextureId,
            boolean resourcePresent,
            boolean authoringTemplate) {
        if (captured) {
            return NativeSlotIntent.PRESENT;
        }
        if (declared && !resourcePresent) {
            return hasTextureId
                    ? NativeSlotIntent.DECLARED_MISSING
                    : NativeSlotIntent.UNKNOWN;
        }
        if (authoringTemplate) {
            return NativeSlotIntent.OMITTED;
        }
        if (declared || hasTextureId) {
            return NativeSlotIntent.UNKNOWN;
        }
        return NativeSlotIntent.OMITTED;
    }

    private static Optional<TextureSourceSnapshot> capture(
            Minecraft minecraft,
            ResourceLocation textureId,
            CarrierSpec spec) {
        try {
            TextureSourceSnapshot source =
                    TextureSourceSnapshot.capture(
                            minecraft.getResourceManager(),
                            textureId);
            return validGrid(source, spec)
                    ? Optional.of(source)
                    : Optional.empty();
        } catch (IOException
                | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static TextureSourceSnapshot synthetic(
            ConnectionTextureDraftContext.DraftInputs inputs,
            ResourceLocation outputId,
            ConnectionMethod method,
            CarrierSpec spec) {
        return CtmModCarrierSynthesis.create(
                inputs.source(),
                outputId.toString(),
                method,
                spec,
                inputs.surface()
                        .overlayProfile());
    }

    private static boolean validGrid(
            TextureSourceSnapshot source,
            CarrierSpec spec) {
        return source.frameWidth() % spec.columns() == 0
                && source.frameHeight() % spec.rows() == 0;
    }

    private static ResourceLocation generatedId(
            ManagedAuthoringRule rule,
            String role) {
        return ResourceLocation.tryParse(CtmModCarrierPaths.generatedId(rule, role));
    }

    private record CarrierDraft(
            String outputPath,
            CarrierSpec spec,
            NativeSlotIntent intent,
            boolean synthetic,
            TextureSourceSnapshot source) {
        private CarrierDraft {
            Objects.requireNonNull(outputPath, "outputPath");
            Objects.requireNonNull(spec, "spec");
            Objects.requireNonNull(intent, "intent");
            Objects.requireNonNull(source, "source");
        }

        private String identity() {
            return outputPath;
        }
    }
}
