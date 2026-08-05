package com.kltyton.autoseamblend.runtime.publication;

import com.kltyton.autoseamblend.engine.routing.ModelOwnershipRuntime;
import com.kltyton.autoseamblend.engine.routing.NativeCaptureHealth;
import com.kltyton.autoseamblend.runtime.publication.NativeGenerationParticipants;
import com.kltyton.autoseamblend.runtime.publication.GenerationStaging;
import com.kltyton.autoseamblend.runtime.publication.ReloadGenerationCoordinator;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.PreparedSurfaceMethods;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSetCatalog;
import com.kltyton.autoseamblend.texture.atlas.ResolvedSpriteCatalog;
import com.kltyton.autoseamblend.runtime.publication.GenerationPublicationState;
import com.kltyton.autoseamblend.reload.rule.ManagedRuleSnapshot;
import com.kltyton.autoseamblend.reload.rule.NativeRuleSnapshot;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * 中文：把规则、表面、方法、模型所有权与生成精灵作为一个完整资源重载代次发布。
 *
 * English:
 * Publishes rules, surfaces, methods, model ownership, and generated sprites as one complete
 * resource-reload generation.
 */
public final class ReloadPublication {
    private static final ReloadGenerationCoordinator<Generation> COORDINATOR =
            new ReloadGenerationCoordinator<>(
                    Generation.bootstrap(),
                    generation -> new GenerationPublicationState.Marker(
                            generation.generation(), 0),
                    "stale reload token rejected before publication");
    private static PendingGeneration pending;

    private ReloadPublication() {}

    public static Generation current() {
        return COORDINATOR.current();
    }

    /**
     * 中文：在同一读锁内读取根快照与可选引擎查询探针，避免提交窗口中的旧新混合。
     *
     * English:
     * Reads the root snapshot and optional-engine query probes under one read lock so a commit
     * window cannot expose mixed generations.
     */
    public static <T> T read(
            Function<Generation, T> reader) {
        return COORDINATOR.read(reader);
    }

    public static long nextGeneration() {
        return COORDINATOR.nextGeneration();
    }

    /**
     * 中文：构造仅供同步精灵规划调用链显式传递的预发布视图；它不会写入任何全局状态。
     *
     * English:
     * Builds an unpublished view passed explicitly through the synchronous sprite-planning call
     * chain. It never mutates global state.
     */
    public static Generation planningView(
            long generation,
            NativeRuleSnapshot nativeRules,
            ManagedRuleSnapshot managedRules,
            RuleRuntime.Snapshot selectors,
            PreparedSurfaceMethods.Snapshot preparedMethods) {
        return new Generation(
                generation,
                nativeRules,
                managedRules,
                selectors,
                preparedMethods,
                GeneratedSpriteSetCatalog.Snapshot.empty(generation),
                MinecraftSurfaceCatalog.Snapshot.empty(generation),
                ModelOwnershipRuntime.Snapshot.empty(generation),
                NativeCaptureHealth.Snapshot.empty(generation),
                ResolvedSpriteCatalog.empty(generation));
    }

    public static Generation preparedGeneration(
            long generation,
            NativeRuleSnapshot nativeRules,
            ManagedRuleSnapshot managedRules,
            RuleRuntime.Snapshot selectors,
            PreparedSurfaceMethods.Snapshot preparedMethods,
            GeneratedSpriteSetCatalog.Snapshot generatedSprites) {
        return new Generation(
                generation,
                nativeRules,
                managedRules,
                selectors,
                preparedMethods,
                generatedSprites,
                MinecraftSurfaceCatalog.Snapshot.empty(generation),
                ModelOwnershipRuntime.Snapshot.empty(generation),
                NativeCaptureHealth.Snapshot.empty(generation),
                ResolvedSpriteCatalog.empty(generation));
    }

    /**
     * 中文：在所有纯准备工作成功后安装一个不可见候选；Atlas 与模型阶段只能读取该不可变值。
     *
     * English:
     * Installs an invisible candidate after all pure preparation succeeds. Atlas and model phases
     * can only read this immutable value.
     */
    public static void stagePreparedGeneration(
            Generation prepared) {
        Objects.requireNonNull(prepared, "prepared");
        requireAlignedPreparation(prepared);
        COORDINATOR.withWriteLock(() -> {
            COORDINATOR.validateNext(prepared);
            discardPendingLocked();
            pending = new PendingGeneration(prepared);
        });
    }

    /**
     * 中文：返回本次 Atlas 加载应消费的生成定义；没有候选时退回当前完整代次。
     *
     * English:
     * Returns generated definitions for the current atlas load, falling back to the active complete
     * generation when no candidate exists.
     */
    public static GeneratedSpriteSetCatalog.Snapshot atlasCatalog() {
        return COORDINATOR.read(active ->
            {
            return pending == null
                    ? active.generatedSprites()
                    : pending.prepared().generatedSprites();
            });
    }

    public static Optional<Generation> pendingPreparation() {
        return COORDINATOR.read(active ->
            {
            return pending == null
                    ? Optional.empty()
                    : Optional.of(pending.prepared());
            });
    }

    /** 中文：同一模型事件中的引擎装饰器读取刚完成的候选表面；已提交时自然退回根快照。 / English: Lets engine decorators in the same model event read the just-completed candidate surfaces, falling back naturally to the root snapshot after commit. */
    public static MinecraftSurfaceCatalog.Snapshot
            modelDecorationSurfaces() {
        return COORDINATOR.read(active ->
            {
            return pending != null
                            && pending.modelFacts()
                                    .isPresent()
                    ? pending.modelFacts()
                            .orElseThrow()
                            .surfaces()
                    : active.surfaces();
            });
    }

    public static void stageResolvedSprites(
            ResolvedSpriteCatalog resolution) {
        Objects.requireNonNull(resolution, "resolution");
        COORDINATOR.withWriteLock(() -> {
            if (pending == null) {
                Generation active = COORDINATOR.current();
                if (resolution.generation()
                        == active.generatedSprites()
                                .generation()) {
                    COORDINATOR.replaceSameGeneration(active.withResolvedSprites(resolution));
                }
                return;
            }
            requirePendingGeneration(
                    resolution.generation());
            pending = pending.withResolvedSprites(
                    resolution);
            publishIfCompleteLocked();
        });
    }

    public static void stageModelFacts(
            ModelOwnershipRuntime.PreparedCapture ownership,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        Objects.requireNonNull(ownership, "ownership");
        Objects.requireNonNull(surfaces, "surfaces");
        COORDINATOR.withWriteLock(() -> {
            requirePendingGeneration(
                    ownership.snapshot()
                            .generation());
            if (surfaces.generation()
                    != ownership.snapshot()
                            .generation()
                    || ownership.health()
                            .generation()
                            != surfaces.generation()) {
                throw new IllegalArgumentException(
                        "model facts must share one reload generation");
            }
            pending = pending.withModelFacts(
                    new ModelFacts(
                            ownership,
                            surfaces));
            publishIfCompleteLocked();
        });
    }

    /** 中文：无模型载体的原生捕获完成后重试同一提交屏障。 / English: Retries the same commit barrier after a native capture without a model carrier is prepared. */
    public static void nativeParticipantPrepared(
            long generation) {
        COORDINATOR.withWriteLock(() -> {
            if (pending != null
                    && pending.prepared()
                            .generation()
                            == generation) {
                publishIfCompleteLocked();
            }
        });
    }

    public static void discardPending(
            long generation) {
        COORDINATOR.withWriteLock(() -> {
            if (pending != null
                    && pending.prepared()
                            .generation()
                            == generation) {
                discardPendingLocked();
            }
        });
    }

    /** 中文：为非资源重载调用保留现有同步选择器刷新语义。 / English: Preserves synchronous selector refresh semantics for non-resource-reload callers. */
    public static RuleRuntime.Snapshot publishSelectors(
            RuleRuntime.Snapshot selectors) {
        Objects.requireNonNull(selectors, "selectors");
        return COORDINATOR.withWriteLock(() -> {
            discardPendingLocked();
            Generation active = COORDINATOR.current();
            GenerationPublicationState.alignSelectorCandidate(
                    new GenerationPublicationState.Marker(
                            active.generation(),
                            0),
                    selectors.generation());
            RuleRuntime.Snapshot aligned =
                    new RuleRuntime.Snapshot(
                            active.generation(),
                            selectors.rules(),
                            selectors.automaticDiscovery(),
                            selectors.selectorCount(),
                            selectors.publicationReason(),
                            selectors.diagnostics());
            COORDINATOR.replaceSameGeneration(active.withSelectors(aligned));
            return aligned;
        });
    }

    private static void publishIfCompleteLocked() {
        if (pending == null
                || !pending.stages().complete()
                || !NativeGenerationParticipants
                        .allPrepared(
                                pending.prepared()
                                        .generation())) {
            return;
        }
        ModelFacts modelFacts = pending.modelFacts()
                .orElseThrow();
        Generation prepared = pending.prepared();
        Generation next = new Generation(
                prepared.generation(),
                prepared.nativeRules(),
                prepared.managedRules(),
                prepared.selectors(),
                prepared.preparedMethods(),
                prepared.generatedSprites(),
                modelFacts.surfaces(),
                modelFacts.ownership()
                        .snapshot(),
                modelFacts.ownership()
                        .health(),
                pending.resolvedSprites()
                        .orElseThrow());
        COORDINATOR.commitNext(next);
        pending = null;
        MinecraftSurfaceCatalog.onPublished(
                next.surfaces());
    }

    private static void discardPendingLocked() {
        if (pending != null) {
            if (pending.modelFacts().isPresent()) {
                ModelOwnershipRuntime.abort(
                        pending.modelFacts()
                                .orElseThrow()
                                .ownership());
            }
            NativeGenerationParticipants.abort(
                    pending.prepared()
                            .generation());
        }
        pending = null;
    }

    private static void requirePendingGeneration(
            long generation) {
        if (pending == null) {
            throw new IllegalStateException(
                    "no prepared reload generation is available");
        }
        if (pending.prepared()
                        .generation()
                != generation) {
            throw new IllegalStateException(
                    "reload phase generation does not match the prepared candidate");
        }
    }

    private static void requireAlignedPreparation(
            Generation prepared) {
        long generation = prepared.generation();
        if (prepared.nativeRules().generation()
                        != generation
                || prepared.managedRules()
                                .generation()
                        != generation
                || prepared.selectors()
                                .generation()
                        != generation
                || prepared.preparedMethods()
                                .generation()
                        != generation
                || prepared.generatedSprites()
                                .generation()
                        != generation) {
            throw new IllegalArgumentException(
                    "prepared reload owners must share one generation");
        }
    }

    public record Generation(
            long generation,
            NativeRuleSnapshot nativeRules,
            ManagedRuleSnapshot managedRules,
            RuleRuntime.Snapshot selectors,
            PreparedSurfaceMethods.Snapshot preparedMethods,
            GeneratedSpriteSetCatalog.Snapshot generatedSprites,
            MinecraftSurfaceCatalog.Snapshot surfaces,
            ModelOwnershipRuntime.Snapshot modelOwnership,
            NativeCaptureHealth.Snapshot captureHealth,
            ResolvedSpriteCatalog resolvedSprites) {
        public Generation {
            if (generation < 0) {
                throw new IllegalArgumentException(
                        "reload generation must be non-negative");
            }
            Objects.requireNonNull(nativeRules, "nativeRules");
            Objects.requireNonNull(managedRules, "managedRules");
            Objects.requireNonNull(selectors, "selectors");
            Objects.requireNonNull(preparedMethods, "preparedMethods");
            Objects.requireNonNull(generatedSprites, "generatedSprites");
            Objects.requireNonNull(surfaces, "surfaces");
            Objects.requireNonNull(modelOwnership, "modelOwnership");
            Objects.requireNonNull(captureHealth, "captureHealth");
            Objects.requireNonNull(resolvedSprites, "resolvedSprites");
        }

        private static Generation bootstrap() {
            return new Generation(
                    0,
                    NativeRuleSnapshot.empty(),
                    ManagedRuleSnapshot.empty(),
                    RuleRuntime.bootstrapSnapshot(),
                    PreparedSurfaceMethods.Snapshot.empty(),
                    GeneratedSpriteSetCatalog.Snapshot.empty(),
                    MinecraftSurfaceCatalog.Snapshot.empty(),
                    ModelOwnershipRuntime.Snapshot.empty(),
                    NativeCaptureHealth.Snapshot.empty(),
                    ResolvedSpriteCatalog.empty());
        }

        private Generation withResolvedSprites(
                ResolvedSpriteCatalog resolution) {
            return new Generation(
                    generation,
                    nativeRules,
                    managedRules,
                    selectors,
                    preparedMethods,
                    generatedSprites,
                    surfaces,
                    modelOwnership,
                    captureHealth,
                    resolution);
        }

        private Generation withSelectors(
                RuleRuntime.Snapshot nextSelectors) {
            return new Generation(
                    generation,
                    nativeRules,
                    managedRules,
                    nextSelectors,
                    preparedMethods,
                    generatedSprites,
                    surfaces,
                    modelOwnership,
                    captureHealth,
                    resolvedSprites);
        }

    }

    private record ModelFacts(
            ModelOwnershipRuntime.PreparedCapture ownership,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        private ModelFacts {
            Objects.requireNonNull(ownership, "ownership");
            Objects.requireNonNull(surfaces, "surfaces");
        }
    }

    private record PendingGeneration(
            GenerationStaging<Generation, ModelFacts, ResolvedSpriteCatalog> staging) {
        private PendingGeneration {
            Objects.requireNonNull(staging, "staging");
            GenerationPublicationState.requireGeneration(
                    staging.stages().generation(),
                    staging.candidate().generation(),
                    "pending stages");
        }

        private PendingGeneration(Generation prepared) {
            this(GenerationStaging.empty(prepared, prepared.generation()));
        }

        private Generation prepared() {
            return staging.candidate();
        }

        private Optional<ModelFacts> modelFacts() {
            return staging.modelFacts();
        }

        private Optional<ResolvedSpriteCatalog> resolvedSprites() {
            return staging.resolvedSprites();
        }

        private GenerationPublicationState.StagedParts stages() {
            return staging.stages();
        }

        private PendingGeneration withModelFacts(
                ModelFacts facts) {
            return new PendingGeneration(staging.withModelFacts(facts));
        }

        private PendingGeneration withResolvedSprites(
                ResolvedSpriteCatalog resolution) {
            return new PendingGeneration(staging.withResolvedSprites(resolution));
        }
    }
}
