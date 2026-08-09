package com.kltyton.autoseamblend.export.managed;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** 中文：GUI 与命令导出以相同方式使用的项目自有不可变表示。 / English: Immutable project-owned representation consumed identically by GUI and command export. */
public final class ManagedExportIr {
    private final long runtimeGeneration;
    private final String managedGenerationHash;
    private final String minecraftVersion;
    private final String loader;
    private final String engine;
    private final String engineVersion;
    private final String autoSeamBlendVersion;
    private final List<Rule> rules;

    public ManagedExportIr(
            long runtimeGeneration,
            String managedGenerationHash,
            String minecraftVersion,
            String loader,
            String engine,
            String engineVersion,
            String autoSeamBlendVersion,
            List<Rule> rules) {
        if (runtimeGeneration < 0) throw new IllegalArgumentException("runtimeGeneration must be non-negative");
        this.runtimeGeneration = runtimeGeneration;
        this.managedGenerationHash = text(managedGenerationHash, "managedGenerationHash");
        this.minecraftVersion = text(minecraftVersion, "minecraftVersion");
        this.loader = text(loader, "loader");
        this.engine = text(engine, "engine");
        this.engineVersion = text(engineVersion, "engineVersion");
        this.autoSeamBlendVersion = text(autoSeamBlendVersion, "autoSeamBlendVersion");
        ArrayList<Rule> ordered = new ArrayList<>(Objects.requireNonNull(rules, "rules"));
        ordered.sort(Comparator.comparingInt(Rule::order));
        this.rules = List.copyOf(ordered);
    }

    public long runtimeGeneration() { return runtimeGeneration; }
    public String managedGenerationHash() { return managedGenerationHash; }
    public String minecraftVersion() { return minecraftVersion; }
    public String loader() { return loader; }
    public String engine() { return engine; }
    public String engineVersion() { return engineVersion; }
    public String autoSeamBlendVersion() { return autoSeamBlendVersion; }
    public List<Rule> rules() { return rules; }

    public static final class Rule {
        private final int order;
        private final String selectorIdentity;
        private final String targetBlockId;
        private final List<Document> documents;
        private final String requestedMethod;
        private final String resolvedMethod;
        private final List<String> inferenceReasons;
        private final List<Integer> requiredSlots;
        private final Map<Integer, String> protectedIntents;
        private final List<Integer> generatedSlots;
        private final List<String> bakedBlockers;
        private final List<Tile> tiles;

        public Rule(
                int order,
                String selectorIdentity,
                String targetBlockId,
                List<Document> documents,
                String requestedMethod,
                String resolvedMethod,
                List<String> inferenceReasons,
                List<Integer> requiredSlots,
                Map<Integer, String> protectedIntents,
                List<Integer> generatedSlots,
                List<String> bakedBlockers,
                List<Tile> tiles) {
            if (order < 0) throw new IllegalArgumentException("rule order must be non-negative");
            this.order = order;
            this.selectorIdentity = text(selectorIdentity, "selectorIdentity");
            this.targetBlockId = text(targetBlockId, "targetBlockId");
            this.documents = List.copyOf(Objects.requireNonNull(documents, "documents"));
            if (this.documents.isEmpty()) {
                throw new IllegalArgumentException("rule requires at least one native document");
            }
            long authoringArtifacts = this.documents.stream()
                    .flatMap(document -> document.authoring().stream())
                    .count();
            long distinctAuthoringPaths = this.documents.stream()
                    .flatMap(document -> document.authoring().stream())
                    .map(Artifact::path)
                    .distinct()
                    .count();
            long bakedArtifacts = this.documents.stream()
                    .flatMap(document -> document.baked().stream())
                    .count();
            long distinctBakedPaths = this.documents.stream()
                    .flatMap(document -> document.baked().stream())
                    .map(Artifact::path)
                    .distinct()
                    .count();
            if (authoringArtifacts != distinctAuthoringPaths
                    || bakedArtifacts != distinctBakedPaths) {
                throw new IllegalArgumentException(
                        "native document paths must be unique within each export profile");
            }
            this.requestedMethod = text(requestedMethod, "requestedMethod");
            this.resolvedMethod = text(resolvedMethod, "resolvedMethod");
            this.inferenceReasons = List.copyOf(inferenceReasons);
            this.requiredSlots = List.copyOf(requiredSlots);
            this.protectedIntents = Map.copyOf(new TreeMap<>(protectedIntents));
            this.generatedSlots = List.copyOf(generatedSlots);
            this.bakedBlockers = List.copyOf(bakedBlockers);
            ArrayList<Tile> orderedTiles = new ArrayList<>(tiles);
            orderedTiles.sort(Comparator
                    .comparing(Tile::path)
                    .thenComparing(Tile::bakedPath));
            this.tiles = List.copyOf(orderedTiles);
        }

        public int order() { return order; }
        public String selectorIdentity() { return selectorIdentity; }
        public String targetBlockId() { return targetBlockId; }
        public List<Document> documents() { return documents; }
        public String requestedMethod() { return requestedMethod; }
        public String resolvedMethod() { return resolvedMethod; }
        public List<String> inferenceReasons() { return inferenceReasons; }
        public List<Integer> requiredSlots() { return requiredSlots; }
        public Map<Integer, String> protectedIntents() { return protectedIntents; }
        public List<Integer> generatedSlots() { return generatedSlots; }
        public List<String> bakedBlockers() { return bakedBlockers; }
        public List<Tile> tiles() { return tiles; }
        public String editableHash() {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                documents.forEach(document -> {
                    document.authoring().ifPresent(artifact -> {
                        digest.update(artifact.path().getBytes(
                                java.nio.charset.StandardCharsets.UTF_8));
                        digest.update((byte) 0);
                        digest.update(artifact.bytes());
                        digest.update((byte) 0);
                    });
                });
                return HexFormat.of().formatHex(digest.digest());
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 unavailable", exception);
            }
        }
    }

    /** 中文：一个原生格式文档，包含可编辑的创作字节和可选的 baked 视图。 / English: One native-format document with editable authoring bytes and an optional baked view. */
    public static final class Document {
        private final Artifact authoring;
        private final Artifact baked;

        public Document(
                String path,
                byte[] authoring,
                byte[] baked) {
            this(
                    path,
                    authoring,
                    baked == null ? null : path,
                    baked);
        }

        public Document(
                String authoringPath,
                byte[] authoring,
                String bakedPath,
                byte[] baked) {
            if ((authoringPath == null) != (authoring == null)
                    || (bakedPath == null) != (baked == null)) {
                throw new IllegalArgumentException(
                        "document paths and bytes must be present together");
            }
            if (authoring == null && baked == null) {
                throw new IllegalArgumentException(
                        "document requires an authoring or baked artifact");
            }
            this.authoring = authoring == null
                    ? null
                    : new Artifact(authoringPath, authoring);
            this.baked = baked == null
                    ? null
                    : new Artifact(bakedPath, baked);
        }

        public java.util.Optional<Artifact> authoring() {
            return java.util.Optional.ofNullable(authoring);
        }
        public java.util.Optional<Artifact> baked() {
            return java.util.Optional.ofNullable(baked);
        }
        public java.util.Optional<String> authoringHash() {
            return authoring == null
                    ? java.util.Optional.empty()
                    : java.util.Optional.of(sha256(authoring.bytes));
        }
        public java.util.Optional<String> bakedHash() {
            return baked == null
                    ? java.util.Optional.empty()
                    : java.util.Optional.of(sha256(baked.bytes));
        }
    }

    public static final class Artifact {
        private final String path;
        private final byte[] bytes;

        public Artifact(String path, byte[] bytes) {
            this.path = text(path, "artifact path");
            this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
        }

        public String path() { return path; }
        public byte[] bytes() { return bytes.clone(); }
    }

    public static final class Tile {
        private final String path;
        private final String bakedPath;
        private final List<Integer> slots;
        private final Source source;
        private final byte[] png;
        private final String hash;

        public Tile(
                String path,
                int slot,
                Source source,
                byte[] png) {
            this(path, path, List.of(slot), source, png);
        }

        public Tile(
                String path,
                List<Integer> slots,
                Source source,
                byte[] png) {
            this(path, path, slots, source, png);
        }

        public Tile(
                String authoringPath,
                String bakedPath,
                int slot,
                Source source,
                byte[] png) {
            this(
                    authoringPath,
                    bakedPath,
                    List.of(slot),
                    source,
                    png);
        }

        public Tile(
                String authoringPath,
                String bakedPath,
                List<Integer> slots,
                Source source,
                byte[] png) {
            this.path = text(
                    authoringPath, "authoring tile path");
            this.bakedPath = text(
                    bakedPath, "baked tile path");
            ArrayList<Integer> orderedSlots =
                    new ArrayList<>(Objects.requireNonNull(slots, "slots"));
            if (orderedSlots.isEmpty()
                    || orderedSlots.stream().anyMatch(slot -> slot == null || slot < 0)
                    || orderedSlots.stream().distinct().count()
                            != orderedSlots.size()) {
                throw new IllegalArgumentException(
                        "tile slots must be non-empty, unique and non-negative");
            }
            orderedSlots.sort(Integer::compareTo);
            this.slots = List.copyOf(orderedSlots);
            this.source = Objects.requireNonNull(source, "source");
            this.png = Objects.requireNonNull(png, "png").clone();
            this.hash = sha256(this.png);
        }

        public String path() { return path; }
        public String bakedPath() { return bakedPath; }
        public List<Integer> slots() { return slots; }
        public Source source() { return source; }
        public byte[] png() { return png.clone(); }
        public String hash() { return hash; }

        public enum Source { MANUAL, GENERATED }
    }

    private static String text(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
