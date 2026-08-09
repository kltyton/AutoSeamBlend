package com.kltyton.autoseamblend.authoring.export;

import com.kltyton.autoseamblend.authoring.property.NativePropertyPatch;
import com.kltyton.autoseamblend.authoring.document.NativeDocumentOperations;
import com.kltyton.autoseamblend.authoring.property.NativePropertyPatchApplier;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：跨 Loader 的原生主文档纯数据快照；格式特有 baked 变换留在对应适配器。
 *
 * English: Loader-neutral pure-data snapshot of a native principal document;
 * format-specific baked transformations stay in the adapter.
 */
public final class NativeDocumentSnapshot {
    private final EngineFamily family;
    private final String documentPath;
    private final byte[] principalDocument;
    private final Map<String, byte[]> companionDocuments;
    private final Optional<NativePropertyPatch> propertyPatch;
    private final String displayIdentity;
    private final ConnectionMethod authoringMethod;
    private final boolean authoringCompatibility;

    public NativeDocumentSnapshot(
            EngineFamily family,
            String documentPath,
            byte[] principalDocument,
            Map<String, byte[]> companionDocuments,
            Optional<NativePropertyPatch> propertyPatch,
            String displayIdentity,
            ConnectionMethod authoringMethod,
            boolean authoringCompatibility) {
        this.family = Objects.requireNonNull(family, "family");
        if (documentPath == null
                || documentPath.isBlank()
                || documentPath.indexOf('\\') >= 0) {
            throw new IllegalArgumentException(
                    "documentPath must be a normalized resource-pack path");
        }
        this.documentPath = documentPath;
        this.principalDocument = Objects.requireNonNull(
                        principalDocument,
                        "principalDocument")
                .clone();
        this.companionDocuments = copyDocuments(companionDocuments);
        this.propertyPatch = Objects.requireNonNull(
                propertyPatch,
                "propertyPatch");
        if (displayIdentity == null || displayIdentity.isEmpty()) {
            throw new IllegalArgumentException(
                    "displayIdentity must not be empty");
        }
        this.displayIdentity = displayIdentity;
        this.authoringMethod = Objects.requireNonNull(
                authoringMethod,
                "authoringMethod");
        this.authoringCompatibility = authoringCompatibility;
        if (propertyPatch.isPresent()) {
            NativePropertyPatch patch = propertyPatch.orElseThrow();
            if (patch.family() != family
                    || !patch.documentPath().equals(documentPath)
                    || !Arrays.equals(
                            patch.sourceDocument(),
                            this.principalDocument)) {
                throw new IllegalArgumentException(
                        "native property patch differs from its principal document");
            }
        }
    }

    public NativeDocumentSnapshot(
            EngineFamily family,
            String documentPath,
            byte[] principalDocument,
            Optional<NativePropertyPatch> propertyPatch) {
        this(
                family,
                documentPath,
                principalDocument,
                Map.of(),
                propertyPatch,
                documentPath,
                ConnectionMethod.NONE,
                false);
    }

    public EngineFamily family() {
        return family;
    }

    public String documentPath() {
        return documentPath;
    }

    public byte[] principalDocument() {
        return principalDocument.clone();
    }

    public Map<String, byte[]> companionDocuments() {
        return copyDocuments(companionDocuments);
    }

    public Optional<NativePropertyPatch> propertyPatch() {
        return propertyPatch;
    }

    public String displayIdentity() {
        return displayIdentity;
    }

    public ConnectionMethod authoringMethod() {
        return authoringMethod;
    }

    public boolean authoringCompatibility() {
        return authoringCompatibility;
    }

    /**
     * 中文：按捕获顺序应用 GUI 补丁和本次导出的扩展字段，保留所有未修改原生字段。
     *
     * English: Applies captured GUI edits and this export's extension values in
     * order while retaining every untouched native field.
     */
    public byte[] resolve(
            Map<String, Optional<String>> exportValues)
            throws IOException {
        return resolve(exportValues, NativeDocumentOperations.shared());
    }

    public byte[] resolve(
            Map<String, Optional<String>> exportValues,
            NativeDocumentOperations operations)
            throws IOException {
        LinkedHashMap<String, Optional<String>> values =
                new LinkedHashMap<>();
        propertyPatch.ifPresent(patch -> values.putAll(patch.values()));
        values.putAll(Objects.requireNonNull(exportValues, "exportValues"));
        return Objects.requireNonNull(operations, "operations").resolveProperty(
                family,
                documentPath,
                principalDocument,
                Collections.unmodifiableMap(values));
    }

    public byte[] resolve() throws IOException {
        return resolve(Map.of());
    }

    public byte[] resolve(NativeDocumentOperations operations) throws IOException {
        return resolve(Map.of(), operations);
    }

    public NativeDocumentSnapshot withPropertyPatch(
            Optional<NativePropertyPatch> value) {
        return new NativeDocumentSnapshot(
                family,
                documentPath,
                principalDocument,
                companionDocuments,
                Objects.requireNonNull(value, "value"),
                displayIdentity,
                authoringMethod,
                authoringCompatibility);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NativeDocumentSnapshot value)) {
            return false;
        }
        return family == value.family
                && documentPath.equals(value.documentPath)
                && Arrays.equals(principalDocument, value.principalDocument)
                && sameDocuments(companionDocuments, value.companionDocuments)
                && propertyPatch.equals(value.propertyPatch)
                && displayIdentity.equals(value.displayIdentity)
                && authoringMethod == value.authoringMethod
                && authoringCompatibility == value.authoringCompatibility;
    }

    @Override
    public int hashCode() {
        int result = 31 * family.hashCode() + documentPath.hashCode();
        result = 31 * result + Arrays.hashCode(principalDocument);
        result = 31 * result + companionHash(companionDocuments);
        result = 31 * result + propertyPatch.hashCode();
        result = 31 * result + displayIdentity.hashCode();
        result = 31 * result + authoringMethod.hashCode();
        return 31 * result + Boolean.hashCode(authoringCompatibility);
    }

    @Override
    public String toString() {
        return "NativeDocumentSnapshot[" + family + ", " + documentPath + "]";
    }

    /**
     * 中文：按工作区顺序合并同一文档的互不冲突补丁，拒绝 last-writer-wins 覆盖。
     *
     * English: Merges non-conflicting patches for one document in workspace
     * order and rejects last-writer-wins overwrites.
     */
    public static Optional<NativePropertyPatch> mergePropertyPatches(
            List<NativeDocumentSnapshot> documents) {
        ArrayList<NativeDocumentSnapshot> ordered = new ArrayList<>(
                Objects.requireNonNull(documents, "documents"));
        if (ordered.isEmpty()) {
            return Optional.empty();
        }
        NativeDocumentSnapshot first = ordered.getFirst();
        LinkedHashMap<String, Optional<String>> values =
                new LinkedHashMap<>();
        String templatePath = null;
        for (NativeDocumentSnapshot document : ordered) {
            if (document.family != first.family
                    || !document.documentPath.equals(first.documentPath)
                    || !Arrays.equals(
                            document.principalDocument,
                            first.principalDocument)
                    || !sameDocuments(
                            document.companionDocuments,
                            first.companionDocuments)) {
                throw new IllegalArgumentException(
                        "native document snapshots cannot be merged");
            }
            if (document.propertyPatch.isEmpty()) {
                continue;
            }
            NativePropertyPatch patch = document.propertyPatch.orElseThrow();
            if (templatePath == null) {
                templatePath = patch.templateDocumentPath();
            } else if (!templatePath.equals(patch.templateDocumentPath())) {
                throw new IllegalArgumentException(
                        "native document template paths differ");
            }
            patch.values().forEach((key, value) -> {
                Optional<String> previous = values.putIfAbsent(key, value);
                if (previous != null && !previous.equals(value)) {
                    throw new IllegalArgumentException(
                            "native document patch conflict: " + key);
                }
            });
        }
        if (templatePath == null) {
            return Optional.empty();
        }
        return Optional.of(new NativePropertyPatch(
                first.family,
                first.documentPath,
                templatePath,
                first.principalDocument,
                Collections.unmodifiableMap(values)));
    }

    private static Map<String, byte[]> copyDocuments(
            Map<String, byte[]> documents) {
        LinkedHashMap<String, byte[]> copy = new LinkedHashMap<>();
        Objects.requireNonNull(documents, "companionDocuments")
                .forEach((path, bytes) -> {
                    if (path == null
                            || path.isBlank()
                            || path.indexOf('\\') >= 0) {
                        throw new IllegalArgumentException(
                                "companion document path must be normalized");
                    }
                    copy.put(
                            path,
                            Objects.requireNonNull(
                                            bytes,
                                            "companion document bytes")
                                    .clone());
                });
        return Collections.unmodifiableMap(copy);
    }

    private static boolean sameDocuments(
            Map<String, byte[]> left,
            Map<String, byte[]> right) {
        if (!left.keySet().equals(right.keySet())) {
            return false;
        }
        return left.entrySet().stream()
                .allMatch(entry -> Arrays.equals(
                        entry.getValue(),
                        right.get(entry.getKey())));
    }

    private static int companionHash(Map<String, byte[]> documents) {
        int result = 1;
        for (Map.Entry<String, byte[]> entry : documents.entrySet()) {
            result = 31 * result + entry.getKey().hashCode();
            result = 31 * result + Arrays.hashCode(entry.getValue());
        }
        return result;
    }
}
