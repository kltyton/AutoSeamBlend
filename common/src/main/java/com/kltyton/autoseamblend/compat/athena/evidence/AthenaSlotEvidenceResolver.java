package com.kltyton.autoseamblend.compat.athena.evidence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import com.kltyton.autoseamblend.engine.ownership.NativeSlotIntent;
import com.kltyton.autoseamblend.engine.ownership.evidence.NativeResourceIdentifier;
import com.kltyton.autoseamblend.engine.ownership.evidence.NativeResourceSource;
import com.kltyton.autoseamblend.engine.ownership.evidence.NativeResourceSource.SheetFramePolicy;
import com.kltyton.autoseamblend.engine.ownership.evidence.NativeResourceSource.TextureResourceState;
import com.kltyton.autoseamblend.engine.ownership.evidence.NativeSlotEvidence;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：解析 Athena 的方向与角色键表达式，并生成公共槽位证据；4.0.6 无 [$index] 载体。
 * English: Resolves Athena direction and role-key expressions into common slots; 4.0.6 has no
 * [$index] carrier.
 */
public final class AthenaSlotEvidenceResolver {
    private AthenaSlotEvidenceResolver() {}

    public static List<NativeSlot> resolve(
            String documentId,
            JsonObject root,
            ConnectionMethod method,
            NativeResourceSource resources) {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(resources, "resources");
        String defaultNamespace = NativeResourceIdentifier
                .namespace(documentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "invalid document id"));
        JsonElement encoded = root.get("ctm_textures");
        if (encoded == null) {
            return NativeSlotEvidence.unknown(
                    method,
                    NativeSlotEvidence.FULL_CTM_SLOTS);
        }
        if (encoded instanceof JsonObject textures
                && fourSlice(textures)) {
            return fourSlice(
                    textures,
                    isPane(root),
                    defaultNamespace,
                    resources);
        }
        ArrayList<NativeSlot> slots = new ArrayList<>();
        collect(
                encoded,
                defaultNamespace,
                resources,
                slots);
        return List.copyOf(slots);
    }

    private static void collect(
            JsonElement encoded,
            String defaultNamespace,
            NativeResourceSource resources,
            List<NativeSlot> output) {
        if (encoded == null) {
            output.add(NativeSlotEvidence.unknown(output.size()));
            return;
        }
        if (encoded.isJsonPrimitive()
                && encoded.getAsJsonPrimitive().isString()) {
            String reference = encoded.getAsString();
            if (reference.contains("[$index]")) {
                output.add(NativeSlotEvidence.unknown(output.size()));
            } else {
                output.add(resourceSlot(
                        output.size(),
                        reference,
                        defaultNamespace,
                        resources));
            }
            return;
        }
        if (!(encoded instanceof JsonObject textures)) {
            output.add(NativeSlotEvidence.unknown(output.size()));
            return;
        }
        if (fourSlice(textures)) {
            for (String key : List.of(
                    "particle",
                    "empty",
                    "center",
                    "vertical",
                    "horizontal")) {
                collect(
                        textures.get(key),
                        defaultNamespace,
                        resources,
                        output);
            }
            return;
        }
        int initialSize = output.size();
        for (String key : List.of(
                "default",
                "down",
                "up",
                "north",
                "south",
                "west",
                "east")) {
            if (textures.has(key)) {
                collect(
                        textures.get(key),
                        defaultNamespace,
                        resources,
                        output);
            }
        }
        if (output.size() == initialSize) {
            output.add(NativeSlotEvidence.unknown(output.size()));
        }
    }

    private static boolean fourSlice(JsonObject textures) {
        return textures.has("center")
                || textures.has("vertical")
                || textures.has("horizontal")
                || textures.has("empty");
    }

    private static List<NativeSlot> fourSlice(
            JsonObject textures,
            boolean pane,
            String defaultNamespace,
            NativeResourceSource resources) {
        ArrayList<NativeSlot> slots = new ArrayList<>(pane ? 7 : 5);
        for (String key : List.of(
                "particle",
                "empty",
                "center",
                "vertical",
                "horizontal")) {
            slots.add(resourceSlot(
                    slots.size(),
                    textures.get(key),
                    defaultNamespace,
                    resources));
        }
        if (pane) {
            JsonElement particle = textures.get("particle");
            slots.add(resourceSlot(
                    5,
                    textures.has("edge")
                            ? textures.get("edge")
                            : particle,
                    defaultNamespace,
                    resources));
            slots.add(resourceSlot(
                    6,
                    textures.has("side_edge")
                            ? textures.get("side_edge")
                            : particle,
                    defaultNamespace,
                    resources));
        }
        return List.copyOf(slots);
    }

    private static boolean isPane(JsonObject root) {
        JsonElement loader = root.get("athena:loader");
        return loader != null
                && loader.isJsonPrimitive()
                && loader.getAsJsonPrimitive().isString()
                && "athena:pane_ctm".equals(loader.getAsString());
    }

    private static NativeSlot resourceSlot(
            int slot,
            JsonElement reference,
            String defaultNamespace,
            NativeResourceSource resources) {
        if (reference == null
                || !reference.isJsonPrimitive()
                || !reference.getAsJsonPrimitive().isString()) {
            return NativeSlotEvidence.unknown(slot);
        }
        return resourceSlot(
                slot,
                reference.getAsString(),
                defaultNamespace,
                resources);
    }

    private static NativeSlot resourceSlot(
            int slot,
            String reference,
            String defaultNamespace,
            NativeResourceSource resources) {
        Optional<String> sprite = NativeResourceIdentifier.textureId(
                reference,
                defaultNamespace);
        if (sprite.isEmpty()) {
            return NativeSlotEvidence.unknown(slot);
        }
        String spriteId = sprite.orElseThrow();
        TextureResourceState state = resources.inspectTexture(
                spriteId,
                1,
                1,
                SheetFramePolicy.EXISTENCE_ONLY);
        return switch (state) {
            case PRESENT -> new NativeSlot(
                    slot,
                    NativeSlotIntent.PRESENT,
                    Optional.of(spriteId));
            case MISSING -> new NativeSlot(
                    slot,
                    NativeSlotIntent.DECLARED_MISSING,
                    Optional.of(spriteId));
            case INVALID -> NativeSlotEvidence.unknown(slot);
        };
    }
}
