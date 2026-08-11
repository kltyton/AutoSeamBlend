package com.kltyton.autoseamblend.forge.compat.fusion.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * 中文：验证 1.21.1 Fusion 生命周期把装饰后的副本写回权威模型表。
 *
 * <p>English: Verifies that the 1.21.1 Fusion lifecycle writes the decorated copy
 * back into the authoritative model map.
 */
class FusionModelLifecycleWriteBackTest {

    @Test
    void writesDecoratedStateModelsBackIntoEventModelMap() {
        // 中文：装饰副本按 keyMapper 覆盖权威 map，无关键保持不变。
        // English: The decorated copy overrides the authoritative map through
        // keyMapper while unrelated keys remain unchanged.
        String wrapperA = "wrapper:a";
        String wrapperB = "wrapper:b";
        Map<ResourceLocation, String> decorated = new HashMap<>();
        decorated.put(new ResourceLocation("state:a"), wrapperA);
        decorated.put(new ResourceLocation("state:b"), wrapperB);

        Map<ResourceLocation, String> authoritative = new HashMap<>();
        authoritative.put(new ResourceLocation("model:a"), "original:a");
        authoritative.put(new ResourceLocation("model:unrelated"), "original:unrelated");

        Function<ResourceLocation, ResourceLocation> keyMapper =
                state -> new ResourceLocation(
                        "model:" + state.getPath());

        int written = FusionModelLifecycle.writeBackDecorated(
                decorated,
                authoritative,
                keyMapper);

        assertEquals(2, written);
        assertSame(wrapperA, authoritative.get(new ResourceLocation("model:a")));
        assertSame(wrapperB, authoritative.get(new ResourceLocation("model:b")));
        assertSame(
                "original:unrelated",
                authoritative.get(new ResourceLocation("model:unrelated")));
        assertEquals(3, authoritative.size());
    }
}
