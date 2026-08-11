package com.kltyton.autoseamblend.authoring.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringProject;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringRule;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ManagedAuthoringTemplatesTest {
    @Test
    void mcPatcherAutoStoresOnlyMinimalDefaultIntent() {
        String source = source(autoRule(true));

        assertEquals(
                "id=minecraft:stone\nmethod=auto\n",
                source);
        assertFalse(source.contains("matchBlocks="));
        assertFalse(source.contains("connectBlocks="));
        assertFalse(source.contains("tiles="));
    }

    @Test
    void mcPatcherAutoWritesOnlyExplicitNonCompatibilityOverride() {
        String source = source(autoRule(false));

        assertEquals(
                "id=minecraft:stone\nmethod=auto\ncompatibility=false\n",
                source);
    }

    @Test
    void explicitNativeMethodKeepsConcreteNativePredicates() {
        ManagedAuthoringRule rule = new ManagedAuthoringRule(
                "minecraft:glass",
                "minecraft:block/glass",
                "minecraft:block/glass",
                ConnectionMethod.CTM,
                ConnectionMethod.CTM,
                true,
                false,
                List.of("all"));

        String source = source(rule);

        assertTrue(source.contains("matchBlocks=minecraft:glass\n"));
        assertTrue(source.contains("connectBlocks=minecraft:glass\n"));
        assertTrue(source.contains("method=ctm\n"));
        assertTrue(source.contains("tiles="));
    }

    private static ManagedAuthoringRule autoRule(
            boolean compatibility) {
        return new ManagedAuthoringRule(
                "minecraft:stone",
                "minecraft:block/stone",
                "minecraft:block/stone",
                ConnectionMethod.AUTO,
                ConnectionMethod.OVERLAY,
                compatibility,
                false,
                List.of("all"));
    }

    private static String source(
            ManagedAuthoringRule rule) {
        ManagedAuthoringProject project =
                ManagedAuthoringTemplates.create(
                        EngineFamily.MCPATCHER,
                        List.of(rule));
        return new String(
                project.documents().get(0).content(),
                StandardCharsets.UTF_8);
    }
}
