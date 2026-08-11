package com.kltyton.autoseamblend.compat.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.compat.athena.runtime.texture.AthenaGeneratedStateSprites;
import com.kltyton.autoseamblend.compat.continuity.runtime.texture.ContinuityGeneratedStateSprites;
import com.kltyton.autoseamblend.compat.ctm_mod.runtime.texture.CtmModGeneratedStateSprites;
import com.kltyton.autoseamblend.compat.fusion.runtime.texture.FusionGeneratedStateSprites;
import com.kltyton.autoseamblend.reload.rule.ManagedRuleSnapshot;
import com.kltyton.autoseamblend.reload.rule.NativeRuleSnapshot;
import com.kltyton.autoseamblend.reload.surface.InitialSurfacePreparation;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.PreparedSurfaceMethods;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSet;
import com.kltyton.autoseamblend.texture.atlas.InitialBlockAtlasResources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：锁定四个 generated-sprite planner 的 profile 去重/过滤前置契约：profile 构造必须按
 * source 实例在一次 planner 调用内缓存（computeIfAbsent），且所有不依赖 profile 的 eligibility
 * 过滤必须先于 InitialTextureProfileFactory.from(source) 执行；输出条目仍按原 key/顺序去重。
 *
 * <p>English: Locks the profile-dedup/filter-first contract of the four generated-sprite
 * planners: profile creation must be cached per source instance for one planner call
 * (computeIfAbsent), and all profile-independent eligibility filters must run before
 * InitialTextureProfileFactory.from(source); output entries keep their original key/order dedup.
 */
class GeneratedStateSpritesProfileDedupContractTest {
    private static final Pattern CACHE_DECLARATION =
            Pattern.compile(
                    "IdentityHashMap\\s*<\\s*SurfaceSourceSnapshot\\s*,\\s*"
                            + "InitialTextureProfiles\\s*>\\s*"
                            + "profilesBySource");
    private static final Pattern SINGLE_SOURCE_CREATION =
            Pattern.compile(
                    "computeIfAbsent\\s*\\(\\s*source\\s*,\\s*"
                            + "InitialTextureProfileFactory\\s*::\\s*from\\s*\\)");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void continuityPlannerDeduplicatesProfilesAfterEligibility() throws IOException {
        assertPlannerContract(
                "continuity",
                "requiresInitialPlan");
    }

    @Test
    void athenaPlannerDeduplicatesProfilesAfterEligibility() throws IOException {
        assertPlannerContract(
                "athena",
                "requiresGeneratedSprites");
    }

    @Test
    void ctmModPlannerDeduplicatesProfilesAfterEligibility() throws IOException {
        assertPlannerContract(
                "ctm_mod",
                "requiresGeneratedSprites");
    }

    @Test
    void fusionPlannerDeduplicatesProfilesAfterEligibility() throws IOException {
        assertPlannerContract(
                "fusion",
                "requiresGeneratedSprites");
    }

    @Test
    void emptyPreparedResultPlansStableEmptyLists() {
        InitialSurfacePreparation.Result prepared =
                new InitialSurfacePreparation.Result(
                        List.of(),
                        List.of(),
                        new InitialBlockAtlasResources.Snapshot(Map.of()),
                        List.of());
        RuleRuntime.Snapshot selectors =
                RuleRuntime.bootstrapSnapshot();
        ReloadPublication.Generation view =
                ReloadPublication.planningView(
                        1L,
                        NativeRuleSnapshot.empty(),
                        ManagedRuleSnapshot.empty(),
                        selectors,
                        PreparedSurfaceMethods.Snapshot.empty(1L));

        List<GeneratedSpriteSet> continuityFirst =
                ContinuityGeneratedStateSprites.planInitial(
                        prepared,
                        selectors,
                        view);
        List<GeneratedSpriteSet> continuitySecond =
                ContinuityGeneratedStateSprites.planInitial(
                        prepared,
                        selectors,
                        view);
        List<GeneratedSpriteSet> athenaFirst =
                AthenaGeneratedStateSprites.planInitial(
                        prepared,
                        selectors,
                        view);
        List<GeneratedSpriteSet> athenaSecond =
                AthenaGeneratedStateSprites.planInitial(
                        prepared,
                        selectors,
                        view);
        List<GeneratedSpriteSet> ctmFirst =
                CtmModGeneratedStateSprites.planInitial(
                        prepared,
                        selectors,
                        view);
        List<GeneratedSpriteSet> ctmSecond =
                CtmModGeneratedStateSprites.planInitial(
                        prepared,
                        selectors,
                        view);
        List<GeneratedSpriteSet> fusionFirst =
                FusionGeneratedStateSprites.planInitial(
                        prepared,
                        selectors,
                        view);
        List<GeneratedSpriteSet> fusionSecond =
                FusionGeneratedStateSprites.planInitial(
                        prepared,
                        selectors,
                        view);

        assertTrue(continuityFirst.isEmpty());
        assertEquals(continuityFirst, continuitySecond);
        assertTrue(athenaFirst.isEmpty());
        assertEquals(athenaFirst, athenaSecond);
        assertTrue(ctmFirst.isEmpty());
        assertEquals(ctmFirst, ctmSecond);
        assertTrue(fusionFirst.isEmpty());
        assertEquals(fusionFirst, fusionSecond);
    }

    private static void assertPlannerContract(
            String planner,
            String filterKeyword) throws IOException {
        String source = Files.readString(
                plannerSource(planner),
                StandardCharsets.UTF_8);

        assertTrue(
                CACHE_DECLARATION.matcher(source).find(),
                planner
                        + " planInitial must declare a method-local "
                        + "IdentityHashMap<SurfaceSourceSnapshot, InitialTextureProfiles>");

        Matcher creation = SINGLE_SOURCE_CREATION.matcher(source);
        assertTrue(
                creation.find(),
                planner
                        + " planInitial must create profiles via "
                        + "computeIfAbsent(source, InitialTextureProfileFactory::from)");
        assertFalse(
                creation.find(),
                planner
                        + " planInitial must create profiles at exactly one call site");

        int filterIndex = source.indexOf(filterKeyword);
        assertTrue(
                filterIndex >= 0,
                planner + " planInitial must retain " + filterKeyword);
        if (!"requiresInitialPlan".equals(filterKeyword)) {
            assertTrue(
                    source.indexOf("isGenerated") >= 0,
                    planner + " planInitial must retain isGenerated filtering");
        }
        int creationIndex = source.indexOf("computeIfAbsent(");
        assertTrue(
                filterIndex < creationIndex,
                planner
                        + " planInitial must run eligibility filters before profile creation");
    }

    private static Path plannerSource(String planner) {
        Path relative = Paths.get(
                "com/kltyton/autoseamblend/compat/"
                        + planner
                        + "/runtime/texture/"
                        + plannerName(planner)
                        + "GeneratedStateSprites.java");
        List<Path> candidates = List.of(
                Paths.get("src/main/java").resolve(relative),
                Paths.get("common/src/main/java").resolve(relative),
                Paths.get("1.21.1/AutoSeamBlend-1.21.1/common/src/main/java")
                        .resolve(relative));
        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElse(candidates.get(0));
    }

    private static String plannerName(String planner) {
        return switch (planner) {
            case "continuity" -> "Continuity";
            case "athena" -> "Athena";
            case "ctm_mod" -> "CtmMod";
            case "fusion" -> "Fusion";
            default -> throw new IllegalArgumentException(
                    "unknown planner " + planner);
        };
    }
}
