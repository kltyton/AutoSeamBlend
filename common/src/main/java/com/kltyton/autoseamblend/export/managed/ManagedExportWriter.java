package com.kltyton.autoseamblend.export.managed;

import com.kltyton.autoseamblend.export.api.ExportSink;
import com.kltyton.autoseamblend.export.io.CanonicalJson;
import com.kltyton.autoseamblend.export.model.ExportDiagnostic;
import com.kltyton.autoseamblend.export.model.GeneratedFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;

/** 中文：在同一不可变 Managed 导出 IR 上运行的确定性双配置档写入器。 / English: Deterministic dual-profile writer over one immutable managed export IR. */
public final class ManagedExportWriter {
    public WriteResult write(
            ManagedExportIr ir,
            ManagedExportProfile profile,
            ExportSink sink,
            BooleanSupplier cancelled) throws IOException {
        ArrayList<ExportDiagnostic> diagnostics = new ArrayList<>();
        LinkedHashMap<String, byte[]> claimed =
                new LinkedHashMap<>();
        writePackMetadata(ir, profile, sink);
        for (ManagedExportIr.Rule rule : ir.rules()) {
            if (cancelled.getAsBoolean()) throw new ExportCancelledException();
            if (profile == ManagedExportProfile.AUTHORING) {
                for (ManagedExportIr.Document document : rule.documents()) {
                    if (document.authoring().isEmpty()) continue;
                    ManagedExportIr.Artifact artifact =
                            document.authoring().orElseThrow();
                    writeUnique(
                            artifact.path(),
                            artifact.bytes(),
                            rule,
                            sink,
                            claimed,
                            diagnostics);
                }
                for (ManagedExportIr.Tile tile : rule.tiles()) {
                    writeUnique(
                            tile.path(),
                            tile.png(),
                            rule,
                            sink,
                            claimed,
                            diagnostics);
                }
            } else {
                writeBakedRule(
                        rule,
                        sink,
                        claimed,
                        diagnostics);
            }
        }
        if (diagnostics.stream().anyMatch(value -> value.level() == ExportDiagnostic.Level.ERROR)) {
            return new WriteResult(sink.files(), diagnostics);
        }
        if (profile == ManagedExportProfile.AUTHORING) {
            sink.writeUtf8("autoseamblend-export.json", report(ir, profile));
            sink.writeUtf8("autoseamblend-manifest.tsv", manifest(sink.files()));
        }
        return new WriteResult(sink.files(), diagnostics);
    }

    private static void writeBakedRule(
            ManagedExportIr.Rule rule,
            ExportSink sink,
            Map<String, byte[]> claimed,
            List<ExportDiagnostic> diagnostics) throws IOException {
        if (!rule.bakedBlockers().isEmpty()) {
            diagnostics.add(error("baked_unsupported", String.join("; ", rule.bakedBlockers()), rule));
            return;
        }
        boolean hasBakedDocument = rule.documents().stream()
                .anyMatch(document -> document.baked().isPresent());
        if (!"none".equalsIgnoreCase(rule.resolvedMethod())
                && !rule.tiles().isEmpty()
                && !hasBakedDocument) {
            diagnostics.add(error(
                    "missing_baked_document",
                    "non-NONE rule publishes PNG tile(s) but has zero baked native documents: "
                            + rule.selectorIdentity(),
                    rule));
            return;
        }
        for (ManagedExportIr.Document document : rule.documents()) {
            if (document.baked().isEmpty()) continue;
            ManagedExportIr.Artifact artifact =
                    document.baked().orElseThrow();
            String baked = new String(
                    artifact.bytes(),
                    StandardCharsets.UTF_8);
            if (runtimeExtensionLeak(baked)) {
                diagnostics.add(error(
                        "runtime_extension_leak",
                                "baked native document still contains AutoSeamBlend authoring extensions: "
                                + artifact.path(),
                        rule));
                return;
            }
        }
        Map<String, ManagedExportIr.Tile> tiles = new TreeMap<>();
        for (ManagedExportIr.Tile tile : rule.tiles()) {
            if (tiles.put(tile.bakedPath(), tile) != null) {
                diagnostics.add(error(
                        "duplicate_tile_path",
                        "duplicate tile path "
                                + tile.bakedPath(),
                        rule));
            }
        }
        for (int slot : rule.requiredSlots()) {
            String intent = rule.protectedIntents().get(slot);
            if ("DEFAULT".equals(intent) || "SKIP".equals(intent)) continue;
            boolean present = tiles.values().stream()
                    .anyMatch(tile -> tile.slots().contains(slot));
            if (!present) {
                diagnostics.add(error("missing_baked_tile", "missing required slot " + slot, rule));
            }
        }
        if (diagnostics.stream().anyMatch(value -> value.level() == ExportDiagnostic.Level.ERROR
                && value.groupId().equals(rule.selectorIdentity()))) return;
        for (ManagedExportIr.Document document : rule.documents()) {
            if (document.baked().isPresent()) {
                ManagedExportIr.Artifact artifact =
                        document.baked().orElseThrow();
                writeUnique(
                        artifact.path(),
                        artifact.bytes(),
                        rule,
                        sink,
                        claimed,
                        diagnostics);
            }
        }
        for (ManagedExportIr.Tile tile : tiles.values()) {
            writeUnique(
                    tile.bakedPath(),
                    tile.png(),
                    rule,
                    sink,
                    claimed,
                    diagnostics);
        }
    }

    private static void writeUnique(
            String path,
            byte[] bytes,
            ManagedExportIr.Rule rule,
            ExportSink sink,
            Map<String, byte[]> claimed,
            List<ExportDiagnostic> diagnostics)
            throws IOException {
        byte[] previous = claimed.putIfAbsent(path, bytes);
        if (previous == null) {
            sink.write(path, bytes);
            return;
        }
        if (!Arrays.equals(previous, bytes)) {
            diagnostics.add(error(
                    "conflicting_export_path",
                    "resolved queries produced different content for "
                            + path,
                    rule));
        }
    }

    private static boolean runtimeExtensionLeak(String baked) {
        String compact = baked.replaceAll("\\s+", "");
        return baked.lines().map(String::trim).anyMatch(line ->
                        line.equals("method=auto")
                                || line.startsWith("compatibility=")
                                || line.startsWith("autoseamblend."))
                || compact.contains("\"method\":\"auto\"")
                || compact.contains("\"compatibility\":true")
                || compact.contains("\"compatibility\":false")
                || compact.contains(
                        "\"type\":\"autoseamblend:connecting\"")
                || compact.contains("\"autoseamblend.");
    }

    private static ExportDiagnostic error(
            String code,
            String message,
            ManagedExportIr.Rule rule) {
        return new ExportDiagnostic(
                ExportDiagnostic.Level.ERROR, code, message, rule.selectorIdentity());
    }

    private static void writePackMetadata(
            ManagedExportIr ir,
            ManagedExportProfile profile,
            ExportSink sink) throws IOException {
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("min_format", List.of(84, 0));
        pack.put("max_format", 84);
        pack.put("description", "AutoSeamBlend " + profile.serialized() + " export");
        sink.writeUtf8("pack.mcmeta", CanonicalJson.stringify(Map.of("pack", pack)));
    }

    private static String report(ManagedExportIr ir, ManagedExportProfile profile) {
        ArrayList<Object> rules = new ArrayList<>();
        for (ManagedExportIr.Rule rule : ir.rules()) {
            ArrayList<Object> tiles = new ArrayList<>();
            for (ManagedExportIr.Tile tile : rule.tiles()) {
                String exportedPath =
                        profile == ManagedExportProfile.AUTHORING
                                ? tile.path()
                                : tile.bakedPath();
                tiles.add(Map.of(
                        "path", exportedPath,
                        "authoringPath", tile.path(),
                        "bakedPath", tile.bakedPath(),
                        "slots", tile.slots(),
                        "source", tile.source().name().toLowerCase(java.util.Locale.ROOT),
                        "sha256", tile.hash()));
            }
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("order", rule.order());
            value.put("selector", rule.selectorIdentity());
            value.put("targetBlock", rule.targetBlockId());
            ArrayList<Object> documents = new ArrayList<>();
            for (ManagedExportIr.Document document : rule.documents()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                document.authoring().ifPresent(artifact -> {
                    entry.put("authoringPath", artifact.path());
                    entry.put(
                            "authoringSha256",
                            document.authoringHash().orElseThrow());
                });
                document.baked().ifPresent(artifact -> {
                    entry.put("bakedPath", artifact.path());
                    entry.put(
                            "bakedSha256",
                            document.bakedHash().orElseThrow());
                });
                documents.add(entry);
            }
            value.put("documents", documents);
            value.put("authoringSha256", rule.editableHash());
            value.put("requestedMethod", rule.requestedMethod());
            value.put("resolvedMethod", rule.resolvedMethod());
            value.put("inferenceReasons", rule.inferenceReasons());
            value.put("protectedIntents", rule.protectedIntents());
            value.put("generatedSlots", rule.generatedSlots());
            value.put("bakedBlockers", rule.bakedBlockers());
            value.put("tiles", tiles);
            rules.add(value);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("profile", profile.serialized());
        root.put("minecraft", ir.minecraftVersion());
        root.put("loader", ir.loader());
        root.put("engine", ir.engine());
        root.put("engineVersion", ir.engineVersion());
        root.put("autoSeamBlendVersion", ir.autoSeamBlendVersion());
        root.put("runtimeGeneration", ir.runtimeGeneration());
        root.put("managedGenerationSha256", ir.managedGenerationHash());
        root.put("rules", rules);
        return CanonicalJson.stringify(root);
    }

    private static String manifest(List<GeneratedFile> files) {
        StringBuilder output = new StringBuilder("AutoSeamBlend-Export-Manifest\t1\n");
        files.stream().sorted().forEach(file -> output.append(file.path()).append('\t')
                .append(file.sha256()).append('\t').append(file.size()).append('\n'));
        return output.toString();
    }

    public record WriteResult(List<GeneratedFile> files, List<ExportDiagnostic> diagnostics) {
        public WriteResult {
            files = List.copyOf(files);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public static final class ExportCancelledException extends IOException {
        public ExportCancelledException() {
            super("export cancelled");
        }
    }
}
