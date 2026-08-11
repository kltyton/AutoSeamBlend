package com.kltyton.autoseamblend.texture.generation;

import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 中文：按稳定注册顺序收集引擎计划，并验证每个生成精灵集合的所有者。
 * English: Collects engine plans in stable registration order and validates every generated-sprite owner.
 */
public final class GeneratedSpritePlannerRegistry<C> {
    private final Map<String, Planner<C>> planners = new LinkedHashMap<>();

    public synchronized void register(String owner, Planner<C> planner) {
        owner = requireOwner(owner);
        Objects.requireNonNull(planner, "planner");
        Planner<C> previous = planners.putIfAbsent(owner, planner);
        if (previous != null) {
            throw new IllegalStateException(
                    "generated sprite planner already registered for " + owner);
        }
    }

    public List<GeneratedSpriteSet> plan(C context) {
        Objects.requireNonNull(context, "context");
        List<Map.Entry<String, Planner<C>>> snapshot;
        synchronized (this) {
            snapshot = List.copyOf(planners.entrySet());
        }
        ArrayList<GeneratedSpriteSet> combined = new ArrayList<>();
        for (Map.Entry<String, Planner<C>> entry : snapshot) {
            List<GeneratedSpriteSet> definitions =
                    List.copyOf(entry.getValue().plan(context));
            for (GeneratedSpriteSet definition : definitions) {
                if (!entry.getKey().equals(definition.owner())) {
                    throw new IllegalStateException(
                            "generated sprite planner "
                                    + entry.getKey()
                                    + " returned owner "
                                    + definition.owner());
                }
            }
            combined.addAll(definitions);
        }
        return List.copyOf(combined);
    }

    public synchronized boolean isEmpty() {
        return planners.isEmpty();
    }

    public synchronized int size() {
        return planners.size();
    }

    private static String requireOwner(String value) {
        value = Objects.requireNonNull(value, "owner");
        if (value.isBlank()) {
            throw new IllegalArgumentException("owner must not be blank");
        }
        return value;
    }

    @FunctionalInterface
    public interface Planner<C> {
        List<GeneratedSpriteSet> plan(C context);
    }
}
