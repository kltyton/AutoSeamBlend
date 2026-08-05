package com.kltyton.autoseamblend.compat.fusion.runtime;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import net.minecraft.resources.Identifier;

/** 中文：Fusion compat 内的不可变原生代次。 / English: Immutable native Fusion generation. */
public record FusionNativeGeneration(
        long generation,
        Map<Identifier, FusionNativeTextureData> textures) {
    public FusionNativeGeneration {
        if (generation < 0) {
            throw new IllegalArgumentException("generation must be non-negative");
        }
        TreeMap<Identifier, FusionNativeTextureData> sorted = new TreeMap<>();
        Objects.requireNonNull(textures, "textures").forEach((identifier, texture) ->
                sorted.put(
                        Objects.requireNonNull(identifier, "identifier"),
                        Objects.requireNonNull(texture, "texture")));
        textures = Collections.unmodifiableMap(sorted);
    }
}
