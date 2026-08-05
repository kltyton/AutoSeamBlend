package com.kltyton.autoseamblend.compat.fusion.runtime;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.state.BlockState;

/** 中文：共享 Fusion modifier Mixin 的 Loader 生命周期端口。 / English: Loader lifecycle port for the shared Fusion modifier mixin. */
public final class FusionModifierLifecycleHooks {
    private static final Hooks EMPTY = new Hooks() {};
    private static final AtomicReference<Hooks> ACTIVE = new AtomicReference<>(EMPTY);

    private FusionModifierLifecycleHooks() {
    }

    public static void install(Hooks hooks) {
        Objects.requireNonNull(hooks, "hooks");
        if (!ACTIVE.compareAndSet(EMPTY, hooks)) {
            throw new IllegalStateException("Fusion modifier lifecycle hooks already installed");
        }
    }

    public static void begin(
            ResourceManager resources,
            Map<Identifier, Resource> documents) {
        ACTIVE.get().begin(
                Objects.requireNonNull(resources, "resources"),
                Map.copyOf(Objects.requireNonNull(documents, "documents")));
    }

    public static void publish(
            ModelBakery.BakingResult bakingResult,
            Map<BlockState, ? extends List<?>> modifiers) {
        ACTIVE.get().publish(
                Objects.requireNonNull(bakingResult, "bakingResult"),
                Objects.requireNonNull(modifiers, "modifiers"));
    }

    public interface Hooks {
        default void begin(
                ResourceManager resources,
                Map<Identifier, Resource> documents) {
        }

        default void publish(
                ModelBakery.BakingResult bakingResult,
                Map<BlockState, ? extends List<?>> modifiers) {
        }
    }
}
