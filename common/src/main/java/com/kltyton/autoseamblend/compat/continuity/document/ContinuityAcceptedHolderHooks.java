package com.kltyton.autoseamblend.compat.continuity.document;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import me.pepperbell.continuity.api.client.QuadProcessor;
import me.pepperbell.continuity.client.properties.BaseCtmProperties;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

/** 中文：共享 holder 接受 Mixin 的 Loader 发布端口。 / English: Loader publication port for the shared accepted-holder mixin. */
public final class ContinuityAcceptedHolderHooks {
    private static final Hooks EMPTY = (properties, processor, spriteGetter) -> {};
    private static final AtomicReference<Hooks> ACTIVE = new AtomicReference<>(EMPTY);

    private ContinuityAcceptedHolderHooks() {
    }

    public static void install(Hooks hooks) {
        Objects.requireNonNull(hooks, "hooks");
        if (!ACTIVE.compareAndSet(EMPTY, hooks)) {
            throw new IllegalStateException("Continuity accepted-holder hooks already installed");
        }
    }

    public static void accepted(
            BaseCtmProperties properties,
            QuadProcessor processor,
            Function<Identifier, TextureAtlasSprite> spriteGetter) {
        ACTIVE.get().accepted(
                Objects.requireNonNull(properties, "properties"),
                Objects.requireNonNull(processor, "processor"),
                Objects.requireNonNull(spriteGetter, "spriteGetter"));
    }

    @FunctionalInterface
    public interface Hooks {
        void accepted(
                BaseCtmProperties properties,
                QuadProcessor processor,
                Function<Identifier, TextureAtlasSprite> spriteGetter);
    }
}
