package com.kltyton.autoseamblend.reload.texture;

import com.kltyton.autoseamblend.foundation.Constants;
import com.kltyton.autoseamblend.reload.rule.ManagedRuleRuntime;
import com.kltyton.autoseamblend.reload.rule.ManagedRuleSnapshot;
import com.kltyton.autoseamblend.reload.rule.NativeRuleRuntime;
import com.kltyton.autoseamblend.reload.rule.NativeRuleSnapshot;
import com.kltyton.autoseamblend.reload.surface.InitialSurfacePreparation;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.PreparedSurfaceMethods;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSetCatalog;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSource;
import com.kltyton.autoseamblend.texture.generation.GeneratedSpritePlanning;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;

/** 中文：在首次方块 Atlas 缝合前准备每个已安装引擎的生成精灵。 / English: Prepares every installed engine's generated sprites before the first block-atlas stitch. */
public final class InitialGeneratedSpritePreparation {
    private InitialGeneratedSpritePreparation() {}

    public static CompletableFuture<Void> prepare(
            ResourceManager resources,
            Executor executor) {
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(executor, "executor");
        if (!GeneratedSpritePlanning.hasInitialPlanners()) {
            return CompletableFuture.completedFuture(null);
        }
        long generation =
                ReloadPublication.nextGeneration();
        NativeRuleSnapshot nativeRules =
                NativeRuleRuntime.prepare(
                        resources,
                        generation);
        ManagedRuleSnapshot managedRules =
                ManagedRuleRuntime.prepare(
                                Minecraft.getInstance(),
                                generation)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Managed reload candidate is incomplete"));
        RuleRuntime.Snapshot rules =
                RuleRuntime.prepare(
                                "initial-resource-preparation",
                                generation)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "selector reload candidate is invalid"));
        return InitialSurfacePreparation.prepare(
                        resources,
                        executor,
                        source -> source instanceof GeneratedSpriteSource)
                .thenAcceptAsync(
                        prepared -> {
                            PreparedSurfaceMethods.Snapshot methods =
                                    PreparedSurfaceMethods.prepare(
                                            prepared,
                                            generation,
                                            "neoforge-pre-atlas-"
                                                    + generation);
                            ReloadPublication.Generation planningView =
                                    ReloadPublication.planningView(
                                            generation,
                                            nativeRules,
                                            managedRules,
                                            rules,
                                            methods);
                            GeneratedSpriteSetCatalog.Snapshot generated =
                                    GeneratedSpritePlanning.prepareInitial(
                                            prepared,
                                            rules,
                                            planningView);
                            ReloadPublication.stagePreparedGeneration(
                                    ReloadPublication.preparedGeneration(
                                            generation,
                                            nativeRules,
                                            managedRules,
                                            rules,
                                            methods,
                                            generated));
                            Constants.LOG.info(
                                    "Prepared in-memory connected-texture sprites before block-atlas stitch: surfaces={}, rejected={}",
                                    prepared.surfaces().size(),
                                    prepared.diagnostics().size());
                        },
                        executor);
    }
}
