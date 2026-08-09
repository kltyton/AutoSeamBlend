package com.kltyton.autoseamblend.engine.query;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/** 中文：一个精确方块状态表面的引擎无关标识。 / English: Engine-neutral identity of one exact block-state surface. */
public record ExactSurfaceIdentity(
        String targetId,
        Map<String, String> stateIdentity,
        SurfaceFace face,
        String spriteId) {
    public ExactSurfaceIdentity {
        requireText(targetId, "targetId");
        TreeMap<String, String> sorted = new TreeMap<>(Objects.requireNonNull(stateIdentity, "stateIdentity"));
        sorted.forEach((name, value) -> {
            requireText(name, "state property name");
            requireText(value, "state property value");
        });
        stateIdentity = Collections.unmodifiableMap(sorted);
        Objects.requireNonNull(face, "face");
        requireText(spriteId, "spriteId");
    }

    public static ExactSurfaceIdentity from(ConnectionQuery query) {
        Objects.requireNonNull(query, "query");
        return new ExactSurfaceIdentity(
                query.blockId(), query.stateProperties(), query.face(), query.spriteId());
    }

    /**
     * 中文：从原生方块状态建立稳定的精确状态属性身份；属性名排序与查询身份保持一致。
     * English: Builds a stable exact state-property identity from a native block state; property
     * ordering matches query identities.
     */
    public static Map<String, String> stateIdentity(BlockState state) {
        Objects.requireNonNull(state, "state");
        TreeMap<String, String> values = new TreeMap<>();
        state.getValues().forEach((property, value) -> values.put(
                property.getName(),
                value.toString()));
        return Collections.unmodifiableMap(values);
    }

    /** 中文：返回原生方块状态的稳定注册表 ID。 / English: Returns the stable registry id of a native block state. */
    public static String blockId(BlockState state) {
        Objects.requireNonNull(state, "state");
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    /**
     * 中文：用原生方块状态建立完整精确表面身份。
     * English: Builds a complete exact surface identity from a native block state.
     */
    public static ExactSurfaceIdentity from(
            BlockState state,
            String targetId,
            SurfaceFace face,
            String spriteId) {
        return new ExactSurfaceIdentity(
                targetId,
                stateIdentity(state),
                face,
                spriteId);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
