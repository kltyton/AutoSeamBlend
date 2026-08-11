package com.kltyton.autoseamblend.compat.fusion.authoring.materialize;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.ownership.NativeSlotIntent;
import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureSet;
import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureDraftContext;
import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureSet.CarrierKind;
import com.kltyton.autoseamblend.authoring.materialize.ConnectionTextureSources;
import com.kltyton.autoseamblend.authoring.materialize.TextureSourceSnapshot;
import com.kltyton.autoseamblend.authoring.materialize.TextureSourceSnapshot.SheetTile;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringDraft;
import com.kltyton.autoseamblend.compat.fusion.authoring.materialize.FusionNativeCarrierPlanning;
import com.kltyton.autoseamblend.authoring.property.NativePropertyDocumentLoader;
import com.kltyton.autoseamblend.compat.fusion.texture.generation.FusionNativeSheetPlan;
import com.kltyton.autoseamblend.compat.fusion.authoring.materialize.FusionNativeEvidenceLayout;
import com.kltyton.autoseamblend.compat.fusion.evidence.FusionSlotEvidenceResolver;
import com.kltyton.autoseamblend.compat.fusion.evidence.FusionSlotEvidenceResolver.FusionCarrier;
import com.kltyton.autoseamblend.engine.ownership.evidence.MinecraftNativeResourceSource;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 中文：使用 Fusion 锁定版本的原生布局与模型元数据，把逻辑槽映射到共享纹理表的物理单元。
 *
 * English: Uses the locked Fusion layout and model metadata to map logical
 * slots onto physical cells of one shared native sheet.
 */
public enum FusionConnectionTextureSourceProvider
        implements ConnectionTextureSources.Provider {
    INSTANCE;

    @Override
    public EngineFamily family() {
        return EngineFamily.FUSION;
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
        if (method == ConnectionMethod.FIXED) {
            return fixedDraft(draft, inputs);
        }
        FusionNativeSheetPlan plan =
                FusionNativeSheetPlan.create(method);
        boolean authoringTemplate =
                ConnectionTextureSources
                        .managedAuthoringTemplate(
                                draft,
                                document);
        Optional<NativeCarrier> detectedCarrier =
                resolveNativeCarrier(
                        document,
                        minecraft.getResourceManager(),
                        method);
        Optional<FusionNativeEvidenceLayout> nativeLayout =
                detectedCarrier.flatMap(value ->
                        FusionNativeEvidenceLayout.resolve(
                                method,
                                value.declaredLayout()));
        Optional<NativeCarrier> nativeCarrier =
                nativeLayout.isPresent()
                        ? detectedCarrier
                        : Optional.empty();
        FusionNativeEvidenceLayout layout = nativeLayout
                .or(() -> FusionNativeEvidenceLayout.resolve(
                        method,
                        Optional.of(plan.layout())))
                        .orElseThrow(() -> new IOException(
                                "FUSION_NATIVE_LAYOUT_INVALID"));
        String outputPath;
        TextureSourceSnapshot carrier;
        boolean nativePresent = false;
        if (nativeCarrier.isPresent()
                && nativeCarrier.orElseThrow()
                        .source().isPresent()) {
            TextureSourceSnapshot candidate =
                    nativeCarrier.orElseThrow()
                            .source()
                            .orElseThrow();
            FusionNativeCarrierPlanning.CellSize size =
                    FusionNativeCarrierPlanning.cellSize(candidate, layout)
                    .orElse(null);
            if (size != null) {
                carrier = candidate;
                outputPath = texturePath(
                        nativeCarrier.orElseThrow()
                                .textureId());
                nativePresent = true;
            } else {
                carrier = generatedCarrier(
                        draft,
                        inputs,
                        plan,
                        layout);
                outputPath = FusionNativeCarrierPlanning.generatedPath(
                        draft);
            }
        } else {
            carrier = generatedCarrier(
                    draft,
                    inputs,
                    plan,
                    layout);
            outputPath = FusionNativeCarrierPlanning.generatedPath(draft);
        }
        FusionNativeCarrierPlanning.CellSize size =
                FusionNativeCarrierPlanning.cellSize(carrier, layout)
                .orElseThrow(() -> new IOException(
                        "FUSION_SHEET_LAYOUT_INVALID"));
        NativeSlotIntent carrierIntent;
        if (nativePresent) {
            carrierIntent = NativeSlotIntent.PRESENT;
        } else if (detectedCarrier.isEmpty()
                && authoringTemplate) {
            carrierIntent = NativeSlotIntent.OMITTED;
        } else if (nativeCarrier.isPresent()
                && nativeCarrier.orElseThrow()
                        .source().isEmpty()
                && !nativeCarrier.orElseThrow()
                        .resourcePresent()) {
            carrierIntent = NativeSlotIntent.DECLARED_MISSING;
        } else {
            carrierIntent = NativeSlotIntent.UNKNOWN;
        }
        boolean carrierSynthetic = carrierIntent
                        == NativeSlotIntent.DECLARED_MISSING
                || carrierIntent == NativeSlotIntent.OMITTED;
        ArrayList<SlotDraft> drafts = new ArrayList<>();
        for (int logicalIndex = 0;
                logicalIndex < layout.logicalCells().size();
                logicalIndex++) {
            for (int physicalIndex
                    : layout.logicalCells()
                            .get(logicalIndex)) {
                int x = physicalIndex
                        % layout.columns()
                        * size.width();
                int y = physicalIndex
                        / layout.columns()
                        * size.height();
                drafts.add(new SlotDraft(
                        logicalIndex,
                        logicalIndex,
                        physicalIndex,
                        x,
                        y,
                        carrierIntent,
                        carrierSynthetic));
            }
        }
        ArrayList<ConnectionTextureSet.Slot> slots =
                new ArrayList<>(drafts.size());
        for (SlotDraft slot : drafts) {
            slots.add(new ConnectionTextureSet.Slot(
                    slot.navigationIndex(),
                    slot.logicalIndex(),
                    slot.physicalIndex(),
                    CarrierKind.SHARED_SHEET,
                    outputPath,
                    slot.x(),
                    slot.y(),
                    size.width(),
                    size.height(),
                    slot.intent(),
                    slot.synthetic(),
                    carrier));
        }
        return new ConnectionTextureSet(
                family(),
                "0-" + (layout.logicalCells().size() - 1),
                slots);
    }

    private static ConnectionTextureSet fixedDraft(
            ManagedAuthoringDraft draft,
            ConnectionTextureDraftContext.DraftInputs
                    inputs) {
        String outputPath = FusionNativeCarrierPlanning.generatedPath(draft)
                .replace("/sheet.png", "/fixed.png");
        TextureSourceSnapshot source = inputs.source()
                .transformTo(
                        textureId(outputPath).toString(),
                        GeneratedTileRecipe.Source.INSTANCE);
        source = source.withSourceMetadata(
                FusionNativeCarrierPlanning.generatedBaseMetadata(
                        draft.requestedMethod(),
                        draft.compatibility(),
                        inputs.source()
                                .sourceMetadata()));
        return new ConnectionTextureSet(
                EngineFamily.FUSION,
                "0",
                List.of(new ConnectionTextureSet.Slot(
                        0,
                        outputPath,
                        NativeSlotIntent.OMITTED,
                        true,
                        source)));
    }

    private static TextureSourceSnapshot
            generatedCarrier(
                    ManagedAuthoringDraft draft,
                    ConnectionTextureDraftContext.DraftInputs
                            inputs,
                    FusionNativeSheetPlan plan,
                    FusionNativeEvidenceLayout layout) {
        // 中文：动画 FULL 必须输出 6 行，静态 FULL 保留 8 行 padding；Fusion 1.3.12 对
        // FULL 动画按 6:8 宽高比校验，整图方形会被拒绝，因此合成后再追加空帧行破方。
        // English: Animated FULL must emit 6 rows while static FULL keeps its 8-row padding;
        // Fusion 1.3.12 validates animated FULL against the 6:8 aspect ratio and rejects a
        // whole-image-square sheet, so an empty frame row is appended after compositing.
        int rows = FusionNativeCarrierPlanning.carrierRows(
                layout,
                inputs.source().animated());
        ArrayList<Optional<SheetTile>> tiles =
                new ArrayList<>(Math.multiplyExact(
                        layout.columns(),
                        rows));
        for (var tileRecipe : plan.tileRecipes()) {
            tiles.add(tileRecipe.map(value ->
                    new SheetTile(
                            inputs.source(),
                            value,
                            inputs.surface()
                                    .overlayProfile())));
        }
        while (tiles.size()
                < Math.multiplyExact(
                        layout.columns(),
                        rows)) {
            tiles.add(Optional.empty());
        }
        String outputPath = FusionNativeCarrierPlanning.generatedPath(draft);
        TextureSourceSnapshot composited = inputs
                .source()
                .compositeSheetTo(
                        textureId(outputPath).toString(),
                        layout.columns(),
                        rows,
                        tiles);
        // 中文：动画 FULL 整图方形时追加一行空帧，避免 Fusion 1.3.12 在读取动画帧尺寸前
        // 按整图宽高拒绝；padding 后必须用原 frameIndices 显式排除新空帧。
        // English: Appends one empty frame row when the animated FULL sheet is whole-image
        // square so Fusion 1.3.12 does not reject it before reading animation frame sizes;
        // after padding the original frameIndices must explicitly exclude the new empty frame.
        TextureSourceSnapshot carrier =
                FusionNativeCarrierPlanning.avoidAnimatedFullSquareSheet(
                        composited,
                        layout);
        boolean paddingApplied =
                carrier.sheetHeight() != composited.sheetHeight();
        return carrier.withSourceMetadata(
                FusionNativeCarrierPlanning.generatedConnectingMetadata(
                        draft.requestedMethod(),
                        draft.compatibility(),
                        plan.layout(),
                        inputs.source().sourceMetadata(),
                        carrier.frameWidth(),
                        carrier.frameHeight(),
                        carrier.animated(),
                        carrier.frameIndices(),
                        paddingApplied));
    }

    private static Optional<NativeCarrier>
            resolveNativeCarrier(
                    NativePropertyDocumentLoader document,
                    ResourceManager resources,
                    ConnectionMethod method) {
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(
                    new String(
                            document.sourceDocument(),
                            StandardCharsets.UTF_8));
            if (!(parsed instanceof JsonObject object)) {
                return Optional.empty();
            }
            root = object;
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        ResourceLocation documentId = FusionNativeCarrierPlanning.resourceId(
                document.sourceDocumentPath())
                .orElse(null);
        if (documentId == null) {
            return Optional.empty();
        }
        Optional<FusionCarrier> connecting = FusionSlotEvidenceResolver.resolveCarrier(
                documentId.toString(),
                root,
                new MinecraftNativeResourceSource(resources));
        if (connecting.isEmpty()) {
            return Optional.empty();
        }
        FusionCarrier metadata = connecting.orElseThrow();
        ResourceLocation textureId = ResourceLocation.tryParse(metadata.spriteId());
        if (textureId == null) {
            return Optional.empty();
        }
        boolean resourcePresent = resources.getResource(
                        SpriteSource.TEXTURE_ID_CONVERTER
                                .idToFile(
                                        textureId))
                .isPresent();
        Optional<TextureSourceSnapshot> source;
        try {
            TextureSourceSnapshot captured =
                            TextureSourceSnapshot.capture(
                            resources,
                            textureId);
            source = FusionNativeEvidenceLayout.resolve(
                            method,
                            metadata.declaredLayout())
                    .map(layout -> FusionNativeCarrierPlanning.normalizeFrames(
                            captured,
                            layout));
        } catch (IOException
                | IllegalArgumentException
                | ArithmeticException exception) {
            source = Optional.empty();
        }
        return Optional.of(new NativeCarrier(
                textureId,
                metadata.declaredLayout(),
                resourcePresent,
                source));
    }

    private static String texturePath(
            ResourceLocation textureId) {
        return "assets/"
                + textureId.getNamespace()
                + "/textures/"
                + textureId.getPath()
                + ".png";
    }

    private static ResourceLocation textureId(
            String outputPath) {
        String[] segments = outputPath.split("/", 4);
        if (segments.length != 4
                || !"assets".equals(segments[0])
                || !"textures".equals(segments[2])
                || !segments[3].endsWith(".png")) {
            throw new IllegalArgumentException(
                    "texture carrier path is outside assets textures");
        }
        return new ResourceLocation(
                segments[1],
                segments[3].substring(
                        0,
                        segments[3].length()
                                - ".png".length()));
    }

    private record NativeCarrier(
            ResourceLocation textureId,
            Optional<String> declaredLayout,
            boolean resourcePresent,
            Optional<TextureSourceSnapshot>
                    source) {}

    private record SlotDraft(
            int navigationIndex,
            int logicalIndex,
            int physicalIndex,
            int x,
            int y,
            NativeSlotIntent intent,
            boolean synthetic) {}
}
