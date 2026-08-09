package com.kltyton.autoseamblend.authoring.workbench;

import com.kltyton.autoseamblend.authoring.workbench.PaintTool;
import com.kltyton.autoseamblend.authoring.workbench.WorkbenchDraftFields;
import com.kltyton.autoseamblend.authoring.selector.NativeBlockSelectorField;
import com.kltyton.autoseamblend.authoring.selector.MinecraftNativeBlockSelectorResolver;
import com.kltyton.autoseamblend.frontend.controller.WorkbenchAction;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.EnumSet;
import net.minecraft.core.Direction;

/**
 * 中文：工作台在一个会话修订中持有的引擎类型无关草稿。
 * English: Engine-type-free workbench draft held by one session revision.
 */
public class WorkbenchDraft implements WorkbenchDraftFields {
    private final String engineId;
    private final String targetBlockId;
    private final String sourceTextureId;
    private final String originalModelId;
    private final ConnectionMethod requestedMethod;
    private final ConnectionMethod resolvedMethod;
    private final boolean compatibility;
    private final boolean pane;
    private final String nativeDocumentKey;
    private final Direction defaultPreviewFace;
    private final Map<String, byte[]> nativeState;
    private final Optional<PaintState> paint;
    private final Optional<NativePropertiesState> properties;

    public WorkbenchDraft(
            String engineId,
            String targetBlockId,
            String sourceTextureId,
            String originalModelId,
            ConnectionMethod requestedMethod,
            ConnectionMethod resolvedMethod,
            boolean compatibility,
            boolean pane,
            String nativeDocumentKey,
            Direction defaultPreviewFace,
            Map<String, byte[]> nativeState,
            Optional<PaintState> paint,
            Optional<NativePropertiesState> properties) {
        requireText(engineId, "engineId");
        requireText(targetBlockId, "targetBlockId");
        requireText(sourceTextureId, "sourceTextureId");
        requireText(originalModelId, "originalModelId");
        Objects.requireNonNull(requestedMethod, "requestedMethod");
        Objects.requireNonNull(resolvedMethod, "resolvedMethod");
        if (resolvedMethod == ConnectionMethod.AUTO
                || requestedMethod != ConnectionMethod.AUTO
                        && requestedMethod != resolvedMethod) {
            throw new IllegalArgumentException("draft method resolution is inconsistent");
        }
        requireText(nativeDocumentKey, "nativeDocumentKey");
        Objects.requireNonNull(defaultPreviewFace, "defaultPreviewFace");
        LinkedHashMap<String, byte[]> state = new LinkedHashMap<>();
        Objects.requireNonNull(nativeState, "nativeState").forEach((key, value) -> {
            requireText(key, "native state key");
            state.put(key, Objects.requireNonNull(value, "native state value").clone());
        });
        nativeState = Collections.unmodifiableMap(state);
        paint = Objects.requireNonNull(paint, "paint");
        properties = Objects.requireNonNull(properties, "properties");
        this.engineId = engineId;
        this.targetBlockId = targetBlockId;
        this.sourceTextureId = sourceTextureId;
        this.originalModelId = originalModelId;
        this.requestedMethod = requestedMethod;
        this.resolvedMethod = resolvedMethod;
        this.compatibility = compatibility;
        this.pane = pane;
        this.nativeDocumentKey = nativeDocumentKey;
        this.defaultPreviewFace = defaultPreviewFace;
        this.nativeState = nativeState;
        this.paint = paint;
        this.properties = properties;
    }

    public String engineId() {
        return engineId;
    }

    public String targetBlockId() {
        return targetBlockId;
    }

    public String sourceTextureId() {
        return sourceTextureId;
    }

    public String originalModelId() {
        return originalModelId;
    }

    public ConnectionMethod requestedMethod() {
        return requestedMethod;
    }

    public ConnectionMethod resolvedMethod() {
        return resolvedMethod;
    }

    public boolean compatibility() {
        return compatibility;
    }

    public boolean pane() {
        return pane;
    }

    public String nativeDocumentKey() {
        return nativeDocumentKey;
    }

    public Direction defaultPreviewFace() {
        return defaultPreviewFace;
    }

    public Optional<PaintState> paint() {
        return paint;
    }

    public Optional<NativePropertiesState> properties() {
        return properties;
    }

    public Map<String, byte[]> nativeState() {
        LinkedHashMap<String, byte[]> copy = new LinkedHashMap<>();
        nativeState.forEach((key, value) -> copy.put(key, value.clone()));
        return Collections.unmodifiableMap(copy);
    }

    /** 中文：原生槽位服务投影出的不可变绘画状态。 / English: Immutable paint state projected by a native-slot service. */
    public static final class PaintState {
        private final int width;
        private final int height;
        private final int[] straightArgb;
        private final Direction selectedFace;
        private final List<Integer> slots;
        private final int selectedSlot;
        private final boolean selectedSynthetic;
        private final PaintTool tool;
        private final int color;
        private final int brushSize;
        private final boolean editable;
        private final boolean canUndo;
        private final boolean canRedo;
        private final String statusKey;

        public PaintState(
                int width,
                int height,
                int[] straightArgb,
                Direction selectedFace,
                List<Integer> slots,
                int selectedSlot,
                boolean selectedSynthetic,
                PaintTool tool,
                int color,
                int brushSize,
                boolean editable,
                boolean canUndo,
                boolean canRedo,
                String statusKey) {
            if (width <= 0 || height <= 0
                    || Math.multiplyExact(width, height) != straightArgb.length
                    || brushSize <= 0) {
                throw new IllegalArgumentException("invalid workbench paint state");
            }
            this.width = width;
            this.height = height;
            this.straightArgb = straightArgb.clone();
            this.selectedFace = Objects.requireNonNull(selectedFace, "selectedFace");
            this.slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
            if (this.slots.isEmpty() || !this.slots.contains(selectedSlot)) {
                throw new IllegalArgumentException("selected workbench paint slot is unavailable");
            }
            this.selectedSlot = selectedSlot;
            this.selectedSynthetic = selectedSynthetic;
            this.tool = Objects.requireNonNull(tool, "tool");
            this.color = color;
            this.brushSize = brushSize;
            this.editable = editable;
            this.canUndo = canUndo;
            this.canRedo = canRedo;
            requireText(statusKey, "statusKey");
            this.statusKey = statusKey;
        }

        public int width() { return width; }
        public int height() { return height; }
        public int[] straightArgb() { return straightArgb.clone(); }
        public Direction selectedFace() { return selectedFace; }
        public List<Integer> slots() { return slots; }
        public int selectedSlot() { return selectedSlot; }
        public boolean selectedSynthetic() { return selectedSynthetic; }
        public PaintTool tool() { return tool; }
        public int color() { return color; }
        public int brushSize() { return brushSize; }
        public boolean editable() { return editable; }
        public boolean canUndo() { return canUndo; }
        public boolean canRedo() { return canRedo; }
        public String statusKey() { return statusKey; }
    }

    /** 中文：原生文档的不可变、结构化、无损编辑投影。 / English: Immutable structured lossless native-document edit projection. */
    public record NativePropertiesState(
            String documentLabel,
            List<Field> fields,
            String preservedFieldsStatusKey,
            Optional<String> entryId,
            NativeBlockSelectorField matchingSelector,
            NativeBlockSelectorField connectionSelector,
            Set<Direction> faces,
            ConnectionBasis connectionBasis,
            RenderLayer renderLayer,
            Optional<String> tintBlockId,
            AthenaConnection athenaConnection,
            boolean entryIdEditable,
            boolean matchingSelectorEditable,
            boolean connectionSelectorEditable,
            boolean facesEditable,
            boolean connectionBasisEditable,
            boolean renderLayerEditable,
            boolean tintBlockEditable,
            boolean athenaConnectionEditable,
            Map<String, String> nativeDetails,
            Map<String, Optional<String>> changedValues) {
        public NativePropertiesState {
            requireText(documentLabel, "documentLabel");
            fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
            requireText(preservedFieldsStatusKey, "preservedFieldsStatusKey");
            entryId = Objects.requireNonNull(entryId, "entryId");
            matchingSelector = Objects.requireNonNull(matchingSelector, "matchingSelector");
            connectionSelector = Objects.requireNonNull(connectionSelector, "connectionSelector");
            faces = Set.copyOf(Objects.requireNonNull(faces, "faces"));
            connectionBasis = Objects.requireNonNull(connectionBasis, "connectionBasis");
            renderLayer = Objects.requireNonNull(renderLayer, "renderLayer");
            tintBlockId = Objects.requireNonNull(tintBlockId, "tintBlockId");
            athenaConnection = Objects.requireNonNull(athenaConnection, "athenaConnection");
            nativeDetails = immutableOrderedMap(nativeDetails, "nativeDetails");
            LinkedHashMap<String, Optional<String>> changed = new LinkedHashMap<>();
            Objects.requireNonNull(changedValues, "changedValues").forEach((key, value) -> {
                requireText(key, "changed property id");
                changed.put(key, Objects.requireNonNull(value, "changed property value"));
            });
            changedValues = Collections.unmodifiableMap(changed);
        }

        public NativePropertiesState with(
                List<Field> nextFields,
                Optional<String> nextEntryId,
                NativeBlockSelectorField nextMatchingSelector,
                NativeBlockSelectorField nextConnectionSelector,
                Set<Direction> nextFaces,
                ConnectionBasis nextConnectionBasis,
                RenderLayer nextRenderLayer,
                Optional<String> nextTintBlockId,
                AthenaConnection nextAthenaConnection,
                Map<String, Optional<String>> nextChangedValues) {
            return new NativePropertiesState(
                    documentLabel, nextFields, preservedFieldsStatusKey, nextEntryId,
                    nextMatchingSelector, nextConnectionSelector, nextFaces,
                    nextConnectionBasis, nextRenderLayer, nextTintBlockId,
                    nextAthenaConnection, entryIdEditable, matchingSelectorEditable,
                    connectionSelectorEditable, facesEditable, connectionBasisEditable,
                    renderLayerEditable, tintBlockEditable, athenaConnectionEditable,
                    nativeDetails, nextChangedValues);
        }

        public NativePropertiesState apply(
                WorkbenchAction edit,
                ValueEncoding encoding,
                Optional<String> targetBlockId) {
            Objects.requireNonNull(edit, "edit");
            Objects.requireNonNull(encoding, "encoding");
            Objects.requireNonNull(targetBlockId, "targetBlockId");
            List<Field> nextFields = fields;
            Optional<String> nextEntryId = entryId;
            NativeBlockSelectorField nextMatching = matchingSelector;
            NativeBlockSelectorField nextConnection = connectionSelector;
            Set<Direction> nextFaces = faces;
            ConnectionBasis nextBasis = connectionBasis;
            RenderLayer nextLayer = renderLayer;
            Optional<String> nextTint = tintBlockId;
            AthenaConnection nextAthena = athenaConnection;
            LinkedHashMap<String, Optional<String>> changed = new LinkedHashMap<>(changedValues);

            if (edit instanceof WorkbenchAction.SetNativeProperty value) {
                if (!value.fieldId().equals("method")
                        && !value.fieldId().equals("compatibility")) {
                    throw new UnsupportedOperationException(
                            "generic native-property values are limited to method and compatibility");
                }
                Field field = requireEditableField(value.fieldId());
                if (!field.options().isEmpty()
                        && field.options().stream().noneMatch(option -> option.token().equals(value.valueToken()))) {
                    throw new IllegalArgumentException("NATIVE_PROPERTY_VALUE_REJECTED:" + value.fieldId());
                }
                nextFields = replaceField(fields, value.fieldId(), value.valueToken());
                changed.put(value.fieldId(), Optional.of(value.fieldId().equals("compatibility")
                        ? value.valueToken() : encoding.encode(value.valueToken())));
            } else if (edit instanceof WorkbenchAction.SetNativeEntryId value) {
                requireSupported(entryIdEditable, "entry id");
                nextEntryId = optionalText(value.value());
                changed.put("id", nextEntryId.map(encoding::encode));
                nextFields = replaceFieldIfPresent(fields, "id", nextEntryId.orElse(""));
            } else if (edit instanceof WorkbenchAction.ToggleNativeFace value) {
                requireSupported(facesEditable, "faces");
                EnumSet<Direction> mutable = faces.isEmpty()
                        ? EnumSet.noneOf(Direction.class) : EnumSet.copyOf(faces);
                if (!mutable.remove(value.face())) mutable.add(value.face());
                nextFaces = Set.copyOf(mutable);
                String token = mutable.stream().map(direction -> direction.name().toLowerCase(java.util.Locale.ROOT))
                        .collect(java.util.stream.Collectors.joining(" "));
                changed.put("faces", Optional.of(token));
                nextFields = replaceFieldIfPresent(fields, "faces", token);
            } else if (edit instanceof WorkbenchAction.CycleNativeConnectionBasis) {
                requireSupported(connectionBasisEditable, "connection basis");
                nextBasis = switch (connectionBasis) {
                    case BLOCK -> ConnectionBasis.TILE;
                    case TILE -> ConnectionBasis.STATE;
                    case STATE -> ConnectionBasis.BLOCK;
                };
                String token = nextBasis.name().toLowerCase(java.util.Locale.ROOT);
                changed.put("connect", Optional.of(token));
                nextFields = replaceFieldIfPresent(fields, "connect", token);
            } else if (edit instanceof WorkbenchAction.CycleNativeRenderLayer) {
                requireSupported(renderLayerEditable, "render layer");
                nextLayer = renderLayer == RenderLayer.CUTOUT ? RenderLayer.TRANSLUCENT : RenderLayer.CUTOUT;
                String token = nextLayer.name().toLowerCase(java.util.Locale.ROOT);
                changed.put("layer", Optional.of(token));
                nextFields = replaceFieldIfPresent(fields, "layer", token);
            } else if (edit instanceof WorkbenchAction.SetNativeTintBlock value) {
                requireSupported(tintBlockEditable, "tint block");
                nextTint = optionalText(value.blockId()).map(id ->
                        MinecraftNativeBlockSelectorResolver.requireRegisteredBlockId(id, "tint block"));
                changed.put("tintBlock", nextTint);
                nextFields = replaceFieldIfPresent(fields, "tintBlock", nextTint.orElse(""));
            } else if (edit instanceof WorkbenchAction.AddNativeSelectorBlock value) {
                String blockId = MinecraftNativeBlockSelectorResolver.requireSelectableBlockId(value.blockId(), "selector block");
                if (value.kind() == WorkbenchAction.NativeSelectorKind.MATCHING) {
                    requireSupported(matchingSelectorEditable, "matching selector");
                    nextMatching = matchingSelector.addBlock(blockId, MinecraftNativeBlockSelectorResolver.DEFAULT_ONLY);
                    changed.put("matchBlocks", nextMatching.serializedValue());
                    nextFields = replaceFieldIfPresent(fields, "matchBlocks", nextMatching.serializedValue().orElse(""));
                } else if (connectionSelectorEditable) {
                    nextConnection = connectionSelector.addBlock(blockId, MinecraftNativeBlockSelectorResolver.DEFAULT_ONLY);
                    changed.put("connectBlocks", nextConnection.serializedValue());
                    nextFields = replaceFieldIfPresent(fields, "connectBlocks", nextConnection.serializedValue().orElse(""));
                } else {
                    requireSupported(athenaConnectionEditable, "Athena connection");
                    nextConnection = NativeBlockSelectorField.registeredBlocks(
                            true, List.of(blockId), MinecraftNativeBlockSelectorResolver.DEFAULT_ONLY);
                    nextAthena = AthenaConnection.STATE;
                    String token = athenaState(blockId);
                    changed.put("connect_to", Optional.of(token));
                    nextFields = replaceFieldIfPresent(fields, "connect_to", token);
                }
            } else if (edit instanceof WorkbenchAction.RemoveNativeSelectorEntry value) {
                if (value.kind() == WorkbenchAction.NativeSelectorKind.MATCHING) {
                    requireSupported(matchingSelectorEditable, "matching selector");
                    nextMatching = matchingSelector.remove(value.index());
                    changed.put("matchBlocks", nextMatching.serializedValue());
                    nextFields = replaceFieldIfPresent(fields, "matchBlocks", nextMatching.serializedValue().orElse(""));
                } else if (connectionSelectorEditable) {
                    nextConnection = connectionSelector.remove(value.index());
                    changed.put("connectBlocks", nextConnection.serializedValue());
                    nextFields = replaceFieldIfPresent(fields, "connectBlocks", nextConnection.serializedValue().orElse(""));
                } else {
                    requireSupported(athenaConnectionEditable, "Athena connection");
                    if (value.index() >= nextConnection.entries().size()) {
                        throw new IndexOutOfBoundsException(value.index());
                    }
                    nextConnection = NativeBlockSelectorField.parse(false, "", MinecraftNativeBlockSelectorResolver.DEFAULT_ONLY);
                    nextAthena = AthenaConnection.SAME_BLOCK;
                    changed.put("connect_to", Optional.of(athenaMode("sameBlock")));
                    nextFields = replaceFieldIfPresent(fields, "connect_to", athenaMode("sameBlock"));
                }
            } else if (edit instanceof WorkbenchAction.MoveNativeSelectorEntry value) {
                if (value.kind() == WorkbenchAction.NativeSelectorKind.MATCHING) {
                    requireSupported(matchingSelectorEditable, "matching selector");
                    nextMatching = matchingSelector.move(value.index(), value.delta());
                    changed.put("matchBlocks", nextMatching.serializedValue());
                    nextFields = replaceFieldIfPresent(
                            fields, "matchBlocks", nextMatching.serializedValue().orElse(""));
                } else {
                    requireSupported(connectionSelectorEditable, "connection selector");
                    nextConnection = connectionSelector.move(value.index(), value.delta());
                    changed.put("connectBlocks", nextConnection.serializedValue());
                    nextFields = replaceFieldIfPresent(
                            fields, "connectBlocks", nextConnection.serializedValue().orElse(""));
                }
            } else if (edit instanceof WorkbenchAction.ToggleNativeSelectorProperty value) {
                NativeBlockSelectorField source = value.kind() == WorkbenchAction.NativeSelectorKind.MATCHING
                        ? matchingSelector : connectionSelector;
                requireSupported(value.kind() == WorkbenchAction.NativeSelectorKind.MATCHING
                        ? matchingSelectorEditable : connectionSelectorEditable, "selector properties");
                var entry = source.entries().get(value.index());
                var facts = entry.blockId().flatMap(MinecraftNativeBlockSelectorResolver.DEFAULT_ONLY::resolve)
                        .orElseThrow(() -> new UnsupportedOperationException("opaque selector entries are read-only"));
                NativeBlockSelectorField result = source.replace(
                        value.index(), entry.toggle(facts, value.propertyName(), value.value()));
                if (value.kind() == WorkbenchAction.NativeSelectorKind.MATCHING) {
                    nextMatching = result;
                    changed.put("matchBlocks", result.serializedValue());
                    nextFields = replaceFieldIfPresent(
                            fields, "matchBlocks", result.serializedValue().orElse(""));
                } else {
                    nextConnection = result;
                    changed.put("connectBlocks", result.serializedValue());
                    nextFields = replaceFieldIfPresent(
                            fields, "connectBlocks", result.serializedValue().orElse(""));
                }
            } else if (edit instanceof WorkbenchAction.CycleAthenaConnection) {
                requireSupported(athenaConnectionEditable, "Athena connection");
                nextAthena = switch (athenaConnection) {
                    case SAME_BLOCK -> AthenaConnection.SAME_STATE;
                    case SAME_STATE -> targetBlockId.isPresent() ? AthenaConnection.STATE : AthenaConnection.SAME_BLOCK;
                    case STATE -> AthenaConnection.SAME_BLOCK;
                    case CUSTOM -> throw new UnsupportedOperationException("custom Athena connection is read-only");
                };
                String token;
                if (nextAthena == AthenaConnection.STATE) {
                    String blockId = MinecraftNativeBlockSelectorResolver.requireSelectableBlockId(
                            targetBlockId.orElseThrow(), "target block");
                    nextConnection = NativeBlockSelectorField.registeredBlocks(
                            true, List.of(blockId), MinecraftNativeBlockSelectorResolver.DEFAULT_ONLY);
                    token = athenaState(blockId);
                } else {
                    nextConnection = NativeBlockSelectorField.parse(false, "", MinecraftNativeBlockSelectorResolver.DEFAULT_ONLY);
                    token = athenaMode(nextAthena == AthenaConnection.SAME_STATE ? "sameState" : "sameBlock");
                }
                changed.put("connect_to", Optional.of(token));
                nextFields = replaceFieldIfPresent(fields, "connect_to", token);
            }
            return with(nextFields, nextEntryId, nextMatching, nextConnection, nextFaces,
                    nextBasis, nextLayer, nextTint, nextAthena, changed);
        }

        private Field requireEditableField(String id) {
            Field field = fields.stream().filter(candidate -> candidate.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("NATIVE_PROPERTY_UNKNOWN:" + id));
            requireSupported(field.editable(), id);
            return field;
        }

        private static void requireSupported(boolean supported, String label) {
            if (!supported) throw new UnsupportedOperationException(label + " is read-only");
        }

        private static Optional<String> optionalText(String value) {
            String trimmed = Objects.requireNonNull(value, "value").trim();
            return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
        }

        private static List<Field> replaceField(List<Field> source, String id, String value) {
            boolean[] found = {false};
            List<Field> result = source.stream().map(field -> {
                if (!field.id().equals(id)) return field;
                found[0] = true;
                return new Field(field.id(), field.labelKey(), value, field.options(), field.editable());
            }).toList();
            if (!found[0]) throw new IllegalArgumentException("NATIVE_PROPERTY_UNKNOWN:" + id);
            return result;
        }

        private static List<Field> replaceFieldIfPresent(List<Field> source, String id, String value) {
            return source.stream().map(field -> field.id().equals(id)
                    ? new Field(field.id(), field.labelKey(), value, field.options(), field.editable())
                    : field).toList();
        }

        private static String athenaMode(String mode) {
            JsonObject object = new JsonObject();
            object.addProperty("type", mode);
            return object.toString();
        }

        private static String athenaState(String blockId) {
            JsonObject object = new JsonObject();
            object.addProperty("type", "state");
            object.addProperty("block", blockId);
            return object.toString();
        }

        public enum ValueEncoding {
            PROPERTIES {
                @Override String encode(String value) { return escapePropertyValue(value); }
            },
            JSON {
                @Override String encode(String value) { return new JsonPrimitive(value).toString(); }
            };

            abstract String encode(String value);

            private static String escapePropertyValue(String value) {
                StringBuilder result = new StringBuilder();
                for (int index = 0; index < value.length(); index++) {
                    char character = value.charAt(index);
                    switch (character) {
                        case '\\' -> result.append("\\\\");
                        case '\t' -> result.append("\\t");
                        case '\n' -> result.append("\\n");
                        case '\r' -> result.append("\\r");
                        case '\f' -> result.append("\\f");
                        case ' ' -> result.append(index == 0 ? "\\ " : " ");
                        default -> result.append(character);
                    }
                }
                return result.toString();
            }
        }

        public record Field(
                String id,
                String labelKey,
                String valueToken,
                List<Option> options,
                boolean editable) {
            public Field {
                requireText(id, "field id");
                requireText(labelKey, "field label key");
                valueToken = Objects.requireNonNull(valueToken, "valueToken");
                options = List.copyOf(Objects.requireNonNull(options, "options"));
            }
        }

        public record Option(String token, String labelKey) {
            public Option {
                token = Objects.requireNonNull(token, "token");
                requireText(labelKey, "option label key");
            }
        }

        public enum ConnectionBasis { BLOCK, TILE, STATE }
        public enum RenderLayer { CUTOUT, TRANSLUCENT }
        public enum AthenaConnection { SAME_BLOCK, SAME_STATE, STATE, CUSTOM }

        private static Map<String, String> immutableOrderedMap(
                Map<String, String> values, String label) {
            LinkedHashMap<String, String> copy = new LinkedHashMap<>();
            Objects.requireNonNull(values, label).forEach((key, value) -> {
                requireText(key, label + " key");
                copy.put(key, Objects.requireNonNull(value, label + " value"));
            });
            return Collections.unmodifiableMap(copy);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
