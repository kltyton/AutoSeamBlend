package com.kltyton.autoseamblend.reload.surface;

import com.kltyton.autoseamblend.inference.InferenceFacts;
import com.kltyton.autoseamblend.mixin.minecraft.ModelManagerInvoker;
import com.kltyton.autoseamblend.runtime.surface.SurfacePreparationDomain;
import com.kltyton.autoseamblend.runtime.surface.SurfacePreparationDomain.FaceInput;
import com.kltyton.autoseamblend.runtime.surface.SurfacePreparationDomain.StateInput;
import com.kltyton.autoseamblend.runtime.surface.SurfaceSourceSnapshot;
import com.kltyton.autoseamblend.runtime.surface.ModelGeometryInspector;
import com.kltyton.autoseamblend.engine.query.SurfaceFace;
import com.kltyton.autoseamblend.texture.atlas.InitialBlockAtlasResources.Snapshot;

import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BlockModelDefinition;
import net.minecraft.client.renderer.block.model.MultiVariant;
import net.minecraft.client.renderer.block.model.Variant;
import net.minecraft.client.renderer.block.model.multipart.Selector;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 中文：在 Atlas 缝合前从原始资源解析源精灵和完整推断事实。
 * <p>
 * English:
 * Resolves source sprites and complete inference facts from raw resources before atlas stitching.
 */
public final class InitialSurfacePreparation {
    private static final int MIN_STATES_PER_TASK = 512;
    private static final int MAX_INSPECTION_TASKS = 8;
    private static final int INSPECTION_PARALLELISM = Math.max(
            1,
            Math.min(
                    MAX_INSPECTION_TASKS,
                    Runtime.getRuntime().availableProcessors()));

    private InitialSurfacePreparation() {
    }

    public static CompletableFuture<Result> prepare(
            ResourceManager resources,
            Executor executor,
            Predicate<SpriteSource> generatedSourcePredicate) {
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(generatedSourcePredicate, "generatedSourcePredicate");
        CompletableFuture<Map<ResourceLocation, BlockModel>> blockModels =
                ModelManagerInvoker.autoseamblend$loadBlockModels(resources, executor)
                        .thenApply(InitialSurfacePreparation::normalizeModelKeys);
        CompletableFuture<Map<ResourceLocation, List<ModelBakery.LoadedJson>>> blockStates =
                ModelManagerInvoker.autoseamblend$loadBlockStates(resources, executor);
        CompletableFuture<Snapshot> atlas =
                CompletableFuture.supplyAsync(
                        () -> com.kltyton.autoseamblend.texture.atlas.InitialBlockAtlasResources.capture(
                                resources,
                                generatedSourcePredicate),
                        executor);
        return blockModels
                .thenCombine(blockStates, Models::new)
                .thenCombine(
                        atlas,
                        ResolutionInputs::new)
                .thenComposeAsync(
                        inputs -> resolveAsync(
                                inputs.models().blockModels(),
                                inputs.models().blockStateJson(),
                                inputs.atlas(),
                                executor),
                        executor);
    }

    /**
     * 中文：1.20.1 的 ModelManager.loadBlockModels 直接使用 FileToIdConverter.listMatchingResources
     * 的原始键（如 "minecraft:models/block/cube_all.json"），而本域后续全部按模型 ID
     * （"minecraft:block/cube_all"）查询，因此在这里统一转换为 ID 键。
     *
     * English: In 1.20.1 ModelManager.loadBlockModels keeps FileToIdConverter.listMatchingResources
     * raw keys (e.g. "minecraft:models/block/cube_all.json"), while this domain queries models by
     * model ID ("minecraft:block/cube_all"), so keys are normalized to IDs here.
     */
    private static Map<ResourceLocation, BlockModel> normalizeModelKeys(
            Map<ResourceLocation, BlockModel> raw) {
        Objects.requireNonNull(raw, "raw");
        if (raw.isEmpty()) {
            return raw;
        }
        Map<ResourceLocation, BlockModel> normalized =
                new HashMap<>(raw.size());
        for (Map.Entry<ResourceLocation, BlockModel> entry
                : raw.entrySet()) {
            normalized.put(
                    ModelBakery.MODEL_LISTER.fileToId(
                            entry.getKey()),
                    entry.getValue());
        }
        return normalized;
    }

    private static CompletableFuture<Result> resolveAsync(
            Map<ResourceLocation, BlockModel> blockModels,
            Map<ResourceLocation, List<ModelBakery.LoadedJson>> blockStateJson,
            Snapshot atlas,
            Executor executor) {
        List<StateModelEntry> entries = resolveStateModels(
                blockModels,
                blockStateJson);
        resolveModelParents(blockModels);
        // 中文：同一资源重载内的 reload-local 几何缓存；key=dependency 仅在
        // blockModels/atlas 冻结边界内成立，跨代次不得复用。
        // English: reload-local geometry cache for one resource reload; the dependency key is
        // valid only inside the frozen blockModels/atlas boundary and must not be reused
        // across generations.
        ConcurrentHashMap<ResourceLocation, ModelGeometryInspector.Result> geometryCache =
                new ConcurrentHashMap<>();
        if (entries.isEmpty()) {
            return CompletableFuture.completedFuture(
                    new Result(List.of(), List.of(), atlas, List.of()));
        }
        int taskCount = Math.min(
                INSPECTION_PARALLELISM,
                Math.max(
                        1,
                        (entries.size() + MIN_STATES_PER_TASK - 1)
                                / MIN_STATES_PER_TASK));
        int batchSize = (entries.size() + taskCount - 1)
                / taskCount;
        ArrayList<CompletableFuture<InspectionBatch>> tasks =
                new ArrayList<>(taskCount);
        for (int start = 0; start < entries.size(); start += batchSize) {
            int from = start;
            int to = Math.min(entries.size(), start + batchSize);
            tasks.add(CompletableFuture.supplyAsync(
                    () -> inspectBatch(
                            entries,
                            from,
                            to,
                            blockModels,
                            geometryCache,
                            atlas),
                    executor));
        }
        return CompletableFuture
                .allOf(tasks.toArray(CompletableFuture<?>[]::new))
                .thenApply(ignored -> merge(tasks, atlas));
    }

    private static InspectionBatch inspectBatch(
            List<StateModelEntry> entries,
            int from,
            int to,
            Map<ResourceLocation, BlockModel> blockModels,
            ConcurrentHashMap<ResourceLocation, ModelGeometryInspector.Result> geometryCache,
            Snapshot atlas) {
        ArrayList<Surface> surfaces = new ArrayList<>();
        ArrayList<StateCandidate> candidates =
                new ArrayList<>(to - from);
        ArrayList<String> diagnostics = new ArrayList<>();
        for (int index = from; index < to; index++) {
            StateModelEntry entry =
                    entries.get(index);
            StateInspection inspection = inspectState(
                    entry.state(),
                    entry.models(),
                    blockModels,
                    geometryCache,
                    atlas);
            surfaces.addAll(inspection.surfaces());
            candidates.add(inspection.candidate());
            inspection.diagnostics().forEach(reason ->
                    diagnostics.add("INITIAL_SURFACE_EVIDENCE:"
                            + entry.state()
                            + ':'
                            + reason));
        }
        return new InspectionBatch(
                surfaces,
                candidates,
                diagnostics);
    }

    private static Result merge(
            List<CompletableFuture<InspectionBatch>> tasks,
            Snapshot atlas) {
        ArrayList<Surface> surfaces = new ArrayList<>();
        ArrayList<StateCandidate> candidates = new ArrayList<>();
        ArrayList<String> diagnostics = new ArrayList<>();
        for (CompletableFuture<InspectionBatch> task : tasks) {
            InspectionBatch batch = task.join();
            surfaces.addAll(batch.surfaces());
            candidates.addAll(batch.candidates());
            diagnostics.addAll(batch.diagnostics());
        }
        return new Result(
                surfaces,
                candidates,
                atlas,
                diagnostics);
    }

    private static StateInspection inspectState(
            BlockState state,
            List<ResourceLocation> modelLocations,
            Map<ResourceLocation, BlockModel> blockModels,
            ConcurrentHashMap<ResourceLocation, ModelGeometryInspector.Result> geometryCache,
            Snapshot atlas) {
        ArrayList<String> evidence = new ArrayList<>();
        if (modelLocations.isEmpty()) {
            evidence.add("BLOCKSTATE_ROOT_HAS_NO_MODEL_DEPENDENCIES");
            return StateInspection.unresolved(state, evidence);
        }
        ArrayList<Draft> drafts = new ArrayList<>();
        boolean completeModelGeometry = true;
        boolean completeSurfaceEvidence = true;
        LinkedHashSet<ResourceLocation> visited = new LinkedHashSet<>();
        for (ResourceLocation dependency : modelLocations) {
            if (!visited.add(dependency)) {
                continue;
            }
            BlockModel model = blockModels.get(dependency);
            if (model == null) {
                completeModelGeometry = false;
                completeSurfaceEvidence = false;
                evidence.add("UNRESOLVED_MODEL_SKIPPED:" + dependency);
                continue;
            }
            if (model.getElements().isEmpty()) {
                completeModelGeometry = false;
                completeSurfaceEvidence = false;
                evidence.add("CUSTOM_MODEL_DEFERRED_TO_BAKED_SURFACE:" + dependency);
                continue;
            }
            GeometryInspection inspected = inspectGeometry(
                    dependency,
                    model,
                    geometryCache,
                    atlas);
            drafts.addAll(inspected.drafts());
            evidence.addAll(inspected.diagnostics());
            completeSurfaceEvidence &= inspected.complete();
        }
        if (drafts.isEmpty()) {
            evidence.add("NO_RESOLVED_CUBOID_SURFACES");
            return StateInspection.unresolved(state, evidence);
        }
        List<FaceInput> faces = drafts.stream()
                .map(draft -> {
                    return new FaceInput(
                            SurfaceFace.valueOf(draft.direction().name()),
                            draft.source(),
                            draft.fullBlock(),
                            draft.axisAligned(),
                            draft.fullFace(),
                            draft.validUv(),
                            draft.tintIndex());
                })
                .toList();
        SurfacePreparationDomain.StateInspection inspected =
                SurfacePreparationDomain.inspect(new StateInput(
                        state.toString(),
                        state.toString(),
                        faces,
                        completeModelGeometry,
                        completeSurfaceEvidence,
                        evidence));
        List<Surface> surfaces = inspected.surfaces().stream()
                .map(surface -> new Surface(
                        state,
                        Direction.valueOf(surface.face().name()),
                        surface.source(),
                        surface.inferenceFacts()))
                .toList();
        CandidateStatus status = CandidateStatus.valueOf(
                inspected.candidate().status().name());
        return new StateInspection(
                surfaces,
                new StateCandidate(
                        state,
                        status,
                        inspected.candidate().evidence()),
                inspected.diagnostics());
    }

    // 中文：包私有仅用于 InitialSurfacePreparationGeometryCacheContractTest 的缓存计数回归
    // （测试脚手架）；缓存解析逻辑本身不属于测试专用实现。
    // English: Package-private only for the InitialSurfacePreparationGeometryCacheContractTest
    // caching-count regression (test scaffolding); the caching logic is not test-only.
    static GeometryInspection inspectGeometry(
            ResourceLocation dependency,
            BlockModel model,
            ConcurrentHashMap<ResourceLocation, ModelGeometryInspector.Result> geometryCache,
            Snapshot atlas) {
        ModelGeometryInspector.Result inspected = geometryCache.computeIfAbsent(
                dependency,
                key -> ModelGeometryInspector.inspect(
                        key,
                        model,
                        atlas));
        List<Draft> drafts = inspected.faces().stream()
                .map(face -> new Draft(
                        face.direction(),
                        face.source(),
                        face.fullBlock(),
                        face.axisAligned(),
                        face.fullFace(),
                        face.validUv(),
                        face.tintIndex()))
                .toList();
        return new GeometryInspection(
                drafts,
                inspected.complete(),
                inspected.diagnostics());
    }

    /**
     * 中文：按 1.20.1 原版 ModelBakery 语义解析：每个 blockstate 原始键先经
     * ModelBakery.BLOCKSTATE_LISTER.fileToId 归一化为方块 ID，再与
     * BlockModelShaper.stateToModelLocation 的 namespace/path 匹配；非 multipart 定义按
     * ModelBakery.predicate 的属性子集语义编译 variant 键谓词。多个 LoadedJson 层按
     * per-layer state putAll/replace 合并：每层在全新 per-state 映射内编译，命中的状态
     * 覆盖低层、未命中保留低层，而不是按 union 累加；multipart 层例外：先为该 block
     * 全部 possible states 建本层条目，selector 无命中的状态也以空依赖覆盖低层。
     *
     * English: Follows vanilla 1.20.1 ModelBakery semantics: each raw blockstate key is
     * normalized to its block ID via ModelBakery.BLOCKSTATE_LISTER.fileToId before matching
     * against the ID-keyed BlockModelShaper.stateToModelLocation; non-multipart definitions
     * compile variant keys as property-subset predicates per ModelBakery.predicate.
     * Multiple LoadedJson layers merge by per-layer state putAll/replace: each layer compiles
     * into a fresh per-state map, matched states replace the lower layer and unmatched states
     * retain it, instead of a union accumulation; a multipart layer is the exception: it first
     * creates an entry for every possible state of the block, and states no selector matches
     * are covered by an empty dependency list overriding the lower layer.
     *
     * 中文：包私有仅用于 InitialSurfacePreparationStateModelKeyContractTest 的
     * 行为回归（测试脚手架）；匹配逻辑本身不属于测试专用实现。
     * English: Package-private only for the InitialSurfacePreparationStateModelKeyContractTest
     * behavioral regression (test scaffolding); the matching logic is not test-only.
     */
    static List<StateModelEntry> resolveStateModels(
            Map<ResourceLocation, BlockModel> blockModels,
            Map<ResourceLocation, List<ModelBakery.LoadedJson>> blockStateJson) {
        LinkedHashMap<BlockState, LinkedHashSet<ResourceLocation>> modelIdsByState =
                new LinkedHashMap<>();
        Map<ResourceLocation, Block> blockById =
                new HashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                modelIdsByState.put(
                        state,
                        new LinkedHashSet<>());
            }
            blockById.put(
                    BuiltInRegistries.BLOCK.getKey(block),
                    block);
        }
        // 1.20.1 has no BlockStateModelLoader; replicate vanilla ModelBakery semantics by
        // parsing each blockstate definition, compiling variant-key predicates once per loaded
        // definition, then applying them to that block's states; multipart definitions keep the
        // selector-based matching.
        for (Map.Entry<ResourceLocation, List<ModelBakery.LoadedJson>> stateEntry
                : blockStateJson.entrySet()) {
            // 中文：1.20.1 ModelManager.loadBlockStates 保留原始键
            // （minecraft:blockstates/<id>.json）；与 vanilla ModelBakery 相同，
            // 先归一化为方块 ID 再与 stateToModelLocation 匹配。
            // English: 1.20.1 ModelManager.loadBlockStates keeps raw keys
            // (minecraft:blockstates/<id>.json); like vanilla ModelBakery, normalize to
            // the block ID before matching stateToModelLocation.
            ResourceLocation stateId =
                    ModelBakery.BLOCKSTATE_LISTER.fileToId(
                            stateEntry.getKey());
            Block block = blockById.get(stateId);
            if (block == null) {
                continue;
            }
            StateDefinition<Block, BlockState> stateDefinition =
                    block.getStateDefinition();
            List<BlockState> possibleStates =
                    stateDefinition.getPossibleStates();
            BlockModelDefinition.Context context =
                    new BlockModelDefinition.Context();
            context.setDefinition(stateDefinition);
            for (ModelBakery.LoadedJson loaded : stateEntry.getValue()) {
                BlockModelDefinition definition;
                try {
                    definition = BlockModelDefinition.fromJsonElement(
                            context,
                            loaded.data());
                } catch (RuntimeException exception) {
                    // 中文：畸形 LoadedJson 在当前 AutoSeamBlend 域按宽松策略跳过该层，
                    // 不中断整个 reload；vanilla 1.20.1 会抛出 BlockStateDefinitionException
                    // 并让该方块整体回退 missing。此处刻意不扩大到 vanilla 的整块回退。
                    // English: a malformed LoadedJson is leniently skipped for this layer in
                    // AutoSeamBlend without aborting the reload; vanilla 1.20.1 throws
                    // BlockStateDefinitionException and falls the whole block back to missing.
                    // We deliberately keep the lenient skip instead of widening behavior.
                    continue;
                }
                // 中文：per-layer state putAll/replace（对应原版 ModelBakery 的
                // blockStateModelMap.putAll(layerMap)）：每层在全新 per-state 映射内编译，
                // 层内 overlap 只毒化该层状态，merge 时命中的状态覆盖低层、未命中保留
                // 低层，而不是按 union 累加；高层干净定义可覆盖低层被毒化的状态。
                // multipart 层例外：先为该 block 全部 possible states 建本层条目，
                // 未命中的状态以空依赖覆盖低层。
                // English: per-layer state putAll/replace (vanilla ModelBakery's
                // blockStateModelMap.putAll(layerMap)): each layer compiles into a fresh
                // per-state map, overlaps poison only that layer's state, and the merge
                // replaces matched states while retaining unmatched lower-layer states,
                // instead of a union accumulation; a clean higher layer can override a state
                // poisoned in a lower layer. A multipart layer is the exception: it first
                // creates an entry for every possible state, and unmatched states are covered
                // by an empty dependency list overriding the lower layer.
                Map<BlockState, LinkedHashSet<ResourceLocation>> layerModels =
                        new IdentityHashMap<>();
                Set<BlockState> poisoned =
                        Collections.newSetFromMap(new IdentityHashMap<>());
                if (definition.isMultiPart()) {
                    // 中文：每个 selector 的谓词在 loaded definition 内只编译一次；
                    // 非法 selector（未知属性/值）按 AutoSeamBlend 的防御性逐-selector
                    // 容错跳过，不中断整个 reload；这是为保障 atlas-preparation future
                    // 存活而设，vanilla 1.20.1 会让该异常使整个 blockstate definition
                    // 回退 missing。
                    // English: each selector predicate is compiled once per loaded definition;
                    // an invalid selector (unknown property/value) is skipped via
                    // AutoSeamBlend's defensive per-selector tolerance to keep the
                    // atlas-preparation future alive; vanilla 1.20.1 lets the exception
                    // fall the whole blockstate definition back to missing.
                    List<CompiledSelector> compiledSelectors =
                            new ArrayList<>();
                    for (Selector selector
                            : definition.getMultiPart().getSelectors()) {
                        Predicate<BlockState> predicate;
                        try {
                            predicate = selector.getPredicate(
                                    stateDefinition);
                        } catch (RuntimeException badSelector) {
                            // 中文：这是 AutoSeamBlend 的防御性逐-selector 容错，保证
                            // atlas-preparation future 存活；vanilla 1.20.1 不会在这里
                            // 隔离，而是让异常使该 blockstate definition 回退 missing。
                            // English: this is AutoSeamBlend's defensive per-selector
                            // tolerance so the atlas-preparation future survives; vanilla
                            // 1.20.1 does not isolate here and the exception makes the
                            // blockstate definition fall back to missing.
                            continue;
                        }
                        compiledSelectors.add(new CompiledSelector(
                                predicate,
                                selector));
                    }
                    // 中文：1.20.1 原版 multipart 层先为该 block 全部 possible states
                    // 建本层条目（possibleStates.forEach(method_4738) 后 layerMap.putAll
                    // 合并）；selector 无命中的状态以空依赖（本域 missing 编码）覆盖低层，
                    // 而不是保留低层；多个 selector 命中同一状态时贡献并集（对应原版
                    // MultiPart 收集全部命中 selector 的 variants）。
                    // English: vanilla 1.20.1 multipart layers first create an entry for
                    // EVERY possible state of the block (possibleStates.forEach(method_4738),
                    // merged by layerMap.putAll); states no selector matches are covered by an
                    // empty dependency list (this domain's missing encoding) overriding the
                    // lower layer instead of retaining it; multiple selectors matching one
                    // state contribute the union of their variants (vanilla MultiPart collects
                    // variants from every matching selector).
                    for (BlockState state : possibleStates) {
                        LinkedHashSet<ResourceLocation> ids =
                                new LinkedHashSet<>();
                        for (CompiledSelector compiled
                                : compiledSelectors) {
                            if (compiled.predicate().test(state)) {
                                for (Variant variant
                                        : compiled.selector()
                                                .getVariant()
                                                .getVariants()) {
                                    ids.add(variant.getModelLocation());
                                }
                            }
                        }
                        layerModels.put(state, ids);
                    }
                    mergeLayer(modelIdsByState, poisoned, layerModels);
                    continue;
                }
                // 中文：1.20.1 原版把 variant 键当作状态属性子集谓词（可省略未列属性），
                // 且对同一 loaded definition 只编译一次，再应用于该方块的全部状态。
                // English: vanilla 1.20.1 treats a variant key as a property-subset predicate
                // (unlisted properties are ignored), compiled once per loaded definition and
                // then applied to every state of the block.
                List<CompiledVariant> compiledVariants =
                        new ArrayList<>();
                for (Map.Entry<String, MultiVariant> variantEntry
                        : definition.getVariants().entrySet()) {
                    Predicate<BlockState> predicate;
                    try {
                        predicate = variantPredicate(
                                stateDefinition,
                                variantEntry.getKey());
                    } catch (RuntimeException badKey) {
                        // Vanilla logs the bad variant and keeps the rest of the definition;
                        // a single illegal key must not abort the whole initial reload.
                        continue;
                    }
                    compiledVariants.add(new CompiledVariant(
                            variantEntry.getKey(),
                            predicate,
                            variantEntry.getValue()));
                }
                for (CompiledVariant compiled : compiledVariants) {
                    for (BlockState state : possibleStates) {
                        if (!compiled.predicate().test(state)) {
                            continue;
                        }
                        if (poisoned.contains(state)) {
                            // 中文：该状态已在本层定义内被 overlap 毒化为 missing；
                            // 后续 variant 键不得复活它。
                            // English: the state was already poisoned to missing by an
                            // overlap inside this layer's definition; later variant keys
                            // must not revive it.
                            continue;
                        }
                        LinkedHashSet<ResourceLocation> previous =
                                layerModels.put(
                                        state,
                                        modelIdsOf(compiled.multiVariant()));
                        if (previous != null) {
                            // 中文：同一定义内多个 variant 键命中同一状态时，原版按
                            // overlapping definition 回退 missing；本域用空依赖列表编码
                            // missing（不加入 builtin/missing），该状态保持 missing，
                            // 不得被后续 key 复活。
                            // English: when multiple variant keys match the same state within
                            // one definition, vanilla treats it as an overlapping definition,
                            // and falls back to missing; this domain encodes missing as an
                            // empty dependency list (never builtin/missing), and the state
                            // stays missing without being revived by later keys.
                            layerModels.remove(state);
                            poisoned.add(state);
                        }
                    }
                }
                // 中文：按 per-layer state putAll/replace 把本层结果并入已累积映射。
                // English: merge this layer's per-state results into the accumulated map by
                // per-layer state putAll/replace.
                mergeLayer(modelIdsByState, poisoned, layerModels);
            }
        }
        StateModelIndex.publish(
                StateModelIndex.invert(modelIdsByState));
        ArrayList<StateModelEntry> entries = new ArrayList<>();
        for (Map.Entry<BlockState, LinkedHashSet<ResourceLocation>> entry
                : modelIdsByState.entrySet()) {
            BlockState state = entry.getKey();
            LinkedHashSet<ResourceLocation> ids = entry.getValue();
            List<ResourceLocation> resolved = ids.stream()
                    .filter(blockModels::containsKey)
                    .toList();
            if (resolved.isEmpty()) {
                entries.add(new StateModelEntry(
                        state,
                        List.of()));
                continue;
            }
            entries.add(new StateModelEntry(
                    state,
                    resolved));
        }
        return entries;
    }

    /**
     * 中文：镜像 1.20.1 原版 ModelBakery.predicate 的 variant 键语义：键是逗号分隔的
     * property=value 子集谓词，未列出的属性不参与匹配；空键匹配该方块全部状态。
     *
     * English: Mirrors vanilla 1.20.1 ModelBakery.predicate variant-key semantics: a key is a
     * comma-separated property=value subset predicate; unlisted properties do not participate
     * in matching, and an empty key matches every state of the block.
     */
    private static Predicate<BlockState> variantPredicate(
            StateDefinition<Block, BlockState> stateDefinition,
            String variantKey) {
        Map<Property<?>, Comparable<?>> matches = new HashMap<>();
        for (String part : variantKey.split(",")) {
            String[] propertyAndValue = part.split("=", 2);
            String propertyName = propertyAndValue[0];
            Property<?> property = stateDefinition.getProperty(propertyName);
            if (property != null && propertyAndValue.length == 2) {
                Comparable<?> value = property
                        .getValue(propertyAndValue[1])
                        .orElse(null);
                if (value == null) {
                    throw new IllegalArgumentException(
                            "Unknown value: '" + propertyAndValue[1]
                                    + "' for blockstate property: '"
                                    + propertyName
                                    + "' " + property.getPossibleValues());
                }
                matches.put(property, value);
            } else if (!propertyName.isEmpty()) {
                throw new IllegalArgumentException(
                        "Unknown blockstate property: '"
                                + propertyName + "'");
            }
        }
        Block block = stateDefinition.getOwner();
        return state -> {
            if (state != null && state.is(block)) {
                for (Map.Entry<Property<?>, Comparable<?>> match
                        : matches.entrySet()) {
                    if (!Objects.equals(
                            state.getValue(match.getKey()),
                            match.getValue())) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        };
    }

    /**
     * 中文：收集一个 MultiVariant 引用的全部模型 ID。
     * English: Collects every model ID referenced by one MultiVariant.
     */
    private static LinkedHashSet<ResourceLocation> modelIdsOf(
            MultiVariant multiVariant) {
        LinkedHashSet<ResourceLocation> ids = new LinkedHashSet<>();
        for (Variant variant : multiVariant.getVariants()) {
            ids.add(variant.getModelLocation());
        }
        return ids;
    }

    /**
     * 中文：per-layer state putAll/replace：被毒化的状态清空（missing），其余按层内
     * 分配覆盖已累积 per-state 映射。
     * English: Per-layer state putAll/replace: poisoned states are cleared (missing), the
     * rest override the accumulated per-state map with this layer's assignment.
     */
    private static void mergeLayer(
            Map<BlockState, LinkedHashSet<ResourceLocation>> accumulated,
            Set<BlockState> poisoned,
            Map<BlockState, LinkedHashSet<ResourceLocation>> layerModels) {
        for (BlockState state : poisoned) {
            accumulated.get(state).clear();
        }
        for (Map.Entry<BlockState, LinkedHashSet<ResourceLocation>> layerEntry
                : layerModels.entrySet()) {
            LinkedHashSet<ResourceLocation> ids =
                    accumulated.get(layerEntry.getKey());
            ids.clear();
            ids.addAll(layerEntry.getValue());
        }
    }

    // 中文：包私有仅用于 InitialSurfacePreparationModelParentContractTest 的父链回归
    // （测试脚手架）；解析逻辑本身不属于测试专用实现。
    // English: Package-private only for the InitialSurfacePreparationModelParentContractTest
    // parent-chain regression (test scaffolding); the resolution logic is not test-only.
    /**
     * 中文：预先解析所有 BlockModel 的 parent 链，使 getElements()/getMaterial() 在检查时
     * 与烘焙语义一致。
     *
     * English: Resolves every BlockModel parent chain up front so getElements()/getMaterial()
     * match baking semantics during inspection.
     */
    static void resolveModelParents(
            Map<ResourceLocation, BlockModel> blockModels) {
        // 中文：原版烘焙用 ModelBakery.getModel 把缺失父模型回退到 builtin/missing；
        // 直接 map.get 返回 null 会让 BlockModel.resolveParents 抛出
        // "BlockModel parent has to be a block model."（例如 builtin/entity 等特殊父模型）。
        // English: Vanilla baking falls back to the builtin missing model for unresolved
        // parents; a bare map.get returning null makes BlockModel.resolveParents throw
        // "BlockModel parent has to be a block model." (e.g. special parents like
        // builtin/entity), so mirror the vanilla fallback here.
        BlockModel missing = BlockModel.fromString(
                ModelBakery.MISSING_MODEL_MESH);
        Map<ResourceLocation, BlockModel> resolvable =
                new HashMap<>(blockModels);
        resolvable.put(
                ModelBakery.MISSING_MODEL_LOCATION,
                missing);
        // 中文：1.20.1 原版 ModelBakery.loadBlockModel 对内置父模型 builtin/generated 与
        // builtin/entity 分别返回 ModelBakery.GENERATION_MARKER / BLOCK_ENTITY_MARKER
        // （public static final BlockModel，由 {"gui_light":"front"} / {"gui_light":"side"}
        // 解析而来，均无元素）；必须登记这两个内置父模型，否则 resolveParents 会把它们
        // 回退到 missing 立方体，令以它们为父的 item/entity 模型父链退化出伪表面证据。
        // English: vanilla 1.20.1 ModelBakery.loadBlockModel maps the builtin parents
        // builtin/generated and builtin/entity to ModelBakery.GENERATION_MARKER /
        // BLOCK_ENTITY_MARKER (public static final BlockModel parsed from
        // {"gui_light":"front"} / {"gui_light":"side"}, both element-less). Both builtin
        // parents must be registered, otherwise resolveParents falls them back to the missing
        // cube and parent chains of item/entity models degenerate into spurious surface
        // evidence.
        resolvable.put(
                new ResourceLocation("builtin/generated"),
                ModelBakery.GENERATION_MARKER);
        resolvable.put(
                new ResourceLocation("builtin/entity"),
                ModelBakery.BLOCK_ENTITY_MARKER);
        java.util.function.Function<ResourceLocation, UnbakedModel> resolver =
                resolvable::get;
        for (BlockModel model : blockModels.values()) {
            model.resolveParents(resolver);
        }
    }

    public record Result(
            List<Surface> surfaces,
            List<StateCandidate> candidates,
            Snapshot atlas,
            List<String> diagnostics) {
        public Result {
            surfaces = List.copyOf(Objects.requireNonNull(surfaces, "surfaces"));
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
            Objects.requireNonNull(atlas, "atlas");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    /**
     * 中文：标准方块状态模型表中的候选即使只能部分解析，也作为显式证据保留。
     * <p>
     * English:
     * Candidates from the standard block-state model table remain explicit even when only partially
     * resolvable before stitching.
     */
    public record StateCandidate(
            BlockState state,
            CandidateStatus status,
            List<String> evidence) {
        public StateCandidate {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(status, "status");
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
            if (evidence.isEmpty()) {
                throw new IllegalArgumentException(
                        "surface candidate must retain evidence");
            }
        }
    }

    public enum CandidateStatus {
        PREPARED,
        PARTIAL,
        UNRESOLVED
    }

    public record Surface(
            BlockState state,
            Direction direction,
            SurfaceSourceSnapshot source,
            InferenceFacts inferenceFacts) {
        public Surface {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(inferenceFacts, "inferenceFacts");
        }
    }

    private record Models(
            Map<ResourceLocation, BlockModel> blockModels,
            Map<ResourceLocation, List<ModelBakery.LoadedJson>> blockStateJson) {
        private Models {
            blockModels = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(blockModels, "blockModels")));
            blockStateJson = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(blockStateJson, "blockStateJson")));
        }
    }

    // 中文：包私有仅用于 InitialSurfacePreparationStateModelKeyContractTest（测试脚手架）。
    // English: Package-private only for InitialSurfacePreparationStateModelKeyContractTest
    // (test scaffolding).
    record StateModelEntry(
            BlockState state,
            List<ResourceLocation> models) {
        StateModelEntry {
            Objects.requireNonNull(state, "state");
            models = List.copyOf(Objects.requireNonNull(models, "models"));
        }
    }

    // 中文：按 loaded definition 编译一次的 variant 键谓词，避免对同一键反复解析。
    // English: A variant-key predicate compiled once per loaded definition so the key is
    // never re-parsed for every state of the block.
    private record CompiledVariant(
            String key,
            Predicate<BlockState> predicate,
            MultiVariant multiVariant) {
        private CompiledVariant {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(predicate, "predicate");
            Objects.requireNonNull(multiVariant, "multiVariant");
        }
    }

    // 中文：按 loaded definition 编译一次的 multipart selector 谓词，供该方块全部状态复用。
    // English: A multipart selector predicate compiled once per loaded definition and reused
    // for every state of the block.
    private record CompiledSelector(
            Predicate<BlockState> predicate,
            Selector selector) {
        private CompiledSelector {
            Objects.requireNonNull(predicate, "predicate");
            Objects.requireNonNull(selector, "selector");
        }
    }

    private record ResolutionInputs(
            Models models,
            Snapshot atlas) {
        private ResolutionInputs {
            Objects.requireNonNull(models, "models");
            Objects.requireNonNull(atlas, "atlas");
        }
    }

    private record InspectionBatch(
            List<Surface> surfaces,
            List<StateCandidate> candidates,
            List<String> diagnostics) {
        private InspectionBatch {
            Objects.requireNonNull(surfaces, "surfaces");
            Objects.requireNonNull(candidates, "candidates");
            Objects.requireNonNull(diagnostics, "diagnostics");
        }
    }

    private record StateInspection(
            List<Surface> surfaces,
            StateCandidate candidate,
            List<String> diagnostics) {
        private StateInspection {
            surfaces = List.copyOf(Objects.requireNonNull(surfaces, "surfaces"));
            Objects.requireNonNull(candidate, "candidate");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }

        private static StateInspection unresolved(
                BlockState state,
                List<String> evidence) {
            return new StateInspection(
                    List.of(),
                    new StateCandidate(
                            state,
                            CandidateStatus.UNRESOLVED,
                            evidence),
                    evidence);
        }
    }

    private record GeometryInspection(
            List<Draft> drafts,
            boolean complete,
            List<String> diagnostics) {
        private GeometryInspection {
            drafts = List.copyOf(Objects.requireNonNull(drafts, "drafts"));
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    private record Draft(
            Direction direction,
            SurfaceSourceSnapshot source,
            boolean fullBlock,
            boolean axisAligned,
            boolean fullFace,
            boolean validUv,
            int tintIndex) {
        private Draft {
            if (tintIndex < -1) {
                throw new IllegalArgumentException("tintIndex must be -1 or non-negative");
            }
        }
    }

}
