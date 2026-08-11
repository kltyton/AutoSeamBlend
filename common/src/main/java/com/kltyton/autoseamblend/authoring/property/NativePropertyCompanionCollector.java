package com.kltyton.autoseamblend.authoring.property;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringFile;
import com.kltyton.autoseamblend.engine.EngineFamily;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * 中文：按四格式原生引用图收集属性文档的伴随模型、纹理、动画元数据与槽位；实际字节读取由 Loader 提供。
 *
 * English: Collects companion models, textures, animation metadata, and slots
 * from the four native reference graphs while the Loader supplies byte reads.
 */
public final class NativePropertyCompanionCollector {
    private static final int MODEL_LIMIT = 64;
    private static final int TILE_LIMIT = 4096;

    private NativePropertyCompanionCollector() {}

    public static ManagedAuthoringFile principal(
            EngineFamily family,
            List<ManagedAuthoringFile> documents) {
        Objects.requireNonNull(family, "family");
        return List.copyOf(Objects.requireNonNull(documents, "documents"))
                .stream()
                .filter(document -> switch (family) {
                    case MCPATCHER -> document.relativePath().endsWith(".properties");
                    default -> throw new IllegalArgumentException(
                            "LOADER_EXCLUSIVE_COMPANION_COLLECTION_REQUIRES_ADAPTER");
                    case FUSION -> document.relativePath()
                            .contains("/fusion/model_modifiers/blocks/");
                    case ATHENA -> document.relativePath().contains("/athena/");
                })
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "NATIVE_PROPERTY_DOCUMENT_MISSING"));
    }

    public static Map<String, byte[]> collect(
            EngineFamily family,
            String sourcePath,
            byte[] source,
            List<ManagedAuthoringFile> templateDocuments,
            String templatePrincipalPath,
            DocumentReader reader) throws IOException {
        Objects.requireNonNull(family, "family");
        String principalPath = path(sourcePath, "sourcePath");
        byte[] principalSource = Objects.requireNonNull(source, "source").clone();
        List<ManagedAuthoringFile> templates = List.copyOf(
                Objects.requireNonNull(templateDocuments, "templateDocuments"));
        String templatePath = path(templatePrincipalPath, "templatePrincipalPath");
        DocumentReader documentReader = Objects.requireNonNull(reader, "reader");
        LinkedHashMap<String, byte[]> result = templateCompanions(
                principalPath,
                templatePath,
                templates,
                documentReader);
        switch (family) {
            case MCPATCHER -> collectMCPatcherTiles(
                    principalPath, principalSource, result, documentReader);
            default -> throw new IOException(
                    "LOADER_EXCLUSIVE_COMPANION_COLLECTION_REQUIRES_ADAPTER");
            case FUSION -> collectModelBundle(
                    principalPath,
                    principalSource,
                    result,
                    documentReader,
                    (root, addReference) -> strings(root.get("default_model_overrides"))
                            .forEach(addReference));
            case ATHENA -> collectTextureBundle(
                    principalPath, json(principalSource), result, documentReader);
        }
        result.remove(principalPath);
        return copyDocuments(result);
    }

    /**
     * 中文：让 Loader 独占模型格式复用公共模型父链、纹理和动画元数据收集。
     * English: Lets a Loader-exclusive model format reuse shared parent-model, texture, and
     * animation-metadata collection.
     */
    public static Map<String, byte[]> collectModelReferenced(
            String sourcePath,
            byte[] source,
            List<ManagedAuthoringFile> templateDocuments,
            String templatePrincipalPath,
            DocumentReader reader,
            ModelReferenceCollector references) throws IOException {
        String principalPath = path(sourcePath, "sourcePath");
        byte[] principalSource = Objects.requireNonNull(source, "source").clone();
        List<ManagedAuthoringFile> templates = List.copyOf(
                Objects.requireNonNull(templateDocuments, "templateDocuments"));
        String templatePath = path(templatePrincipalPath, "templatePrincipalPath");
        DocumentReader documentReader = Objects.requireNonNull(reader, "reader");
        LinkedHashMap<String, byte[]> result = templateCompanions(
                principalPath,
                templatePath,
                templates,
                documentReader);
        collectModelBundle(
                principalPath,
                principalSource,
                result,
                documentReader,
                Objects.requireNonNull(references, "references"));
        result.remove(principalPath);
        return copyDocuments(result);
    }

    private static void collectModelBundle(
            String sourcePath,
            byte[] source,
            Map<String, byte[]> output,
            DocumentReader reader,
            ModelReferenceCollector references) throws IOException {
        String defaultNamespace = namespace(sourcePath);
        LinkedHashSet<String> pending = new LinkedHashSet<>();
        JsonObject root = json(source);
        references.collect(
                root,
                value -> modelPath(value, defaultNamespace).ifPresent(pending::add));
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        while (!pending.isEmpty() && visited.size() < MODEL_LIMIT) {
            String modelPath = pending.iterator().next();
            pending.remove(modelPath);
            if (!visited.add(modelPath)) {
                continue;
            }
            Optional<byte[]> bytes = reader.read(modelPath);
            if (bytes.isEmpty()) {
                continue;
            }
            byte[] document = bytes.orElseThrow();
            output.put(modelPath, document);
            JsonObject model = json(document);
            modelPath(string(model.get("parent")), namespace(modelPath))
                    .ifPresent(pending::add);
            collectTextureBundle(modelPath, model, output, reader);
        }
    }

    private static LinkedHashMap<String, byte[]> templateCompanions(
            String principalPath,
            String templatePath,
            List<ManagedAuthoringFile> templates,
            DocumentReader reader) throws IOException {
        LinkedHashMap<String, byte[]> result = new LinkedHashMap<>();
        if (!principalPath.equals(templatePath)) {
            return result;
        }
        for (ManagedAuthoringFile document : templates) {
            if (document.relativePath().equals(templatePath)) {
                continue;
            }
            result.put(
                    document.relativePath(),
                    reader.read(document.relativePath()).orElseGet(document::content));
        }
        return result;
    }

    private static void collectTextureBundle(
            String sourcePath,
            JsonObject root,
            Map<String, byte[]> output,
            DocumentReader reader) throws IOException {
        String defaultNamespace = namespace(sourcePath);
        ArrayList<String> references = new ArrayList<>();
        collectTextureReferences(root.get("textures"), references);
        collectTextureReferences(root.get("ctm_textures"), references);
        for (String reference : references) {
            Optional<String> texturePath = texturePath(reference, defaultNamespace);
            if (texturePath.isPresent()) {
                copyDocumentAndMetadata(texturePath.orElseThrow(), output, reader);
            }
        }
    }

    private static void collectTextureReferences(
            JsonElement encoded,
            List<String> output) {
        if (encoded == null) {
            return;
        }
        String value = string(encoded);
        if (value != null) {
            if (!value.startsWith("#") && !value.contains("[$index]")) {
                output.add(value);
            }
            return;
        }
        if (encoded.isJsonArray()) {
            encoded.getAsJsonArray().forEach(element ->
                    collectTextureReferences(element, output));
        } else if (encoded.isJsonObject()) {
            encoded.getAsJsonObject().entrySet().forEach(entry ->
                    collectTextureReferences(entry.getValue(), output));
        }
    }

    private static void collectMCPatcherTiles(
            String sourcePath,
            byte[] source,
            Map<String, byte[]> output,
            DocumentReader reader) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(decode(source)));
        String expression = properties.getProperty("tiles", "");
        String directory = sourcePath.substring(0, sourcePath.lastIndexOf('/') + 1);
        int expanded = 0;
        for (String token : expression.split("\\s+")) {
            if (token.isBlank() || token.startsWith("<")) {
                continue;
            }
            if (token.matches("[0-9]+-[0-9]+")) {
                String[] bounds = token.split("-", 2);
                int first = Integer.parseInt(bounds[0]);
                int last = Integer.parseInt(bounds[1]);
                if (last < first
                        || Math.addExact(expanded, last - first + 1) > TILE_LIMIT) {
                    continue;
                }
                for (int value = first; value <= last; value++) {
                    copyDocumentAndMetadata(
                            directory + value + ".png", output, reader);
                }
                expanded += last - first + 1;
                continue;
            }
            Optional<String> tilePath = token.indexOf(':') >= 0
                    ? texturePath(token, namespace(sourcePath))
                    : relativePngPath(directory, token);
            if (tilePath.isPresent()) {
                copyDocumentAndMetadata(tilePath.orElseThrow(), output, reader);
                expanded++;
            }
            if (expanded >= TILE_LIMIT) {
                break;
            }
        }
    }

    private static Optional<String> relativePngPath(
            String directory,
            String value) {
        String normalized = value.startsWith("./") ? value.substring(2) : value;
        if (normalized.isBlank()
                || normalized.startsWith("/")
                || normalized.contains("../")
                || normalized.contains("/..")) {
            return Optional.empty();
        }
        return Optional.of(directory + normalized
                + (normalized.endsWith(".png") ? "" : ".png"));
    }

    private static Optional<String> modelPath(
            String value,
            String defaultNamespace) {
        return resourcePath(value, defaultNamespace, "models/", ".json");
    }

    private static Optional<String> texturePath(
            String value,
            String defaultNamespace) {
        return resourcePath(value, defaultNamespace, "textures/", ".png");
    }

    private static Optional<String> resourcePath(
            String value,
            String defaultNamespace,
            String folder,
            String suffix) {
        if (value == null || value.isBlank() || value.startsWith("#")) {
            return Optional.empty();
        }
        String normalized = value;
        int separator = normalized.indexOf(':');
        String namespace = separator >= 0
                ? normalized.substring(0, separator)
                : defaultNamespace;
        String resource = separator >= 0
                ? normalized.substring(separator + 1)
                : normalized;
        if (resource.startsWith(folder)) {
            resource = resource.substring(folder.length());
        }
        if (resource.endsWith(suffix)) {
            resource = resource.substring(0, resource.length() - suffix.length());
        }
        if (!canonicalIdentifier(namespace, resource)) {
            return Optional.empty();
        }
        return Optional.of("assets/" + namespace + '/' + folder + resource + suffix);
    }

    private static boolean canonicalIdentifier(
            String namespace,
            String resource) {
        if (!namespace.matches("[a-z0-9_.-]+")
                || !resource.matches("[a-z0-9/._-]+")
                || resource.startsWith("/")
                || resource.endsWith("/")
                || resource.contains("//")) {
            return false;
        }
        for (String segment : resource.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    private static void copyDocumentAndMetadata(
            String path,
            Map<String, byte[]> output,
            DocumentReader reader) throws IOException {
        Optional<byte[]> document = reader.read(path);
        if (document.isPresent()) {
            output.putIfAbsent(path, document.orElseThrow());
        }
        Optional<byte[]> metadata = reader.read(path + ".mcmeta");
        if (metadata.isPresent()) {
            output.putIfAbsent(path + ".mcmeta", metadata.orElseThrow());
        }
    }

    private static String namespace(String path) {
        String[] segments = path.split("/", 3);
        if (segments.length != 3 || !segments[0].equals("assets")) {
            throw new IllegalArgumentException("native document is outside assets");
        }
        return segments[1];
    }

    private static JsonObject json(byte[] source) throws IOException {
        try {
            JsonElement parsed = JsonParser.parseString(decode(source));
            if (parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
        } catch (RuntimeException exception) {
            throw new IOException("NATIVE_PROPERTY_JSON_INVALID", exception);
        }
        throw new IOException("NATIVE_PROPERTY_JSON_INVALID");
    }

    private static String decode(byte[] source) {
        return new String(Objects.requireNonNull(source, "source"), StandardCharsets.UTF_8);
    }

    private static List<String> strings(JsonElement value) {
        if (value == null || !value.isJsonArray()) {
            return List.of();
        }
        ArrayList<String> result = new ArrayList<>();
        value.getAsJsonArray().forEach(element -> {
            String candidate = string(element);
            if (candidate != null && !candidate.isBlank()) {
                result.add(candidate);
            }
        });
        return List.copyOf(result);
    }

    private static String string(JsonElement value) {
        return value != null
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isString()
                ? value.getAsString()
                : null;
    }

    private static Map<String, byte[]> copyDocuments(
            Map<String, byte[]> documents) {
        LinkedHashMap<String, byte[]> copy = new LinkedHashMap<>();
        Objects.requireNonNull(documents, "documents").forEach((path, bytes) ->
                copy.put(
                        path(path, "companion document path"),
                        Objects.requireNonNull(bytes, "companion document bytes").clone()));
        return Collections.unmodifiableMap(copy);
    }

    private static String path(String value, String label) {
        if (value == null
                || value.isBlank()
                || value.indexOf('\\') >= 0
                || value.startsWith("/")
                || value.contains("../")
                || value.contains("/..")) {
            throw new IllegalArgumentException(label + " is not a safe relative path");
        }
        return value;
    }

    /** 中文：Loader 提供的无副作用原生资源字节读取边界。 / English: Side-effect-free native resource byte-read boundary supplied by a Loader. */
    @FunctionalInterface
    public interface DocumentReader {
        Optional<byte[]> read(String path) throws IOException;
    }

    /** 中文：格式适配器向公共模型图提交原生模型引用。 / English: A format adapter submits native model references to the shared model graph. */
    @FunctionalInterface
    public interface ModelReferenceCollector {
        void collect(JsonObject root, Consumer<String> addReference);
    }
}
