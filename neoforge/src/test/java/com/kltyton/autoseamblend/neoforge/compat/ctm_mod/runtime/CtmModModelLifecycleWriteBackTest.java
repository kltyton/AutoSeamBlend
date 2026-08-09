package com.kltyton.autoseamblend.neoforge.compat.ctm_mod.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * 中文：验证 CtmModModelLifecycle 把装饰后的副本写回权威 event 模型表的最小回归测试。
 * 仅使用 JDK String/Map 驱动计划中的 package-private generic helper
 * {@link CtmModModelLifecycle#writeBackDecorated(Map, Map, Function)}，不链接
 * Minecraft/CTM 类型；语义对应 26.1.2 对权威表原地 replaceAll。
 *
 * <p>English: Minimal regression test proving CtmModModelLifecycle writes the decorated copy
 * back into the authoritative event model map. It drives the planned package-private generic
 * helper {@link CtmModModelLifecycle#writeBackDecorated(Map, Map, Function)} with plain JDK
 * String/Map and links no Minecraft/CTM types; semantics mirror 26.1.2's in-place replaceAll
 * on the authoritative map.
 */
class CtmModModelLifecycleWriteBackTest {

    @Test
    void writesDecoratedStateModelsBackIntoEventModelMap() {
        // 中文：装饰副本按 keyMapper 覆盖权威 map，无关键保持不变。
        // English: The decorated copy overrides the authoritative map via keyMapper
        // while unrelated keys stay unchanged.
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

        int written = CtmModModelLifecycle.writeBackDecorated(
                decorated,
                authoritative,
                keyMapper);

        assertEquals(2, written);
        assertSame(wrapperA, authoritative.get("model:a"));
        assertSame(wrapperB, authoritative.get("model:b"));
        assertSame("original:unrelated", authoritative.get("model:unrelated"));
        assertEquals(3, authoritative.size());
    }
}
