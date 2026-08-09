package com.kltyton.autoseamblend.neoforge.compat.fusion.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
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
        Map<String, String> decorated = new HashMap<>();
        decorated.put("state:a", wrapperA);
        decorated.put("state:b", wrapperB);

        Map<String, String> authoritative = new HashMap<>();
        authoritative.put("model:a", "original:a");
        authoritative.put("model:unrelated", "original:unrelated");

        Function<String, String> keyMapper =
                state -> "model:" + state.substring("state:".length());

        int written = FusionModelLifecycle.writeBackDecorated(
                decorated,
                authoritative,
                keyMapper);

        assertEquals(2, written);
        assertSame(wrapperA, authoritative.get("model:a"));
        assertSame(wrapperB, authoritative.get("model:b"));
        assertSame(
                "original:unrelated",
                authoritative.get("model:unrelated"));
        assertEquals(3, authoritative.size());
    }
}
