package com.kltyton.autoseamblend.export.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

public final class SafeExportPaths {
    private SafeExportPaths() {}

    public static Path resolve(Path root, String relativePath) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(relativePath, "relativePath");
        if (relativePath.isBlank() || relativePath.indexOf('\\') >= 0 || relativePath.startsWith("/")
                || relativePath.endsWith("/") || relativePath.contains("//")) {
            throw new IllegalArgumentException("Unsafe export path: " + relativePath);
        }
        String[] segments = relativePath.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..") || segment.indexOf(':') >= 0) {
                throw new IllegalArgumentException("Unsafe export path: " + relativePath);
            }
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        rejectSymbolicLinks(normalizedRoot);
        Path resolved = normalizedRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Export path escapes root: " + relativePath);
        }
        rejectExistingChildLinks(normalizedRoot, resolved);
        return resolved;
    }

    public static void rejectSymbolicLinks(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        if (current == null) throw new IllegalArgumentException("Path has no root: " + path);
        for (Path component : absolute) {
            current = current.resolve(component);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IOException("Symbolic links are not allowed in export paths: " + current);
            }
        }
    }

    private static void rejectExistingChildLinks(Path root, Path resolved) throws IOException {
        Path current = root;
        for (Path component : root.relativize(resolved)) {
            current = current.resolve(component);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IOException("Symbolic links are not allowed in export paths: " + current);
            }
        }
    }
}
