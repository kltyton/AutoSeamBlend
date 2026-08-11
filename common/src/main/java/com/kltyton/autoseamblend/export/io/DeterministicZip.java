package com.kltyton.autoseamblend.export.io;

import com.kltyton.autoseamblend.texture.budget.TextureInputBudget;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class DeterministicZip {
    private static final long DOS_EPOCH_MILLIS = 315_532_800_000L;

    private DeterministicZip() {}

    public static void write(Path sourceDirectory, Path zipFile) throws IOException {
        List<Path> files;
        try (var stream = Files.walk(sourceDirectory)) {
            files = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> entryName(sourceDirectory, path)))
                    .toList();
        }
        try (ZipOutputStream output = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(
                zipFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)))) {
            for (Path file : files) {
                String entryName = entryName(sourceDirectory, file);
                byte[] content = readEntry(file, entryName);
                CRC32 crc = new CRC32();
                crc.update(content);
                ZipEntry entry = new ZipEntry(entryName);
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(content.length);
                entry.setCompressedSize(content.length);
                entry.setCrc(crc.getValue());
                entry.setTime(DOS_EPOCH_MILLIS);
                output.putNextEntry(entry);
                output.write(content);
                output.closeEntry();
            }
        }
    }

    /**
     * 中文：导出读取按条目后缀施加有界输入预算。
     *
     * English: Apply the bounded input budget by entry suffix while reading an export file.
     */
    private static byte[] readEntry(Path file, String entryName) throws IOException {
        return TextureInputBudget.DEFAULT.read(
                file,
                inputKind(entryName),
                "export-zip:" + entryName);
    }

    /**
     * 中文：PNG、pack 元数据和其他原生文档使用各自固定上限。
     *
     * English: PNGs, pack metadata, and other native documents use their fixed limits.
     */
    private static TextureInputBudget.InputKind inputKind(String entryName) {
        String normalized = entryName.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".png")) {
            return TextureInputBudget.InputKind.PNG;
        }
        if (normalized.endsWith(".mcmeta")) {
            return TextureInputBudget.InputKind.METADATA;
        }
        return TextureInputBudget.InputKind.NATIVE_DOCUMENT;
    }

    private static String entryName(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
