package com.kltyton.autoseamblend.export.model;

public record GeneratedFile(String path, String sha256, long size) implements Comparable<GeneratedFile> {
    @Override
    public int compareTo(GeneratedFile other) { return path.compareTo(other.path); }
}
