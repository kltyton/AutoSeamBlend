package com.kltyton.autoseamblend.export.api;

import com.kltyton.autoseamblend.export.io.SafeExportPaths;
import com.kltyton.autoseamblend.export.model.GeneratedFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

public final class ExportSink {
    private final Path root;
    private final Set<String> claimedPaths = new HashSet<>();
    private final List<GeneratedFile> files = new ArrayList<>();

    public ExportSink(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public void writeUtf8(String relativePath, String content) throws IOException {
        write(relativePath, content.getBytes(StandardCharsets.UTF_8));
    }

    public void write(String relativePath, byte[] content) throws IOException {
        if (!claimedPaths.add(relativePath)) throw new IOException("Writer attempted duplicate path: " + relativePath);
        Path destination = SafeExportPaths.resolve(root, relativePath);
        Files.createDirectories(destination.getParent());
        Files.write(destination, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        files.add(new GeneratedFile(relativePath, sha256(content), content.length));
    }

    public List<GeneratedFile> files() {
        ArrayList<GeneratedFile> ordered = new ArrayList<>(files);
        Collections.sort(ordered);
        return List.copyOf(ordered);
    }

    public Path root() { return root; }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
