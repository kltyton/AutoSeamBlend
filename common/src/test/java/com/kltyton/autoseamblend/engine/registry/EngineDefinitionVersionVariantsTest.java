package com.kltyton.autoseamblend.engine.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.engine.EngineFamily;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 中文：同一家族在同一 Loader 上可由多个二进制实现提供时，发现门必须接受明确列出的版本，
 * 同时继续拒绝未审计版本。
 *
 * <p>English: When one engine family has multiple binary implementations on one loader, the
 * discovery gate accepts only explicitly audited versions and remains fail-closed otherwise.
 */
class EngineDefinitionVersionVariantsTest {
    private static final String CONTINUITY = "3.0.0+1.20.1.forge";
    private static final String CONSTANCY = "0.1.1+1.20.1.forge.build.4";

    @Test
    void acceptsEveryDeclaredImplementationVersionAndRejectsUnknownVersions() {
        EngineDefinition definition = definition();

        assertEquals(CONTINUITY, definition.descriptor().expectedVersion());
        assertEquals(List.of(CONTINUITY, CONSTANCY), definition.acceptedVersions());
        assertTrue(definition.acceptsVersion(CONTINUITY));
        assertTrue(definition.acceptsVersion(CONSTANCY));
        assertFalse(definition.acceptsVersion("3.0.1"));
    }

    @Test
    void catalogTreatsEitherAuditedImplementationAsLinkable() {
        EngineDefinitionCatalog catalog = EngineDefinitionCatalog.of(List.of(definition()));

        assertEquals(List.of("continuity"), catalog.linkableEngineIds(discovery(CONTINUITY)));
        assertEquals(List.of("continuity"), catalog.linkableEngineIds(discovery(CONSTANCY)));
        assertTrue(catalog.linkableEngineIds(discovery("3.0.1")).isEmpty());
    }

    private static EngineDefinition definition() {
        return EngineDefinition.ofVersions(
                "continuity",
                EngineFamily.MCPATCHER,
                "mcpatcher",
                List.of(CONTINUITY, CONSTANCY),
                "Continuity and Constancy processor lifecycle",
                "continuity/Hook.class");
    }

    private static EngineDiscovery discovery(String version) {
        return new EngineDiscovery() {
            @Override
            public Optional<String> installedVersion(String modId) {
                return Optional.of(version);
            }

            @Override
            public boolean hookPresent(String resourcePath) {
                return true;
            }
        };
    }
}
