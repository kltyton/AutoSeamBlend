package com.kltyton.autoseamblend.forge.compat.athena.runtime;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.Function;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import org.junit.jupiter.api.Test;

/**
 * 中文：验证 Forge 1.20.1 模型烘焙事件可临时访问当前 StitchResult 的精灵，
 * 并且作用域结束或异常退出后不会把过期图集泄漏到后续重载。
 *
 * <p>English: Verifies that Forge 1.20.1 model-bake listeners can temporarily resolve
 * sprites from the current StitchResult without leaking a stale atlas getter after normal
 * completion or an exception.
 */
class ForgeModelBakeTextureContextTest {

    @Test
    void scopedGetterOverridesFallbackAndIsRestored() {
        Function<Material, TextureAtlasSprite> fallback = material -> null;
        Function<Material, TextureAtlasSprite> stitched = material -> null;

        assertSame(fallback, ForgeModelBakeTextureContext.currentOr(fallback));
        ForgeModelBakeTextureContext.runWith(
                stitched,
                () -> assertSame(
                        stitched,
                        ForgeModelBakeTextureContext.currentOr(fallback)));
        assertSame(fallback, ForgeModelBakeTextureContext.currentOr(fallback));
    }

    @Test
    void exceptionalExitClearsTheScopedGetter() {
        Function<Material, TextureAtlasSprite> fallback = material -> null;
        Function<Material, TextureAtlasSprite> stitched = material -> null;

        assertThrows(
                IllegalStateException.class,
                () -> ForgeModelBakeTextureContext.runWith(
                        stitched,
                        () -> {
                            throw new IllegalStateException("reload failed");
                        }));
        assertSame(fallback, ForgeModelBakeTextureContext.currentOr(fallback));
    }
}
