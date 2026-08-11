package com.kltyton.autoseamblend.forge.compat.athena.runtime;

import java.util.Objects;
import java.util.function.Function;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;

/**
 * 中文：在 Forge 1.20.1 的模型烘焙事件期间暴露本次重载的 StitchResult 精灵解析器。
 *
 * <p>English: Exposes the current reload's StitchResult sprite resolver while Forge 1.20.1
 * dispatches its model-bake event.
 */
public final class ForgeModelBakeTextureContext {
    private static final ThreadLocal<Function<Material, TextureAtlasSprite>> CURRENT =
            new ThreadLocal<>();

    private ForgeModelBakeTextureContext() {}

    /**
     * 中文：在当前线程的有限作用域内安装解析器；finally 恢复此前状态。
     * English: Installs a resolver for a bounded thread-local scope and restores prior state.
     */
    public static void runWith(
            Function<Material, TextureAtlasSprite> getter,
            Runnable action) {
        Objects.requireNonNull(getter, "getter");
        Objects.requireNonNull(action, "action");
        Function<Material, TextureAtlasSprite> previous = CURRENT.get();
        CURRENT.set(getter);
        try {
            action.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /**
     * 中文：优先返回当前烘焙代次解析器；非烘焙路径使用调用方回退。
     * English: Returns the active bake resolver, falling back outside the bake scope.
     */
    static Function<Material, TextureAtlasSprite> currentOr(
            Function<Material, TextureAtlasSprite> fallback) {
        Objects.requireNonNull(fallback, "fallback");
        Function<Material, TextureAtlasSprite> current = CURRENT.get();
        return current == null ? fallback : current;
    }
}
