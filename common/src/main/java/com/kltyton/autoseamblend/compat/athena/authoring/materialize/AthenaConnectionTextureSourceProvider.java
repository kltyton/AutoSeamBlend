package com.kltyton.autoseamblend.compat.athena.authoring.materialize;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import com.kltyton.autoseamblend.compat.athena.authoring.AthenaDeclaredTexturePlan;
import com.kltyton.autoseamblend.engine.ownership.evidence.NativeResourceIdentifier;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：保留 Athena 4.0.6 的原生独立 PNG 载体：五角色 four-slice 与 pane 的七角色载体。
 *
 * English: Preserves Athena 4.0.6's native independent-PNG carriers for the
 * five-role four-slice and pane seven-role layouts.
 */
public enum AthenaConnectionTextureSourceProvider
        implements ConnectionTextureSources.Provider {
    INSTANCE;

    @Override
    public EngineFamily family() {
        return EngineFamily.ATHENA;
    }

    @Override
    public ConnectionTextureSet capture(
            Minecraft minecraft,
            ManagedAuthoringDraft draft,
            NativePropertyDocumentLoader document)
            throws IOException {
        ConnectionTextureDraftContext.requireSlots(
                draft.resolvedMethod(),
                draft.resolvedMethod() != ConnectionMethod.NONE);
        ConnectionTextureDraftContext.DraftInputs inputs =
                ConnectionTextureDraftContext.draftInputs(
                        minecraft,
                        draft);
        JsonObject root = parse(
                document.sourceDocument());
        String namespace = documentNamespace(
                document.sourceDocumentPath());
        LinkedHashMap<ResourceLocation, TextureSourceSnapshot>
                carriers = new LinkedHashMap<>();
        ArrayList<ConnectionTextureSet.Slot> slots =
                new ArrayList<>();
        List<AthenaDeclaredTexturePlan.Slot> declared =
                AthenaDeclaredTexturePlan.resolve(root, draft.resolvedMethod());
        for (int slot = 0; slot < declared.size(); slot++) {
            AthenaDeclaredTexturePlan.Slot value = declared.get(slot);
            addSlot(
                    slots,
                    carriers,
                    minecraft,
                    draft,
                    inputs,
                    slot,
                    value.reference().orElse(null),
                    namespace,
                    value.fallbackName(),
                    value.recipe());
        }
        return new ConnectionTextureSet(
                family(),
                slots.size() == 1 ? "0" : "0-" + (slots.size() - 1),
                slots);
    }

    private static void addSlot(
            List<ConnectionTextureSet.Slot> slots,
            Map<ResourceLocation, TextureSourceSnapshot>
                    carriers,
            Minecraft minecraft,
            ManagedAuthoringDraft draft,
            ConnectionTextureDraftContext.DraftInputs
                    inputs,
            int slot,
            String reference,
            String defaultNamespace,
            String fallbackName,
            GeneratedTileRecipe recipe) {
        Optional<ResourceLocation> nativeId = textureId(
                reference,
                defaultNamespace);
        ResourceLocation outputId = nativeId.orElseGet(() ->
                generatedTextureId(
                        draft,
                        fallbackName));
        Optional<TextureSourceSnapshot> captured =
                nativeId.flatMap(id -> capture(
                        minecraft,
                        id));
        boolean resourcePresent = nativeId
                .map(id -> minecraft.getResourceManager()
                        .getResource(
                                SpriteSource
                                        .TEXTURE_ID_CONVERTER
                                        .idToFile(id))
                        .isPresent())
                .orElse(false);
        TextureSourceSnapshot candidate = captured
                .orElseGet(() -> inputs.source()
                        .transformTo(
                                outputId.toString(),
                                recipe,
                                inputs.surface()
                                        .overlayProfile()));
        TextureSourceSnapshot existing =
                carriers.putIfAbsent(
                        outputId,
                        candidate);
        if (existing != null
                && !existing.sameCarrierContent(
                        candidate)) {
            throw new IllegalArgumentException(
                    "ATHENA_TEXTURE_CARRIER_CONFLICT:"
                            + outputId);
        }
        TextureSourceSnapshot source = existing == null
                ? candidate
                : existing;
        NativeSlotIntent intent = captured.isPresent()
                ? NativeSlotIntent.PRESENT
                : nativeId.isPresent()
                                && !resourcePresent
                        ? NativeSlotIntent.DECLARED_MISSING
                        : NativeSlotIntent.UNKNOWN;
        slots.add(new ConnectionTextureSet.Slot(
                slot,
                slot,
                0,
                CarrierKind.INDEPENDENT_PNG,
                texturePath(outputId),
                0,
                0,
                source.frameWidth(),
                source.frameHeight(),
                intent,
                intent == NativeSlotIntent.DECLARED_MISSING,
                source));
    }

    private static Optional<TextureSourceSnapshot>
            capture(
                    Minecraft minecraft,
                    ResourceLocation textureId) {
        try {
            return Optional.of(
                    TextureSourceSnapshot.capture(
                            minecraft.getResourceManager(),
                            textureId));
        } catch (IOException
                | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static JsonObject parse(byte[] document) {
        try {
            JsonElement parsed = JsonParser.parseString(
                    new String(
                            document,
                            StandardCharsets.UTF_8));
            return parsed instanceof JsonObject root
                    ? root
                    : new JsonObject();
        } catch (RuntimeException exception) {
            return new JsonObject();
        }
    }

    private static Optional<ResourceLocation> textureId(
            String reference,
            String defaultNamespace) {
        return NativeResourceIdentifier.textureId(reference, defaultNamespace)
                .map(ResourceLocation::tryParse);
    }

    private static ResourceLocation generatedTextureId(
            ManagedAuthoringDraft draft,
            String name) {
        ManagedAuthoringRule rule =
                ManagedAuthoringProjectDrafts.createRule(draft);
        return new ResourceLocation(
                "autoseamblend",
                "generated/"
                        + draft.resolvedMethod()
                                .serializedName()
                        + '/'
                        + rule.managedStem()
                        + '/'
                        + name);
    }

    private static String texturePath(
            ResourceLocation textureId) {
        return "assets/"
                + textureId.getNamespace()
                + "/textures/"
                + textureId.getPath()
                + ".png";
    }

    private static String documentNamespace(
            String path) {
        String[] segments = path.split("/", 3);
        if (segments.length != 3
                || !"assets".equals(segments[0])) {
            throw new IllegalArgumentException(
                    "Athena document is outside assets");
        }
        return segments[1];
    }

}
