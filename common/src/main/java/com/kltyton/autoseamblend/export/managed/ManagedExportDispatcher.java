package com.kltyton.autoseamblend.export.managed;

import com.kltyton.autoseamblend.export.api.ExportSink;
import com.kltyton.autoseamblend.export.io.DeterministicZip;
import com.kltyton.autoseamblend.export.io.SafeExportPaths;
import com.kltyton.autoseamblend.export.model.ExportDiagnostic;
import com.kltyton.autoseamblend.export.model.GeneratedFile;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/** 中文：暂存一个完整配置档，并仅在通过取消、错误和过期门禁后发布。 / English: Stages one complete profile and publishes only after cancellation/error/staleness gates pass. */
public final class ManagedExportDispatcher {
    private final ManagedExportWriter writer = new ManagedExportWriter();

    public Result dispatch(
            ManagedExportRequest request,
            ManagedExportIr ir,
            BooleanSupplier cancelled,
            GenerationGuard generationGuard) throws IOException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(ir, "ir");
        Objects.requireNonNull(cancelled, "cancelled");
        Objects.requireNonNull(generationGuard, "generationGuard");
        Path destination = request.destination();
        Path parent = destination.getParent();
        if (parent == null) throw new IOException("export destination has no parent");
        Files.createDirectories(parent);
        SafeExportPaths.rejectSymbolicLinks(parent);
        if (Files.exists(destination) && !request.overwrite()) {
            throw new FileAlreadyExistsException(destination.toString());
        }
        if (Files.isSymbolicLink(destination)) {
            throw new IOException("refusing symbolic-link export destination");
        }

        Path staging = Files.createTempDirectory(parent, ".autoseamblend-export-");
        Path stagedZip = null;
        try {
            ManagedExportWriter.WriteResult written = writer.write(
                    ir, request.profile(), new ExportSink(staging), cancelled);
            if (written.diagnostics().stream().anyMatch(
                    value -> value.level() == ExportDiagnostic.Level.ERROR)) {
                throw new ExportRejectedException(written.diagnostics());
            }
            if (cancelled.getAsBoolean()) throw new ManagedExportWriter.ExportCancelledException();
            if (!generationGuard.isCurrent(ir)) throw new StaleGenerationException();
            Path source = staging;
            if (request.zip()) {
                stagedZip = Files.createTempFile(parent, ".autoseamblend-export-", ".zip.tmp");
                Files.delete(stagedZip);
                DeterministicZip.write(staging, stagedZip);
                source = stagedZip;
            }
            if (cancelled.getAsBoolean()) throw new ManagedExportWriter.ExportCancelledException();
            if (!generationGuard.isCurrent(ir)) throw new StaleGenerationException();
            publish(source, destination, request.overwrite());
            if (request.zip()) deleteTree(staging);
            return new Result(destination, written.files(), written.diagnostics());
        } catch (IOException | RuntimeException exception) {
            deleteTree(staging);
            if (stagedZip != null) Files.deleteIfExists(stagedZip);
            throw exception;
        }
    }

    /**
     * 中文：在同一原子目标根目录下，为每个已解析引擎发布一个引擎原生资源包；请求 zip 时，每个引擎分区各生成一个 zip。
     *
     * English:
     * Publishes one engine-native pack per resolved engine under one atomic
     * destination root. A zip request produces one zip per engine partition.
     */
    public PartitionedResult dispatchPartitions(
            ManagedExportRequest request,
            List<ManagedExportIr> partitions,
            BooleanSupplier cancelled,
            GenerationGuard generationGuard)
            throws IOException {
        return dispatchPartitions(
                request,
                partitions,
                cancelled,
                generationGuard,
                () -> true);
    }

    /**
     * 中文：在最终外层发布前调用一次门禁，使调用方能够把取消与外部目标提交线性化。
     * English: Invokes one gate before the final outer publication so the caller can linearize
     * cancellation against committing the external destination.
     */
    public PartitionedResult dispatchPartitions(
            ManagedExportRequest request,
            List<ManagedExportIr> partitions,
            BooleanSupplier cancelled,
            GenerationGuard generationGuard,
            PublicationGate publicationGate)
            throws IOException {
        Objects.requireNonNull(request, "request");
        partitions = List.copyOf(Objects.requireNonNull(
                partitions, "partitions"));
        Objects.requireNonNull(cancelled, "cancelled");
        Objects.requireNonNull(generationGuard, "generationGuard");
        Objects.requireNonNull(publicationGate, "publicationGate");
        if (partitions.isEmpty()) {
            throw new IOException(
                    "partitioned export requires at least one resolved engine");
        }
        LinkedHashMap<String, ManagedExportIr> byEngine =
                new LinkedHashMap<>();
        for (ManagedExportIr ir : partitions) {
            String engine = ir.engine();
            if (!engine.matches("[a-z0-9_-]+")) {
                throw new IOException(
                        "unsafe engine partition name: " + engine);
            }
            if (byEngine.putIfAbsent(engine, ir) != null) {
                throw new IOException(
                        "duplicate engine partition: " + engine);
            }
        }

        Path destination = request.destination();
        Path parent = destination.getParent();
        if (parent == null) {
            throw new IOException(
                    "partitioned export destination has no parent");
        }
        Files.createDirectories(parent);
        SafeExportPaths.rejectSymbolicLinks(parent);
        if (Files.exists(destination) && !request.overwrite()) {
            throw new FileAlreadyExistsException(
                    destination.toString());
        }
        if (Files.isSymbolicLink(destination)) {
            throw new IOException(
                    "refusing symbolic-link export destination");
        }

        Path staging = Files.createTempDirectory(
                parent, ".autoseamblend-partitioned-");
        LinkedHashMap<String, Result> results =
                new LinkedHashMap<>();
        try {
            for (Map.Entry<String, ManagedExportIr> entry :
                    byEngine.entrySet()) {
                if (cancelled.getAsBoolean()) {
                    throw new ManagedExportWriter
                            .ExportCancelledException();
                }
                String suffix = request.zip() ? ".zip" : "";
                ManagedExportRequest partitionRequest =
                        new ManagedExportRequest(
                                request.profile(),
                                staging.resolve(
                                        entry.getKey() + suffix),
                                request.zip(),
                                false);
                Result result = dispatch(
                        partitionRequest,
                        entry.getValue(),
                        cancelled,
                        generationGuard);
                results.put(entry.getKey(), result);
            }
            if (cancelled.getAsBoolean()) {
                throw new ManagedExportWriter
                        .ExportCancelledException();
            }
            for (ManagedExportIr ir : byEngine.values()) {
                if (!generationGuard.isCurrent(ir)) {
                    throw new StaleGenerationException();
                }
            }
            if (!publicationGate.beginPublication()) {
                throw new ManagedExportWriter
                        .ExportCancelledException();
            }
            publish(staging, destination, request.overwrite());
            LinkedHashMap<String, Result> published =
                    new LinkedHashMap<>();
            String suffix = request.zip() ? ".zip" : "";
            results.forEach((engine, result) ->
                    published.put(
                            engine,
                            new Result(
                                    destination.resolve(
                                            engine + suffix),
                                    result.files(),
                                    result.diagnostics())));
            return new PartitionedResult(
                    destination, published);
        } catch (IOException | RuntimeException exception) {
            deleteTree(staging);
            throw exception;
        }
    }

    private static void publish(Path source, Path destination, boolean overwrite) throws IOException {
        Path backup = null;
        try {
            if (overwrite && Files.exists(destination)) {
                backup = Files.createTempFile(destination.getParent(), ".autoseamblend-backup-", ".tmp");
                Files.delete(backup);
                atomicMove(destination, backup);
            }
            atomicMove(source, destination);
            if (backup != null) deleteTree(backup);
        } catch (IOException failure) {
            if (backup != null && Files.exists(backup)) atomicMove(backup, destination);
            throw failure;
        }
    }

    private static void atomicMove(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("atomic export publication is unavailable", exception);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        if (Files.isRegularFile(root)) {
            Files.delete(root);
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted((left, right) ->
                    Integer.compare(right.getNameCount(), left.getNameCount())).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @FunctionalInterface
    public interface GenerationGuard {
        boolean isCurrent(ManagedExportIr ir) throws IOException;
    }

    /** 中文：最终外层发布的单次提交门禁。 / English: One-shot commit gate for the final outer publication. */
    @FunctionalInterface
    public interface PublicationGate {
        boolean beginPublication();
    }

    public record Result(
            Path destination,
            List<GeneratedFile> files,
            List<ExportDiagnostic> diagnostics) {
        public Result {
            files = List.copyOf(files);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    public record PartitionedResult(
            Path destination,
            Map<String, Result> partitions) {
        public PartitionedResult {
            Objects.requireNonNull(destination, "destination");
            partitions = Collections.unmodifiableMap(
                    new LinkedHashMap<>(
                            Objects.requireNonNull(
                                    partitions, "partitions")));
        }
    }

    public static final class ExportRejectedException extends IOException {
        private final List<ExportDiagnostic> diagnostics;

        public ExportRejectedException(List<ExportDiagnostic> diagnostics) {
            super(message(diagnostics));
            this.diagnostics = List.copyOf(diagnostics);
        }

        public List<ExportDiagnostic> diagnostics() { return diagnostics; }

        private static String message(List<ExportDiagnostic> diagnostics) {
            String details = diagnostics.stream()
                    .filter(value -> value.level() == ExportDiagnostic.Level.ERROR)
                    .map(value -> value.code() + '[' + value.groupId() + ']')
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(", "));
            return details.isEmpty()
                    ? "managed export rejected by error diagnostics"
                    : "managed export rejected by error diagnostics: " + details;
        }
    }

    public static final class StaleGenerationException extends IOException {
        public StaleGenerationException() { super("runtime or managed generation changed during export"); }
    }
}
