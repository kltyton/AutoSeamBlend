package com.kltyton.autoseamblend.compat.athena.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kltyton.autoseamblend.engine.query.NativeDocumentIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * 中文：Athena 4.7.3 精确文档身份解析合同测试，由 1.21.1 a71485e 已验收测试移植。
 * 锁定键空间语义：blockId 派生 namespace:blockstates/<path>.json 文档身份；loaderIds
 * 顺序求值、首个非 null 命中即短路；无命中返回 empty。测试经 package-private 重载注入
 * 查找器，不链接 Athena impl 类，也不使用反射/mixin。
 *
 * <p>English: Contract tests for Athena 4.7.3 exact document-identity resolution, ported
 * from the accepted 1.21.1 a71485e tests. Locks the key-space semantics: the blockId
 * derives the namespace:blockstates/<path>.json document identity; loaderIds are evaluated
 * in order with the first non-null hit short-circuiting; no hit yields empty. Tests inject
 * the lookup through the package-private overload, never link Athena impl classes, and use
 * neither reflection nor mixins.
 */
class AthenaAcceptedDocumentIdentityContractTest {
    private static final Identifier CTM =
            Identifier.parse("athena:ctm");
    private static final Identifier PANE_CTM =
            Identifier.parse("athena:pane_ctm");

    @Test
    void firstNonNullLoaderHitResolvesBlockstateDocumentIdentity() {
        JsonObject hit = JsonParser.parseString(
                        "{\"athena:loader\":\"athena:ctm\"}")
                .getAsJsonObject();
        BiFunction<Identifier, Identifier, JsonObject>
                lookup = (loader, block) ->
                loader.equals(CTM) ? hit : null;

        Optional<NativeDocumentIdentity> identity =
                AthenaAcceptedDocumentIdentity.resolve(
                        Identifier.parse(
                                "minecraft:stone"),
                        List.of(CTM),
                        lookup);

        assertEquals(
                Optional.of(
                        NativeDocumentIdentity.resourceOnly(
                                "minecraft:blockstates/stone.json")),
                identity);
    }

    @Test
    void resolvesFirstHitWithoutConsultingLaterLoaders() {
        JsonObject hit = JsonParser.parseString("{}")
                .getAsJsonObject();
        ArrayList<Identifier> consulted =
                new ArrayList<>();
        BiFunction<Identifier, Identifier, JsonObject>
                lookup = (loader, block) -> {
            consulted.add(loader);
            return loader.equals(CTM) ? hit : null;
        };

        Optional<NativeDocumentIdentity> identity =
                AthenaAcceptedDocumentIdentity.resolve(
                        Identifier.parse(
                                "minecraft:stone"),
                        List.of(CTM, PANE_CTM),
                        lookup);

        assertEquals(
                List.of(CTM),
                consulted,
                "later loaders must not be consulted after the first hit");
        assertEquals(
                Optional.of(
                        NativeDocumentIdentity.resourceOnly(
                                "minecraft:blockstates/stone.json")),
                identity);
    }

    @Test
    void noNonNullHitReturnsEmpty() {
        BiFunction<Identifier, Identifier, JsonObject>
                lookup = (loader, block) -> null;

        Optional<NativeDocumentIdentity> identity =
                AthenaAcceptedDocumentIdentity.resolve(
                        Identifier.parse(
                                "minecraft:stone"),
                        List.of(CTM, PANE_CTM),
                        lookup);

        assertTrue(identity.isEmpty());
    }

    @Test
    void derivesNamespaceAndPathFromBlockId() {
        JsonObject hit = JsonParser.parseString("{}")
                .getAsJsonObject();

        Optional<NativeDocumentIdentity> identity =
                AthenaAcceptedDocumentIdentity.resolve(
                        Identifier.parse(
                                "example:glass"),
                        List.of(PANE_CTM),
                        (loader, block) -> hit);

        assertEquals(
                Optional.of(
                        NativeDocumentIdentity.resourceOnly(
                                "example:blockstates/glass.json")),
                identity);
    }
}
