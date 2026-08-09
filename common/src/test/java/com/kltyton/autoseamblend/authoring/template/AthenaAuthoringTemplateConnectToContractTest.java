package com.kltyton.autoseamblend.authoring.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringFile;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringRule;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 中文：锁定 Athena authoring 模板必须保留 26.1.2 已验收的 connect_to 同块连接合同，
 * 该合同在 Athena 4.0.6 原生 CtmUtils 中仍然受支持，不能随载体迁移丢失。
 *
 * <p>English: Locks the Athena authoring template to the 26.1.2-accepted connect_to
 * same-block connection contract, which Athena 4.0.6's native CtmUtils still
 * supports and must not be lost during the carrier migration.
 */
class AthenaAuthoringTemplateConnectToContractTest {

    @Test
    void athenaAuthoringDocumentKeepsSameBlockConnectContract() {
        ManagedAuthoringRule rule = new ManagedAuthoringRule(
                "minecraft:stone",
                "minecraft:block/stone",
                "minecraft:block/stone",
                ConnectionMethod.CTM,
                ConnectionMethod.CTM,
                true,
                false,
                List.of("all"));

        JsonObject document = document(rule);

        assertTrue(
                document.has("connect_to"),
                "authoring document must keep connect_to");
        JsonObject connectTo =
                document.getAsJsonObject("connect_to");
        assertEquals(
                "sameBlock",
                connectTo.get("type").getAsString());
    }

    private static JsonObject document(
            ManagedAuthoringRule rule) {
        ManagedAuthoringFile file = AthenaAuthoringTemplate
                .create(rule)
                .getFirst();
        return JsonParser.parseString(
                        new String(
                                file.content(),
                                StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
