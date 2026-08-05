package com.kltyton.autoseamblend.authoring.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 中文：Loader 中立的 Managed 相对路径、大小写冲突和重解析点包含策略。
 *
 * English: Loader-neutral policy for Managed relative paths, case collisions,
 * and reparse-point containment.
 */
public final class ManagedPathPolicy {
    private static final Set<String> RESERVED = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9");

    private ManagedPathPolicy() {}

    public static String validateRelative(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Managed path is blank");
        }
        if (raw.indexOf('\0') >= 0
                || raw.indexOf('\n') >= 0
                || raw.indexOf('\r') >= 0
                || raw.indexOf('\t') >= 0
                || raw.startsWith("\\\\")
                || raw.startsWith("//")
                || raw.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Managed path is not relative: " + raw);
        }
        Path path = Path.of(raw.replace('\\', '/'));
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("Managed path is absolute: " + raw);
        }
        Path normalized = path.normalize();
        if (normalized.getNameCount() == 0 || normalized.startsWith("..")) {
            throw new IllegalArgumentException("Managed path escapes the workspace: " + raw);
        }
        for (Path part : normalized) {
            String segment = part.toString();
            if (segment.equals(".")
                    || segment.equals("..")
                    || segment.endsWith(" ")
                    || segment.endsWith(".")) {
                throw new IllegalArgumentException("Managed path has an unsafe segment: " + raw);
            }
            String stem = segment;
            int dot = stem.indexOf('.');
            if (dot >= 0) {
                stem = stem.substring(0, dot);
            }
            if (RESERVED.contains(stem.toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Managed path uses a reserved name: " + raw);
            }
        }
        return normalized.toString().replace('\\', '/');
    }

    public static Path resolveContained(
            Path resourcePacksRoot,
            Path workspaceRoot,
            String relative) throws IOException {
        Paths roots = roots(resourcePacksRoot, workspaceRoot);
        String safe = validateRelative(relative);
        Path target = roots.workspaceRoot().resolve(safe).normalize();
        if (!target.startsWith(roots.workspaceRoot())) {
            throw new IllegalArgumentException("Managed path escapes the workspace: " + relative);
        }
        rejectExistingLinkChain(roots.workspaceRoot(), target.getParent());
        return target;
    }

    public static void rejectCaseCollisions(Iterable<String> paths) {
        Map<String, String> spellings = new HashMap<>();
        for (String raw : paths) {
            String safe = validateRelative(raw);
            String folded = safe.toLowerCase(Locale.ROOT);
            String previous = spellings.putIfAbsent(folded, safe);
            if (previous != null && !previous.equals(safe)) {
                throw new IllegalArgumentException(
                        "case-colliding Managed paths: " + previous + " and " + safe);
            }
        }
    }

    public static void rejectWorkspaceRoot(
            Path resourcePacksRoot,
            Path workspaceRoot) throws IOException {
        Paths roots = roots(resourcePacksRoot, workspaceRoot);
        rejectDirectory(roots.resourcePacksRoot(), "resourcepacks root");
        if (Files.exists(roots.workspaceRoot(), LinkOption.NOFOLLOW_LINKS)) {
            rejectDirectory(roots.workspaceRoot(), "Managed workspace");
        }
    }

    private static Paths roots(Path resourcePacksRoot, Path workspaceRoot) {
        Path packs = java.util.Objects.requireNonNull(resourcePacksRoot, "resourcePacksRoot")
                .toAbsolutePath().normalize();
        Path workspace = java.util.Objects.requireNonNull(workspaceRoot, "workspaceRoot")
                .toAbsolutePath().normalize();
        if (!workspace.startsWith(packs) || workspace.equals(packs)) {
            throw new IllegalArgumentException("Managed workspace must be inside resourcepacks root");
        }
        return new Paths(packs, workspace);
    }

    private static void rejectExistingLinkChain(Path root, Path parent) throws IOException {
        if (parent == null || !parent.startsWith(root)) {
            throw new IllegalArgumentException("Managed parent escapes workspace");
        }
        rejectPathLink(root);
        Path current = root;
        for (Path segment : root.relativize(parent)) {
            current = current.resolve(segment);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            BasicFileAttributes attributes = Files.readAttributes(
                    current,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || attributes.isOther()) {
                throw new IOException("Managed path crosses a link or reparse point: " + current);
            }
            if (!attributes.isDirectory()) {
                throw new IOException("Managed parent is not a directory: " + current);
            }
        }
    }

    private static void rejectPathLink(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        BasicFileAttributes attributes = Files.readAttributes(
                path,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink() || attributes.isOther()) {
            throw new IOException("Managed workspace is a link or reparse point: " + path);
        }
    }

    private static void rejectDirectory(Path path, String label) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                path,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink() || attributes.isOther() || !attributes.isDirectory()) {
            throw new IOException(label + " is not a regular directory: " + path);
        }
    }

    private record Paths(Path resourcePacksRoot, Path workspaceRoot) {}
}
