package com.kltyton.autoseamblend.texture.generation;

import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSet;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSetCatalog;
import com.kltyton.autoseamblend.reload.surface.InitialSurfacePreparation;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import java.util.List;
import java.util.Objects;

/** 中文：收集隔离的引擎计划，并构造一个完整生成精灵候选目录。 / English: Collects isolated engine plans and builds one complete generated-sprite candidate catalog. */
public final class GeneratedSpritePlanning {
    private static final GeneratedSpritePlannerRegistry<PlanningContext> INITIAL_PLANNERS =
            new GeneratedSpritePlannerRegistry<>();

    private GeneratedSpritePlanning() {}

    public static void register(
            String owner,
            InitialPlanner initialPlanner) {
        Objects.requireNonNull(initialPlanner, "initialPlanner");
        INITIAL_PLANNERS.register(
                owner,
                context -> initialPlanner.plan(
                        context.prepared(),
                        context.rules(),
                        context.planningView()));
    }

    public static GeneratedSpriteSetCatalog.Snapshot prepareInitial(
            InitialSurfacePreparation.Result prepared,
            RuleRuntime.Snapshot rules,
            ReloadPublication.Generation planningView) {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(
                planningView,
                "planningView");
        List<GeneratedSpriteSet> combined = INITIAL_PLANNERS.plan(
                new PlanningContext(prepared, rules, planningView));
        GeneratedSpriteSetCatalog.Snapshot catalog =
                GeneratedSpriteSetCatalog.prepare(
                        combined,
                        planningView.generation());
        return catalog;
    }

    public static boolean hasInitialPlanners() {
        return !INITIAL_PLANNERS.isEmpty();
    }

    private record PlanningContext(
            InitialSurfacePreparation.Result prepared,
            RuleRuntime.Snapshot rules,
            ReloadPublication.Generation planningView) {
        private PlanningContext {
            Objects.requireNonNull(prepared, "prepared");
            Objects.requireNonNull(rules, "rules");
            Objects.requireNonNull(planningView, "planningView");
        }
    }

    @FunctionalInterface
    public interface InitialPlanner {
        List<GeneratedSpriteSet> plan(
                InitialSurfacePreparation.Result prepared,
                RuleRuntime.Snapshot rules,
                ReloadPublication.Generation planningView);
    }
}
