package com.kltyton.autoseamblend.authoring.format.mcpatcher;

import com.kltyton.autoseamblend.authoring.document.NativeDocumentMerge;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringFile;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringProject;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringRule;
import com.kltyton.autoseamblend.authoring.template.ManagedAuthoringTemplates;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.selection.method.MethodSlotDomain;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * 中文：MCPatcher 原生 {@code .properties} 的 Loader 中立无 I/O authoring/baked 变换边界。
 *
 * English: Loader-neutral, I/O-free authoring/baked transformation boundary for native
 * MCPatcher {@code .properties} documents.
 */
public final class MCPatcherNativeProperties {
    private static final Set<String> PATCHABLE_KEYS =
            Set.of(
                    "id",
                    "matchBlocks",
                    "matchTiles",
                    "connectBlocks",
                    "faces",
                    "connect",
                    "layer",
                    "tintBlock",
                    "tiles",
                    "method",
                    "compatibility");

    private MCPatcherNativeProperties() {}

    /**
     * 中文：把 Common 原生模板无损合并到捕获文档；未知键、注释、顺序与换行风格均保留。
     *
     * English: Losslessly merges the Common native template into a captured
     * document, retaining unknown keys, comments, ordering, and newline style.
     */
    public static AuthoringDocument authoring(
            ManagedAuthoringRule rule, Optional<CapturedDocument> captured) throws IOException {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(captured, "captured");
        ManagedAuthoringProject project =
                ManagedAuthoringTemplates.create(EngineFamily.MCPATCHER, List.of(rule));
        ManagedAuthoringFile template = project.documents().getFirst();
        String path = captured.map(CapturedDocument::path).orElse(template.relativePath());
        byte[] source =
                captured.isPresent()
                        ? NativeDocumentMerge.mergeSource(
                                EngineFamily.MCPATCHER,
                                path,
                                captured.orElseThrow().source(),
                                template.content())
                        : template.content();
        LinkedHashMap<String, Optional<String>> authoringValues = new LinkedHashMap<>();
        authoringValues.put(
                "method", Optional.of(rule.requestedMethod().serializedName()));
        authoringValues.put(
                "compatibility", Optional.of(Boolean.toString(rule.compatibility())));
        return new AuthoringDocument(
                path,
                patch(source, authoringValues),
                captured.map(CapturedDocument::source));
    }

    /**
     * 中文：生成只含 MCPatcher 原生字段的 baked 视图；NONE 没有捕获的作者资源时不输出伪规则。
     *
     * English: Produces a baked view containing only MCPatcher-native fields.
     * NONE emits no synthetic rule when no captured author resource exists.
     */
    public static Optional<byte[]> baked(
            AuthoringDocument document,
            ManagedAuthoringRule rule,
            FrozenPredicate predicate,
            Map<Integer, String> tileExpressions)
            throws IOException {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(predicate, "predicate");
        tileExpressions = Map.copyOf(Objects.requireNonNull(tileExpressions, "tileExpressions"));
        if (rule.resolvedMethod() == ConnectionMethod.NONE) {
            Optional<byte[]> captured = document.capturedSource();
            if (captured.isEmpty()) {
                return Optional.empty();
            }
            /*
             * 中文：只有原生规则可以 passthrough；AutoSeamBlend 的 auto/none 文档不能烘焙
             * 为 Continuity 规则。原生 method 保留，防止缺失 method 默认激活 ctm。
             * English: Only native rules may pass through; AutoSeamBlend auto/none documents
             * cannot bake into Continuity rules. Preserve a native method so an absent method
             * never defaults to ctm.
             */
            return bakedPassthrough(captured.orElseThrow());
        }

        LinkedHashMap<String, Optional<String>> values = new LinkedHashMap<>();
        values.put("id", Optional.empty());
        values.put(
                "method",
                Optional.of(MCPatcherMethodCodec.nativeMethod(rule.resolvedMethod())));
        values.put("tiles", Optional.of(tilesExpression(rule, tileExpressions)));
        values.put("compatibility", Optional.empty());
        if (rule.requestedMethod() == ConnectionMethod.AUTO) {
            addFrozenAutoPredicate(values, rule, predicate);
        }
        return Optional.of(patch(document.source(), values));
    }

    private static void addFrozenAutoPredicate(
            Map<String, Optional<String>> values,
            ManagedAuthoringRule rule,
            FrozenPredicate predicate) {
        boolean overlay = isOverlay(rule.resolvedMethod());
        List<String> receivers = predicate.overlayReceiverBlockIds();
        if (overlay && receivers.isEmpty()) {
            throw new IllegalStateException("EXPORT_OVERLAY_RECEIVERS_EMPTY");
        }
        values.put(
                "matchBlocks",
                Optional.of(overlay ? String.join(" ", receivers) : rule.targetBlockId()));
        values.put(
                "matchTiles",
                rule.pane() ? Optional.of(rule.sourceTextureId()) : Optional.empty());
        values.put("faces", rule.pane() ? Optional.of("sides") : Optional.empty());
        values.put("connect", Optional.of("block"));
        values.put("connectBlocks", Optional.of(rule.targetBlockId()));
        values.put("layer", overlay ? Optional.of("cutout") : Optional.empty());
        values.put(
                "tintBlock",
                overlay ? Optional.of(rule.targetBlockId()) : Optional.empty());
    }

    private static Optional<byte[]> bakedPassthrough(byte[] capturedSource) throws IOException {
        byte[] source = Objects.requireNonNull(capturedSource, "capturedSource").clone();
        Properties properties = new Properties();
        properties.load(new StringReader(decode(source)));
        String method = properties.getProperty("method", "").trim();
        if (method.equalsIgnoreCase("auto") || method.equalsIgnoreCase("none")) {
            return Optional.empty();
        }
        LinkedHashMap<String, Optional<String>> values = new LinkedHashMap<>();
        values.put("id", Optional.empty());
        values.put("compatibility", Optional.empty());
        return Optional.of(patch(source, values));
    }

    private static String tilesExpression(
            ManagedAuthoringRule rule,
            Map<Integer, String> tileExpressions) {
        StringBuilder tiles = new StringBuilder();
        for (int slot : MethodSlotDomain.of(rule.resolvedMethod()).slots()) {
            if (!tiles.isEmpty()) {
                tiles.append(' ');
            }
            String expression = tileExpressions.get(slot);
            if (expression == null || expression.isBlank()) {
                throw new IllegalArgumentException(
                        "baked tile expression missing for slot " + slot);
            }
            tiles.append(expression);
        }
        return tiles.toString();
    }

    private static boolean isOverlay(ConnectionMethod method) {
        return method == ConnectionMethod.RUNTIME_BLEND
                || method == ConnectionMethod.OVERLAY
                || method == ConnectionMethod.OVERLAY_CTM;
    }

    /** 中文：只替换显式键并去重同名行，其他原生内容逐行不动。 / English: Replaces only explicit keys and deduplicates matching lines while leaving all other native content untouched. */
    private static byte[] patch(byte[] sourceBytes, Map<String, Optional<String>> values)
            throws IOException {
        if (!PATCHABLE_KEYS.containsAll(values.keySet())) {
            throw new IOException("NATIVE_PROPERTY_PATCH_UNSUPPORTED");
        }
        String source = decode(sourceBytes);
        String newline = source.contains("\r\n") ? "\r\n" : "\n";
        boolean finalNewline = source.endsWith("\n") || source.endsWith("\r");
        ArrayList<String> output = new ArrayList<>();
        LinkedHashSet<String> emitted = new LinkedHashSet<>();
        for (String line : source.split("\\R", -1)) {
            String key = propertyKey(line);
            if (key == null || !values.containsKey(key)) {
                output.add(line);
                continue;
            }
            if (!emitted.add(key)) {
                continue;
            }
            values.get(key).ifPresent(value -> output.add(key + '=' + value));
        }
        values.forEach(
                (key, value) -> {
                    if (emitted.add(key)) {
                        value.ifPresent(next -> output.add(key + '=' + next));
                    }
                });
        if (finalNewline && !output.isEmpty() && output.getLast().isEmpty()) {
            output.removeLast();
        }
        String resolved = String.join(newline, output);
        if (finalNewline) {
            resolved += newline;
        }
        return resolved.getBytes(StandardCharsets.UTF_8);
    }

    private static String decode(byte[] source) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .decode(ByteBuffer.wrap(Objects.requireNonNull(source, "source")))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("MCPATCHER_DOCUMENT_UTF8_INVALID", exception);
        }
    }

    private static String propertyKey(String line) {
        int start = 0;
        while (start < line.length() && Character.isWhitespace(line.charAt(start))) {
            start++;
        }
        if (start == line.length() || line.charAt(start) == '#' || line.charAt(start) == '!') {
            return null;
        }
        boolean escaped = false;
        for (int index = start; index < line.length(); index++) {
            char value = line.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (value == '\\') {
                escaped = true;
            } else if (value == '=' || value == ':' || Character.isWhitespace(value)) {
                return line.substring(start, index);
            }
        }
        return line.substring(start);
    }

    public record CapturedDocument(String path, byte[] source) {
        public CapturedDocument {
            path = requirePropertiesPath(path);
            source = Objects.requireNonNull(source, "source").clone();
        }

        @Override
        public byte[] source() {
            return source.clone();
        }
    }

    public record AuthoringDocument(
            String path, byte[] source, Optional<byte[]> capturedSource) {
        public AuthoringDocument {
            path = requirePropertiesPath(path);
            source = Objects.requireNonNull(source, "source").clone();
            Objects.requireNonNull(capturedSource, "capturedSource");
            capturedSource = capturedSource.map(bytes -> bytes.clone());
        }

        @Override
        public byte[] source() {
            return source.clone();
        }

        @Override
        public Optional<byte[]> capturedSource() {
            return capturedSource.map(byte[]::clone);
        }
    }

    /** 中文：同一冻结代次中 AUTO 推断所需的精确接收方集合。 / English: Exact receiver set required by AUTO inference in one frozen generation. */
    public record FrozenPredicate(List<String> overlayReceiverBlockIds) {
        public FrozenPredicate {
            overlayReceiverBlockIds = List.copyOf(
                    Objects.requireNonNull(overlayReceiverBlockIds, "overlayReceiverBlockIds"));
            if (overlayReceiverBlockIds.stream().anyMatch(
                            id -> id == null || !canonicalIdentifier(id))
                    || overlayReceiverBlockIds.stream().distinct().count()
                            != overlayReceiverBlockIds.size()) {
                throw new IllegalArgumentException(
                        "overlay receiver ids must be canonical and unique");
            }
        }
    }

    private static boolean canonicalIdentifier(String raw) {
        int separator = raw.indexOf(':');
        if (separator <= 0
                || separator != raw.lastIndexOf(':')
                || separator == raw.length() - 1) {
            return false;
        }
        String namespace = raw.substring(0, separator);
        String path = raw.substring(separator + 1);
        if (!namespace.matches("[a-z0-9_.-]+")
                || !path.matches("[a-z0-9/._-]+")
                || path.startsWith("/")
                || path.endsWith("/")
                || path.contains("//")) {
            return false;
        }
        for (String segment : path.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    private static String requirePropertiesPath(String path) {
        Objects.requireNonNull(path, "path");
        if (path.isBlank()
                || path.indexOf('\\') >= 0
                || path.startsWith("/")
                || !path.endsWith(".properties")) {
            throw new IllegalArgumentException(
                    "path must be a normalized MCPatcher properties path");
        }
        return path;
    }

}
