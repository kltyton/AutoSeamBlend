package com.kltyton.autoseamblend.authoring.property;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.kltyton.autoseamblend.authoring.selector.NativeBlockSelectorEntry;
import com.kltyton.autoseamblend.authoring.selector.NativeBlockSelectorFacts;
import com.kltyton.autoseamblend.authoring.selector.NativeBlockSelectorField;
import com.kltyton.autoseamblend.authoring.selector.NativeBlockSelectorResolver;
import com.kltyton.autoseamblend.authoring.export.NativeDocumentSnapshot;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.geometry.WorldDirection;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * 中文：把当前引擎家族的原生文档投影为可视化属性状态；未知字段仍由原生文档合并器保留。
 *
 * English:
 * Projects the current engine family's native document into visual property
 * state. Unknown fields remain preserved by the native-document merger.
 */
public final class NativePropertyDocument {
    private static final Set<WorldDirection> ALL_FACES =
            Collections.unmodifiableSet(
                    EnumSet.allOf(WorldDirection.class));

    private final EngineFamily family;
    private final Optional<String> targetBlockId;
    private final String documentPath;
    private final String sourceDocumentPath;
    private final byte[] sourceDocument;
    private final Map<String, byte[]> companionDocuments;
    private final NativeBlockSelectorField matchingSelector;
    private final NativeBlockSelectorField connectionSelector;
    private final String entryId;
    private final Set<WorldDirection> faces;
    private final ConnectionBasis connectionBasis;
    private final RenderLayer renderLayer;
    private final String tintBlockId;
    private final AthenaConnection athenaConnection;
    private final ConnectionMethod authoringMethod;
    private final boolean authoringCompatibility;
    private final Map<String, String> nativeDetails;
    private final EnumSet<Field> changed;

    private NativePropertyDocument(
            EngineFamily family,
            Optional<String> targetBlockId,
            String documentPath,
            String sourceDocumentPath,
            byte[] sourceDocument,
            Map<String, byte[]> companionDocuments,
            NativeBlockSelectorField matchingSelector,
            NativeBlockSelectorField connectionSelector,
            String entryId,
            Set<WorldDirection> faces,
            ConnectionBasis connectionBasis,
            RenderLayer renderLayer,
            String tintBlockId,
            AthenaConnection athenaConnection,
            ConnectionMethod authoringMethod,
            boolean authoringCompatibility,
            Map<String, String> nativeDetails,
            Set<Field> changed) {
        this.family = Objects.requireNonNull(
                family,
                "family");
        this.targetBlockId = Objects.requireNonNull(
                targetBlockId,
                "targetBlockId");
        this.documentPath = path(
                documentPath,
                "documentPath");
        this.sourceDocumentPath = path(
                sourceDocumentPath,
                "sourceDocumentPath");
        this.sourceDocument = Objects.requireNonNull(
                        sourceDocument,
                        "sourceDocument")
                .clone();
        this.companionDocuments = copyDocuments(
                companionDocuments);
        this.matchingSelector = Objects.requireNonNull(
                matchingSelector,
                "matchingSelector");
        this.connectionSelector = Objects.requireNonNull(
                connectionSelector,
                "connectionSelector");
        this.entryId = normalizeEntryId(entryId);
        EnumSet<WorldDirection> faceCopy =
                faces.isEmpty()
                        ? EnumSet.noneOf(WorldDirection.class)
                        : EnumSet.copyOf(faces);
        this.faces = Collections.unmodifiableSet(
                faceCopy);
        this.connectionBasis = Objects.requireNonNull(
                connectionBasis,
                "connectionBasis");
        this.renderLayer = Objects.requireNonNull(
                renderLayer,
                "renderLayer");
        this.tintBlockId = tintBlockId == null ? "" : tintBlockId;
        this.athenaConnection = Objects.requireNonNull(
                athenaConnection,
                "athenaConnection");
        this.authoringMethod = Objects.requireNonNull(
                authoringMethod,
                "authoringMethod");
        this.authoringCompatibility =
                authoringCompatibility;
        this.nativeDetails = Collections.unmodifiableMap(
                new LinkedHashMap<>(
                        Objects.requireNonNull(
                                nativeDetails,
                                "nativeDetails")));
        this.changed = changed.isEmpty()
                ? EnumSet.noneOf(Field.class)
                : EnumSet.copyOf(changed);
    }

    /**
     * 中文：从已捕获的原生主文档与伴随文档构造不可变属性状态；不执行任何 I/O。
     *
     * English: Builds immutable property state from a captured native principal
     * and companion documents without performing I/O.
     */
    public static NativePropertyDocument parse(
            EngineFamily family,
            Optional<String> targetBlockId,
            String documentPath,
            String sourceDocumentPath,
            byte[] sourceDocument,
            Map<String, byte[]> companionDocuments,
            ConnectionMethod fallbackMethod,
            boolean fallbackCompatibility,
            NativeBlockSelectorResolver selectorResolver)
            throws IOException {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(targetBlockId, "targetBlockId");
        Objects.requireNonNull(fallbackMethod, "fallbackMethod");
        Objects.requireNonNull(selectorResolver, "selectorResolver");
        Optional<String> receiver = targetBlockId.map(value ->
                blockId(value, "targetBlockId", selectorResolver));
        return switch (family) {
            case MCPATCHER -> fromMCPatcher(
                    receiver,
                    documentPath,
                    sourceDocumentPath,
                    sourceDocument,
                    companionDocuments,
                    fallbackMethod,
                    fallbackCompatibility,
                    selectorResolver);
            default -> throw new IOException(
                    "LOADER_EXCLUSIVE_PROPERTY_DOCUMENT_REQUIRES_ADAPTER");
            case FUSION -> fromFusion(
                    receiver,
                    documentPath,
                    sourceDocumentPath,
                    sourceDocument,
                    companionDocuments,
                    fallbackMethod,
                    fallbackCompatibility,
                    selectorResolver);
            case ATHENA -> fromAthena(
                    receiver,
                    documentPath,
                    sourceDocumentPath,
                    sourceDocument,
                    companionDocuments,
                    fallbackMethod,
                    fallbackCompatibility,
                    selectorResolver);
        };
    }
    public EngineFamily family() {
        return family;
    }

    public Optional<String> targetBlockId() {
        return targetBlockId;
    }

    public ConnectionMethod authoringMethod() {
        return authoringMethod;
    }

    public boolean authoringCompatibility() {
        return authoringCompatibility;
    }

    public String documentPath() {
        return documentPath;
    }

    public String sourceDocumentPath() {
        return sourceDocumentPath;
    }

    /**
     * 中文：返回属性面板实际读取的原生主文档字节，用于显式导出时冻结当前来源。
     *
     * English:
     * Returns the native principal bytes actually read by the property panel so
     * an explicit export can freeze the current source.
     */
    public byte[] sourceDocument() {
        return sourceDocument.clone();
    }

    public Map<String, byte[]> companionDocuments() {
        return copyDocuments(companionDocuments);
    }

    public List<String> matchingBlocks() {
        return matchingSelector.blockIds();
    }

    public SelectorPresence matchingPresence() {
        return selectorPresence(
                matchingSelector);
    }

    public List<String> connectionBlocks() {
        return connectionSelector.blockIds();
    }

    public SelectorPresence connectionPresence() {
        return selectorPresence(
                connectionSelector);
    }

    public NativeBlockSelectorField matchingSelector() {
        return matchingSelector;
    }

    public NativeBlockSelectorField connectionSelector() {
        return connectionSelector;
    }

    public Optional<String> explicitEntryId() {
        return entryId.isEmpty()
                ? Optional.empty()
                : Optional.of(entryId);
    }

    /**
     * 中文：显示身份依次使用显式 id、connectBlocks、matchBlocks，最后才使用文档路径。
     *
     * English:
     * Resolves display identity from explicit id, connectBlocks, matchBlocks,
     * and only then the document path.
     */
    public String displayEntryId() {
        return explicitEntryId()
                .or(() -> connectionSelector
                        .firstDisplayValue())
                .or(() -> matchingSelector
                        .firstDisplayValue())
                .orElse(sourceDocumentPath);
    }

    public Set<WorldDirection> faces() {
        return faces;
    }

    public ConnectionBasis connectionBasis() {
        return connectionBasis;
    }

    public RenderLayer renderLayer() {
        return renderLayer;
    }

    public String tintBlockId() {
        return tintBlockId;
    }

    public AthenaConnection athenaConnection() {
        return athenaConnection;
    }

    public Map<String, String> nativeDetails() {
        return nativeDetails;
    }

    /**
     * 中文：返回 MCPatcher 原生文档当前声明的连接纹理槽位表达式。
     *
     * English:
     * Returns the connected-texture slot expression currently declared by the
     * MCPatcher native document.
     */
    public String tilesExpression() {
        return nativeDetails.getOrDefault(
                "tiles",
                "");
    }

    public boolean connectionEditingSupported() {
        return family == EngineFamily.MCPATCHER;
    }

    public boolean matchingEditingSupported() {
        return family == EngineFamily.MCPATCHER;
    }

    public boolean athenaConnectionEditingSupported() {
        return family == EngineFamily.ATHENA
                && athenaConnection != AthenaConnection.CUSTOM;
    }

    public boolean dirty() {
        return !changed.isEmpty();
    }

    public NativePropertyDocument toggleFace(
            WorldDirection direction) {
        requireMCPatcher();
        EnumSet<WorldDirection> next =
                faces.isEmpty()
                        ? EnumSet.noneOf(WorldDirection.class)
                        : EnumSet.copyOf(faces);
        if (!next.remove(
                Objects.requireNonNull(
                        direction,
                        "direction"))) {
            next.add(direction);
        }
        return copy(
                matchingSelector,
                connectionSelector,
                entryId,
                next,
                connectionBasis,
                renderLayer,
                tintBlockId,
                athenaConnection,
                Field.FACES);
    }

    public NativePropertyDocument cycleConnectionBasis() {
        requireMCPatcher();
        ConnectionBasis[] values =
                ConnectionBasis.values();
        ConnectionBasis next =
                values[(connectionBasis.ordinal() + 1)
                        % values.length];
        return copy(
                matchingSelector,
                connectionSelector,
                entryId,
                faces,
                next,
                renderLayer,
                tintBlockId,
                athenaConnection,
                Field.CONNECTION_BASIS);
    }

    public NativePropertyDocument cycleRenderLayer() {
        requireMCPatcher();
        RenderLayer next =
                renderLayer == RenderLayer.CUTOUT
                        ? RenderLayer.TRANSLUCENT
                        : RenderLayer.CUTOUT;
        return copy(
                matchingSelector,
                connectionSelector,
                entryId,
                faces,
                connectionBasis,
                next,
                tintBlockId,
                athenaConnection,
                Field.RENDER_LAYER);
    }

    public NativePropertyDocument addConnectionBlock(
            String blockId,
            NativeBlockSelectorResolver selectorResolver) {
        String canonical = blockId(
                blockId,
                "connectionBlockId",
                selectorResolver);
        if (family == EngineFamily.ATHENA) {
            requireEditableAthenaConnection();
            return copy(
                    matchingSelector,
                    NativeBlockSelectorField
                            .registeredBlocks(
                                    true,
                                    List.of(canonical),
                                    selectorResolver),
                    entryId,
                    faces,
                    connectionBasis,
                    renderLayer,
                    tintBlockId,
                    AthenaConnection.STATE,
                    Field.ATHENA_CONNECTION);
        }
        requireConnectionBlocksEditable();
        return copy(
                matchingSelector,
                connectionSelector.addBlock(
                        canonical,
                        selectorResolver),
                entryId,
                faces,
                connectionBasis,
                renderLayer,
                tintBlockId,
                athenaConnection,
                Field.CONNECTION_BLOCKS);
    }

    public NativePropertyDocument removeConnectionBlock(
            String blockId,
            NativeBlockSelectorResolver selectorResolver) {
        if (family == EngineFamily.ATHENA) {
            requireEditableAthenaConnection();
            return copy(
                    matchingSelector,
                    NativeBlockSelectorField
                            .registeredBlocks(
                                    true,
                                    List.of(),
                                    selectorResolver),
                    entryId,
                    faces,
                    connectionBasis,
                    renderLayer,
                    tintBlockId,
                    AthenaConnection.SAME_BLOCK,
                    Field.ATHENA_CONNECTION);
        }
        requireConnectionBlocksEditable();
        int index = selectorIndex(
                connectionSelector,
                blockId);
        if (index < 0) {
            return this;
        }
        return copy(
                matchingSelector,
                connectionSelector.remove(index),
                entryId,
                faces,
                connectionBasis,
                renderLayer,
                tintBlockId,
                athenaConnection,
                Field.CONNECTION_BLOCKS);
    }

    public NativePropertyDocument cycleAthenaConnection(
            NativeBlockSelectorResolver selectorResolver) {
        requireEditableAthenaConnection();
        AthenaConnection next = switch (athenaConnection) {
            case SAME_BLOCK -> AthenaConnection.SAME_STATE;
            case SAME_STATE -> targetBlockId.isPresent()
                    ? AthenaConnection.STATE
                    : AthenaConnection.SAME_BLOCK;
            case STATE -> AthenaConnection.SAME_BLOCK;
            case CUSTOM -> throw new IllegalStateException(
                    "custom Athena connection cannot be changed visually");
        };
        List<String> nextBlocks =
                next == AthenaConnection.STATE
                        ? targetBlockId.stream()
                                .toList()
                        : List.of();
        return copy(
                matchingSelector,
                NativeBlockSelectorField
                        .registeredBlocks(
                                true,
                                nextBlocks,
                                selectorResolver),
                entryId,
                faces,
                connectionBasis,
                renderLayer,
                tintBlockId,
                next,
                Field.ATHENA_CONNECTION);
    }

    public NativePropertyDocument withTintBlock(
            String blockId,
            NativeBlockSelectorResolver selectorResolver) {
        requireMCPatcher();
        String canonical =
                blockId == null
                                || blockId.isBlank()
                        ? ""
                        : blockId(
                                blockId,
                                "tintBlockId",
                                selectorResolver);
        return copy(
                matchingSelector,
                connectionSelector,
                entryId,
                faces,
                connectionBasis,
                renderLayer,
                canonical,
                athenaConnection,
                Field.TINT_BLOCK);
    }

    /**
     * 中文：绘画工作区首次产生像素修改时，把生成槽位显式写回原生 tiles 字段。
     *
     * English:
     * Writes the generated slot domain back to the native tiles field when the
     * paint workspace first produces a pixel edit.
     */
    public NativePropertyDocument withTilesExpression(
            String expression) {
        requireMCPatcher();
        String normalized = Objects.requireNonNull(
                        expression,
                        "expression")
                .trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "tiles expression must not be blank");
        }
        if (normalized.equals(
                tilesExpression())) {
            return this;
        }
        LinkedHashMap<String, String> details =
                new LinkedHashMap<>(nativeDetails);
        details.put("tiles", normalized);
        EnumSet<Field> nextChanged =
                changed.isEmpty()
                        ? EnumSet.noneOf(Field.class)
                        : EnumSet.copyOf(changed);
        nextChanged.add(Field.TILES);
        return new NativePropertyDocument(
                family,
                targetBlockId,
                documentPath,
                sourceDocumentPath,
                sourceDocument,
                companionDocuments,
                matchingSelector,
                connectionSelector,
                entryId,
                faces,
                connectionBasis,
                renderLayer,
                tintBlockId,
                athenaConnection,
                authoringMethod,
                authoringCompatibility,
                details,
                nextChanged);
    }

    public Optional<NativePropertyPatch> patch() {
        if (changed.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Optional<String>> values =
                patchValues();
        return values.isEmpty()
                ? Optional.empty()
                : Optional.of(patch(values));
    }

    /**
     * 中文：为显式保存捕获完整原生主文档，即使属性面板没有改动也能创建自定义目标路径。
     *
     * English:
     * Captures the complete native principal for an explicit save so a custom
     * target path can be created even when no property field changed.
     */
    public NativePropertyPatch managedPatch() {
        return patch(patchValues());
    }

    /** 中文：冻结为现有 Common 导出快照。 / English: Freezes this state into the existing Common export snapshot. */
    public NativeDocumentSnapshot snapshot() {
        return new NativeDocumentSnapshot(
                family,
                sourceDocumentPath,
                sourceDocument,
                companionDocuments,
                patch(),
                displayEntryId(),
                authoringMethod,
                authoringCompatibility);
    }

    private NativePropertyPatch patch(
            Map<String, Optional<String>> values) {
        return new NativePropertyPatch(
                family,
                sourceDocumentPath,
                documentPath,
                sourceDocument,
                values);
    }

    private Map<String, Optional<String>>
            patchValues() {
        LinkedHashMap<String, Optional<String>>
                values = new LinkedHashMap<>();
        if (changed.contains(
                Field.MATCHING_BLOCKS)) {
            values.put(
                    "matchBlocks",
                    matchingSelector
                            .serializedValue());
        }
        if (changed.contains(
                Field.CONNECTION_BLOCKS)) {
            values.put(
                    "connectBlocks",
                    connectionSelector
                            .serializedValue());
        }
        if (changed.contains(Field.ENTRY_ID)) {
            values.put(
                    "id",
                    explicitEntryId()
                            .map(value ->
                                    family
                                                    == EngineFamily.MCPATCHER
                                            ? propertyValue(value)
                                            : new JsonPrimitive(
                                                            value)
                                                    .toString()));
        }
        if (changed.contains(Field.FACES)) {
            values.put(
                    "faces",
                    optional(faces(faces)));
        }
        if (changed.contains(
                Field.CONNECTION_BASIS)) {
            values.put(
                    "connect",
                    Optional.of(
                            connectionBasis.serializedName()));
        }
        if (changed.contains(
                Field.RENDER_LAYER)) {
            values.put(
                    "layer",
                    Optional.of(
                            renderLayer.serializedName()));
        }
        if (changed.contains(
                Field.TINT_BLOCK)) {
            values.put(
                    "tintBlock",
                    optional(tintBlockId));
        }
        if (changed.contains(Field.TILES)) {
            values.put(
                    "tiles",
                    Optional.of(
                            tilesExpression()));
        }
        if (changed.contains(
                Field.ATHENA_CONNECTION)) {
            values.put(
                    "connect_to",
                    Optional.of(
                            athenaConnectionJson()));
        }
        if (changed.contains(
                Field.AUTHORING_METHOD)) {
            values.put(
                    "method",
                    Optional.of(
                            family == EngineFamily.MCPATCHER
                                    ? authoringMethod
                                            .serializedName()
                                    : new JsonPrimitive(
                                                    authoringMethod
                                                            .serializedName())
                                            .toString()));
        }
        if (changed.contains(
                Field.AUTHORING_COMPATIBILITY)) {
            values.put(
                    "compatibility",
                    Optional.of(Boolean.toString(
                            authoringCompatibility)));
        }
        return Collections.unmodifiableMap(values);
    }

    private NativePropertyDocument copy(
            NativeBlockSelectorField
                    nextMatchingSelector,
            NativeBlockSelectorField
                    nextConnectionSelector,
            String nextEntryId,
            Set<WorldDirection> nextFaces,
            ConnectionBasis nextBasis,
            RenderLayer nextLayer,
            String nextTintBlock,
            AthenaConnection nextAthenaConnection,
            Field field) {
        EnumSet<Field> nextChanged =
                changed.isEmpty()
                        ? EnumSet.noneOf(Field.class)
                        : EnumSet.copyOf(changed);
        nextChanged.add(field);
        return new NativePropertyDocument(
                family,
                targetBlockId,
                documentPath,
                sourceDocumentPath,
                sourceDocument,
                companionDocuments,
                nextMatchingSelector,
                nextConnectionSelector,
                nextEntryId,
                nextFaces,
                nextBasis,
                nextLayer,
                nextTintBlock,
                nextAthenaConnection,
                authoringMethod,
                authoringCompatibility,
                nativeDetails,
                nextChanged);
    }

    private void requireMCPatcher() {
        if (family != EngineFamily.MCPATCHER) {
            throw new UnsupportedOperationException(
                    "native property is read-only for "
                            + family.formatId());
        }
    }

    private void requireConnectionBlocksEditable() {
        requireMCPatcher();
        if (!connectionEditingSupported()) {
            throw new UnsupportedOperationException(
                    "opaque connectBlocks must remain lossless");
        }
    }

    private void requireMatchingBlocksEditable() {
        requireMCPatcher();
        if (!matchingEditingSupported()) {
            throw new UnsupportedOperationException(
                    "opaque matchBlocks must remain lossless");
        }
    }

    public NativePropertyDocument addMatchingBlock(
            String blockId,
            NativeBlockSelectorResolver selectorResolver) {
        requireMatchingBlocksEditable();
        String canonical = blockId(
                blockId,
                "matchingBlockId",
                selectorResolver);
        return copy(
                matchingSelector.addBlock(
                        canonical,
                        selectorResolver),
                connectionSelector,
                entryId,
                faces,
                connectionBasis,
                renderLayer,
                tintBlockId,
                athenaConnection,
                Field.MATCHING_BLOCKS);
    }

    public NativePropertyDocument removeMatchingBlock(
            String blockId) {
        requireMatchingBlocksEditable();
        int index = selectorIndex(
                matchingSelector,
                blockId);
        if (index < 0) {
            return this;
        }
        return copy(
                matchingSelector.remove(index),
                connectionSelector,
                entryId,
                faces,
                connectionBasis,
                renderLayer,
                tintBlockId,
                athenaConnection,
                Field.MATCHING_BLOCKS);
    }

    public NativePropertyDocument removeMatchingEntry(
            int index) {
        requireMatchingBlocksEditable();
        return copy(
                matchingSelector.remove(index),
                connectionSelector,
                entryId,
                faces,
                connectionBasis,
                renderLayer,
                tintBlockId,
                athenaConnection,
                Field.MATCHING_BLOCKS);
    }

    public NativePropertyDocument removeConnectionEntry(
            int index) {
        requireConnectionBlocksEditable();
        return copy(
                matchingSelector,
                connectionSelector.remove(index),
                entryId,
                faces,
                connectionBasis,
                renderLayer,
                tintBlockId,
                athenaConnection,
                Field.CONNECTION_BLOCKS);
    }

    public NativePropertyDocument toggleMatchingProperty(
            int index,
            String propertyName,
            String value,
            NativeBlockSelectorResolver selectorResolver) {
        requireMatchingBlocksEditable();
        NativeBlockSelectorEntry updated =
                matchingSelector.entries()
                        .get(index)
                        .toggle(
                                selectorFacts(
                                        matchingSelector.entries()
                                                .get(index),
                                        selectorResolver),
                                propertyName,
                                value);
        return copy(
                matchingSelector.replace(
                        index,
                        updated),
                connectionSelector,
                entryId,
                faces,
                connectionBasis,
                renderLayer,
                tintBlockId,
                athenaConnection,
                Field.MATCHING_BLOCKS);
    }

    public NativePropertyDocument toggleConnectionProperty(
            int index,
            String propertyName,
            String value,
            NativeBlockSelectorResolver selectorResolver) {
        requireConnectionBlocksEditable();
        NativeBlockSelectorEntry updated =
                connectionSelector.entries()
                        .get(index)
                        .toggle(
                                selectorFacts(
                                        connectionSelector.entries()
                                                .get(index),
                                        selectorResolver),
                                propertyName,
                                value);
        return copy(
                matchingSelector,
                connectionSelector.replace(
                        index,
                        updated),
                entryId,
                faces,
                connectionBasis,
                renderLayer,
                tintBlockId,
                athenaConnection,
                Field.CONNECTION_BLOCKS);
    }

    public NativePropertyDocument moveMatchingEntry(
            int index,
            int delta) {
        requireMatchingBlocksEditable();
        NativeBlockSelectorField next =
                matchingSelector.move(
                        index,
                        delta);
        return next == matchingSelector
                ? this
                : copy(
                        next,
                        connectionSelector,
                        entryId,
                        faces,
                        connectionBasis,
                        renderLayer,
                        tintBlockId,
                        athenaConnection,
                        Field.MATCHING_BLOCKS);
    }

    public NativePropertyDocument moveConnectionEntry(
            int index,
            int delta) {
        requireConnectionBlocksEditable();
        NativeBlockSelectorField next =
                connectionSelector.move(
                        index,
                        delta);
        return next == connectionSelector
                ? this
                : copy(
                        matchingSelector,
                        next,
                        entryId,
                        faces,
                        connectionBasis,
                        renderLayer,
                        tintBlockId,
                        athenaConnection,
                        Field.CONNECTION_BLOCKS);
    }

    public NativePropertyDocument withEntryId(
            String value) {
        String normalized = normalizeEntryId(
                value);
        if (entryId.equals(normalized)) {
            return this;
        }
        return copy(
                matchingSelector,
                connectionSelector,
                normalized,
                faces,
                connectionBasis,
                renderLayer,
                tintBlockId,
                athenaConnection,
                Field.ENTRY_ID);
    }

    /**
     * 中文：更新四格式共享的创作方法扩展；只改变扩展值，不重写任何原生选择器语义。
     *
     * English:
     * Updates the authoring-method extension shared by all four formats
     * without rewriting native selector semantics.
     */
    public NativePropertyDocument withAuthoringMethod(
            ConnectionMethod value) {
        ConnectionMethod normalized =
                Objects.requireNonNull(
                        value,
                        "value");
        if (authoringMethod == normalized) {
            return this;
        }
        return copyAuthoring(
                normalized,
                authoringCompatibility,
                Field.AUTHORING_METHOD);
    }

    /**
     * 中文：更新四格式共享的创作兼容策略扩展，并保留原生文档其他内容。
     *
     * English:
     * Updates the authoring compatibility extension shared by all four
     * formats while retaining every other native-document field.
     */
    public NativePropertyDocument
            withAuthoringCompatibility(
                    boolean value) {
        if (authoringCompatibility == value) {
            return this;
        }
        return copyAuthoring(
                authoringMethod,
                value,
                Field.AUTHORING_COMPATIBILITY);
    }

    private NativePropertyDocument copyAuthoring(
            ConnectionMethod nextMethod,
            boolean nextCompatibility,
            Field field) {
        EnumSet<Field> nextChanged =
                changed.isEmpty()
                        ? EnumSet.noneOf(Field.class)
                        : EnumSet.copyOf(changed);
        nextChanged.add(field);
        return new NativePropertyDocument(
                family,
                targetBlockId,
                documentPath,
                sourceDocumentPath,
                sourceDocument,
                companionDocuments,
                matchingSelector,
                connectionSelector,
                entryId,
                faces,
                connectionBasis,
                renderLayer,
                tintBlockId,
                athenaConnection,
                nextMethod,
                nextCompatibility,
                nativeDetails,
                nextChanged);
    }

    private void requireEditableAthenaConnection() {
        if (!athenaConnectionEditingSupported()) {
            throw new UnsupportedOperationException(
                    "complex Athena connection is preserved as read-only");
        }
    }

    private String athenaConnectionJson() {
        JsonObject condition = new JsonObject();
        condition.addProperty(
                "type",
                athenaConnection.serializedName());
        if (athenaConnection == AthenaConnection.STATE) {
            List<String> connectionBlocks =
                    connectionBlocks();
            condition.addProperty(
                    "block",
                    connectionBlocks.isEmpty()
                            ? targetBlockId.orElseThrow(
                                    () -> new IllegalStateException(
                                            "Athena state connection requires a receiver or explicit connection block"))
                            : connectionBlocks.getFirst());
        }
        return condition.toString();
    }

    private static NativePropertyDocument
            fromMCPatcher(
                    Optional<String> receiver,
                    String documentPath,
                    String sourcePath,
                    byte[] source,
                    Map<String, byte[]> companions,
                    ConnectionMethod fallbackMethod,
                    boolean fallbackCompatibility,
                    NativeBlockSelectorResolver selectorResolver)
                    throws IOException {
        Properties properties =
                new Properties();
        try {
            properties.load(new StringReader(
                    decode(source)));
        } catch (RuntimeException exception) {
            throw new IOException(
                    "MCPATCHER_DOCUMENT_INVALID",
                    exception);
        }
        NativeBlockSelectorField matching =
                NativeBlockSelectorField.parse(
                        properties.containsKey(
                                "matchBlocks"),
                        properties.getProperty(
                                "matchBlocks"),
                        selectorResolver);
        NativeBlockSelectorField connecting =
                NativeBlockSelectorField.parse(
                        properties.containsKey(
                                "connectBlocks"),
                        properties.getProperty(
                                "connectBlocks"),
                        selectorResolver);
        EnumSet<WorldDirection> faces = parseFaces(
                properties.getProperty(
                        "faces"));
        ConnectionBasis basis =
                ConnectionBasis.parse(
                        properties.getProperty(
                                "connect"))
                        .orElse(
                                ConnectionBasis.BLOCK);
        RenderLayer layer =
                RenderLayer.parse(
                        properties.getProperty(
                                "layer"))
                        .orElse(
                                RenderLayer.CUTOUT);
        LinkedHashMap<String, String> details =
                new LinkedHashMap<>();
        details.put(
                "format",
                "MCPatcher .properties");
        details.put(
                "tiles",
                properties.getProperty(
                        "tiles",
                        ""));
        details.put(
                "matchTiles",
                properties.getProperty(
                        "matchTiles",
                        ""));
        details.put(
                "matchBlocks",
                properties.getProperty(
                        "matchBlocks",
                        ""));
        details.put(
                "connectBlocks",
                properties.getProperty(
                        "connectBlocks",
                        ""));
        details.put(
                "id",
                properties.getProperty(
                        "id",
                        ""));
        ConnectionMethod method =
                ConnectionMethod.parse(
                                properties.getProperty(
                                        "method"))
                        .orElse(fallbackMethod);
        boolean compatibility = booleanValue(
                properties.getProperty(
                        "compatibility"),
                fallbackCompatibility);
        String tintBlock = properties.getProperty(
                "tintBlock",
                "");
        if (!tintBlock.isBlank()) {
            tintBlock = blockId(
                    tintBlock,
                    "tintBlockId",
                    selectorResolver);
        }
        return new NativePropertyDocument(
                EngineFamily.MCPATCHER,
                receiver,
                documentPath,
                sourcePath,
                source,
                companions,
                matching,
                connecting,
                properties.getProperty(
                        "id",
                        ""),
                faces,
                basis,
                layer,
                tintBlock,
                AthenaConnection.CUSTOM,
                method,
                compatibility,
                details,
                Set.of());
    }

    private static NativePropertyDocument fromFusion(
            Optional<String> receiver,
            String documentPath,
            String sourcePath,
            byte[] source,
            Map<String, byte[]> companions,
            ConnectionMethod fallbackMethod,
            boolean fallbackCompatibility,
            NativeBlockSelectorResolver selectorResolver)
            throws IOException {
        JsonObject root = json(source);
        List<String> targets =
                strings(root.get("targets"));
        LinkedHashMap<String, String> details =
                new LinkedHashMap<>();
        details.put(
                "format",
                "Fusion model modifier");
        details.put(
                "default_model_overrides",
                primitive(
                        root.get(
                                "default_model_overrides")));
        details.put(
                "id",
                Objects.toString(
                        string(root.get("id")),
                        ""));
        return createReadOnly(
                EngineFamily.FUSION,
                receiver,
                documentPath,
                sourcePath,
                source,
                companions,
                selectorResolver,
                string(root.get("id")),
                targets,
                root.has("targets")
                        ? targets.isEmpty()
                                ? SelectorPresence.PRESENT_INVALID
                                : SelectorPresence.PRESENT_VALUES
                        : SelectorPresence.ABSENT,
                details,
                AthenaConnection.CUSTOM,
                List.of(),
                SelectorPresence.ABSENT,
                ConnectionMethod.parse(
                                string(root.get(
                                        "method")))
                        .orElse(fallbackMethod),
                booleanValue(
                        root.get("compatibility"),
                        fallbackCompatibility));
    }

    private static NativePropertyDocument fromAthena(
            Optional<String> receiver,
            String documentPath,
            String sourcePath,
            byte[] source,
            Map<String, byte[]> companions,
            ConnectionMethod fallbackMethod,
            boolean fallbackCompatibility,
            NativeBlockSelectorResolver selectorResolver)
            throws IOException {
        JsonObject root = json(source);
        LinkedHashMap<String, String> details =
                new LinkedHashMap<>();
        details.put(
                "format",
                "Athena model JSON");
        details.put(
                "loader",
                string(root.get("athena:loader")));
        details.put(
                "connect_to",
                primitive(
                        root.get("connect_to")));
        details.put(
                "id",
                Objects.toString(
                        string(root.get("id")),
                        ""));
        AthenaConnection connection =
                AthenaConnection.parse(
                        root.get("connect_to"));
        List<String> connectionBlocks =
                connection == AthenaConnection.STATE
                        ? stateConnectionBlocks(
                                root.get("connect_to"),
                                selectorResolver)
                        : List.of();
        if (connection == AthenaConnection.STATE
                && connectionBlocks.isEmpty()) {
            connection = AthenaConnection.CUSTOM;
        }
        return createReadOnly(
                EngineFamily.ATHENA,
                receiver,
                documentPath,
                sourcePath,
                source,
                companions,
                selectorResolver,
                string(root.get("id")),
                List.of(),
                SelectorPresence.ABSENT,
                details,
                connection,
                connectionBlocks,
                root.has("connect_to")
                        ? connectionBlocks.isEmpty()
                                ? SelectorPresence.PRESENT_INVALID
                                : SelectorPresence.PRESENT_VALUES
                        : SelectorPresence.ABSENT,
                ConnectionMethod.parse(
                                string(root.get(
                                        "method")))
                        .orElse(fallbackMethod),
                booleanValue(
                        root.get("compatibility"),
                        fallbackCompatibility));
    }

    /**
     * 中文：供 Loader 独占格式解析器构造公共只读属性值对象。
     * English: Lets Loader-exclusive format parsers construct the shared read-only value object.
     */
    public static NativePropertyDocument createReadOnly(
            EngineFamily family,
            Optional<String> receiver,
            String documentPath,
            String sourcePath,
            byte[] source,
            Map<String, byte[]> companions,
            NativeBlockSelectorResolver selectorResolver,
            String entryId,
            List<String> matching,
            SelectorPresence matchingPresence,
            Map<String, String> details,
            AthenaConnection athenaConnection,
            List<String> connectionBlocks,
            SelectorPresence connectionPresence,
            ConnectionMethod authoringMethod,
            boolean authoringCompatibility) {
        return new NativePropertyDocument(
                family,
                receiver,
                documentPath,
                sourcePath,
                source,
                companions,
                NativeBlockSelectorField.parse(
                        matchingPresence
                                != SelectorPresence.ABSENT,
                        String.join(" ", matching),
                        selectorResolver),
                NativeBlockSelectorField.parse(
                        connectionPresence
                                != SelectorPresence.ABSENT,
                        String.join(
                                " ",
                                connectionBlocks),
                        selectorResolver),
                entryId,
                ALL_FACES,
                ConnectionBasis.BLOCK,
                RenderLayer.CUTOUT,
                "",
                athenaConnection,
                authoringMethod,
                authoringCompatibility,
                details,
                Set.of());
    }

    private static List<String> stateConnectionBlocks(
            JsonElement encoded,
            NativeBlockSelectorResolver selectorResolver) {
        if (encoded == null
                || !encoded.isJsonObject()) {
            return List.of();
        }
        JsonObject condition =
                encoded.getAsJsonObject();
        if (condition.has("properties")) {
            return List.of();
        }
        String block = string(
                condition.get("block"));
        if (block == null) {
            return List.of();
        }
        return selectorResolver.resolve(block)
                .map(facts -> List.of(facts.blockId()))
                .orElseGet(List::of);
    }

    private static JsonObject json(
            byte[] source) throws IOException {
        try {
            JsonElement parsed =
                    JsonParser.parseString(
                            decode(source));
            if (parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
        } catch (RuntimeException exception) {
            throw new IOException(
                    "NATIVE_PROPERTY_JSON_INVALID",
                    exception);
        }
        throw new IOException(
                "NATIVE_PROPERTY_JSON_INVALID");
    }

    private static String decode(
            byte[] source) {
        return new String(
                source,
                StandardCharsets.UTF_8);
    }

    private static SelectorPresence selectorPresence(
            NativeBlockSelectorField field) {
        return switch (field.presence()) {
            case ABSENT -> SelectorPresence.ABSENT;
            case PRESENT_EMPTY ->
                    SelectorPresence.PRESENT_EMPTY;
            case PRESENT_VALUES ->
                    SelectorPresence.PRESENT_VALUES;
            case PRESENT_WITH_OPAQUE ->
                    SelectorPresence.PRESENT_INVALID;
        };
    }

    private static List<String> strings(
            JsonElement value) {
        if (value == null
                || !value.isJsonArray()) {
            return List.of();
        }
        ArrayList<String> result =
                new ArrayList<>();
        value.getAsJsonArray()
                .forEach(element -> {
                    String candidate =
                            string(element);
                    if (candidate != null
                            && !candidate.isBlank()) {
                        result.add(candidate);
                    }
                });
        return List.copyOf(result);
    }

    private static List<String> stringsOrSingle(
            JsonElement value) {
        String single = string(value);
        return single == null
                ? strings(value)
                : List.of(single);
    }

    private static boolean booleanValue(
            JsonElement value,
            boolean fallback) {
        return value != null
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive()
                                .isBoolean()
                ? value.getAsBoolean()
                : fallback;
    }

    private static boolean booleanValue(
            String value,
            boolean fallback) {
        if (value == null) {
            return fallback;
        }
        return switch (value.trim()
                .toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> fallback;
        };
    }

    private static EnumSet<WorldDirection> parseFaces(
            String value) {
        if (value == null
                || value.isBlank()) {
            return EnumSet.allOf(
                    WorldDirection.class);
        }
        EnumSet<WorldDirection> result =
                EnumSet.noneOf(WorldDirection.class);
        for (String token : value.trim()
                .split("[ ,]+")) {
            switch (token.toLowerCase(
                    Locale.ROOT)) {
                case "all" -> result.addAll(
                        ALL_FACES);
                case "sides" -> result.addAll(
                        List.of(
                                WorldDirection.NORTH,
                                WorldDirection.SOUTH,
                                WorldDirection.WEST,
                                WorldDirection.EAST));
                case "top" -> result.add(
                        WorldDirection.UP);
                case "bottom" -> result.add(
                        WorldDirection.DOWN);
                default -> {
                    try {
                        result.add(
                                WorldDirection.valueOf(
                                        token.toUpperCase(
                                                Locale.ROOT)));
                    } catch (IllegalArgumentException ignored) {
                        // 中文：未知原生面标记保持在源文档中，但不映射为可视开关。
                        // English: Unknown native face tokens stay in source but are not mapped to visual toggles.
                    }
                }
            }
        }
        return result;
    }

    private static String faces(
            Set<WorldDirection> faces) {
        if (faces.size()
                == WorldDirection.values().length) {
            return "all";
        }
        return faces.stream()
                .map(direction ->
                        direction.name()
                                .toLowerCase(
                                        Locale.ROOT))
                .toList()
                .stream()
                .reduce((left, right) ->
                        left + ' ' + right)
                .orElse("");
    }

    private static Optional<String> optional(
            String value) {
        return value == null
                        || value.isBlank()
                ? Optional.empty()
                : Optional.of(value);
    }

    private static Map<String, byte[]> copyDocuments(
            Map<String, byte[]> documents) {
        LinkedHashMap<String, byte[]> copy =
                new LinkedHashMap<>();
        Objects.requireNonNull(
                        documents,
                        "documents")
                .forEach((path, bytes) ->
                        copy.put(
                                path(
                                        path,
                                        "companion document path"),
                                Objects.requireNonNull(
                                                bytes,
                                                "companion document bytes")
                                        .clone()));
        return Collections.unmodifiableMap(copy);
    }

    private static String string(
            JsonElement value) {
        return value != null
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive()
                                .isString()
                ? value.getAsString()
                : null;
    }

    private static String primitive(
            JsonElement value) {
        return value == null
                ? ""
                : value.toString();
    }

    private static String path(
            String value,
            String label) {
        if (value == null
                || value.isBlank()
                || value.indexOf('\\') >= 0
                || value.startsWith("/")
                || value.contains("../")
                || value.contains("/..")) {
            throw new IllegalArgumentException(
                    label + " is not a safe relative path");
        }
        return value;
    }

    private static String normalizeEntryId(
            String value) {
        return value == null ? "" : value;
    }

    private static String propertyValue(
            String value) {
        StringBuilder encoded =
                new StringBuilder(value.length());
        boolean leading = true;
        for (int index = 0;
                index < value.length();
                index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> encoded.append("\\\\");
                case '\t' -> encoded.append("\\t");
                case '\n' -> encoded.append("\\n");
                case '\r' -> encoded.append("\\r");
                case '\f' -> encoded.append("\\f");
                case ' ' -> {
                    if (leading) {
                        encoded.append('\\');
                    }
                    encoded.append(' ');
                }
                default -> encoded.append(character);
            }
            leading &= character == ' ';
        }
        return encoded.toString();
    }

    private static int selectorIndex(
            NativeBlockSelectorField selector,
            String value) {
        for (int index = 0;
                index < selector.entries().size();
                index++) {
            NativeBlockSelectorEntry entry =
                    selector.entries()
                            .get(index);
            if (entry.serialized()
                            .equals(value)
                    || entry.blockId()
                            .filter(value::equals)
                            .isPresent()) {
                return index;
            }
        }
        return -1;
    }

    private static NativeBlockSelectorFacts selectorFacts(
            NativeBlockSelectorEntry entry,
            NativeBlockSelectorResolver selectorResolver) {
        return entry.blockId()
                .flatMap(selectorResolver::resolve)
                .orElseThrow(() ->
                        new UnsupportedOperationException(
                                "opaque selector entries are read-only"));
    }

    private static String blockId(
            String value,
            String label,
            NativeBlockSelectorResolver selectorResolver) {
        return selectorResolver.resolve(value)
                .map(NativeBlockSelectorFacts::blockId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                label + " is not a registered block id"));
    }

    public enum ConnectionBasis {
        BLOCK,
        TILE,
        STATE;

        public String serializedName() {
            return name().toLowerCase(
                    Locale.ROOT);
        }

        static Optional<ConnectionBasis> parse(
                String value) {
            if (value == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(valueOf(
                        value.trim()
                                .toUpperCase(
                                        Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }
    }

    public enum RenderLayer {
        CUTOUT,
        TRANSLUCENT;

        public String serializedName() {
            return name().toLowerCase(
                    Locale.ROOT);
        }

        static Optional<RenderLayer> parse(
                String value) {
            if (value == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(valueOf(
                        value.trim()
                                .toUpperCase(
                                        Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }
    }

    public enum AthenaConnection {
        SAME_BLOCK("sameBlock", "same_block"),
        SAME_STATE("sameState", "same_state"),
        STATE("state", "state"),
        CUSTOM("custom", "custom");

        private final String serializedName;
        private final String translationSuffix;

        AthenaConnection(
                String serializedName,
                String translationSuffix) {
            this.serializedName = serializedName;
            this.translationSuffix = translationSuffix;
        }

        public String serializedName() {
            return serializedName;
        }

        public String translationSuffix() {
            return translationSuffix;
        }

        private static AthenaConnection parse(
                JsonElement encoded) {
            if (encoded == null
                    || !encoded.isJsonObject()) {
                return CUSTOM;
            }
            JsonObject condition =
                    encoded.getAsJsonObject();
            String type = string(
                    condition.get("type"));
            if (type == null) {
                return CUSTOM;
            }
            return switch (type) {
                case "sameBlock" -> SAME_BLOCK;
                case "sameState" -> SAME_STATE;
                case "state" -> STATE;
                default -> CUSTOM;
            };
        }
    }

    public enum SelectorPresence {
        ABSENT,
        PRESENT_EMPTY,
        PRESENT_VALUES,
        PRESENT_INVALID
    }

    private enum Field {
        ENTRY_ID,
        MATCHING_BLOCKS,
        CONNECTION_BLOCKS,
        FACES,
        CONNECTION_BASIS,
        RENDER_LAYER,
        TINT_BLOCK,
        TILES,
        ATHENA_CONNECTION,
        AUTHORING_METHOD,
        AUTHORING_COMPATIBILITY
    }
}
