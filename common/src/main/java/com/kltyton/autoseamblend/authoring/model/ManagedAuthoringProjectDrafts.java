package com.kltyton.autoseamblend.authoring.model;

import com.kltyton.autoseamblend.authoring.template.ManagedAuthoringTemplates;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.reload.rule.ManagedRule;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.PreparedSurfaceMethods;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Optional;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * 中文：统一规范 Managed 草稿、规则与首次保存项目之间的转换。
 *
 * English: Canonical conversion between Managed drafts, rules, and first-save
 * projects.
 */
public final class ManagedAuthoringProjectDrafts {
    private ManagedAuthoringProjectDrafts() {}

    /**
     * 中文：解析工作台输入的方法名，并补齐手动方法的具体解析值。
     *
     * English: Parses a workbench method name and fills the concrete value for
     * manual methods.
     */
    public static ManagedAuthoringDraft draft(
            String targetBlockId,
            String sourceTextureId,
            String originalModelId,
            String requestedMethod,
            ConnectionMethod resolvedMethod,
            boolean compatibility,
            boolean pane) {
        ConnectionMethod requested = parseRequestedMethod(requestedMethod);
        if (requested != ConnectionMethod.AUTO
                && resolvedMethod != null
                && requested != resolvedMethod) {
            throw new IllegalArgumentException(
                    "manual method must equal resolved method");
        }
        ConnectionMethod resolved = requested == ConnectionMethod.AUTO
                ? Objects.requireNonNull(
                        resolvedMethod,
                        "resolvedMethod for auto")
                : requested;
        return draft(
                targetBlockId,
                sourceTextureId,
                originalModelId,
                requested,
                resolved,
                compatibility,
                pane);
    }

    /**
     * 中文：构造已经完成方法推断的规范草稿。
     *
     * English: Constructs a canonical draft after method inference has
     * completed.
     */
    public static ManagedAuthoringDraft draft(
            String targetBlockId,
            String sourceTextureId,
            String originalModelId,
            ConnectionMethod requestedMethod,
            ConnectionMethod resolvedMethod,
            boolean compatibility,
            boolean pane) {
        return new ManagedAuthoringDraft(
                targetBlockId,
                sourceTextureId,
                originalModelId,
                requestedMethod,
                resolvedMethod,
                compatibility,
                pane);
    }

    /**
     * 中文：把文本方法参数规范化为公共方法枚举。
     *
     * English: Normalizes a textual method parameter to the shared method
     * enum.
     */
    public static ConnectionMethod parseRequestedMethod(String requestedMethod) {
        return ConnectionMethod.parse(requestedMethod)
                .orElseThrow(() -> new IllegalArgumentException("METHOD_UNKNOWN"));
    }

    /**
     * 中文：使用 Loader 提供的模型纹理键构造规范规则。
     *
     * English: Constructs a canonical rule with model texture keys supplied by
     * the Loader adapter.
     */
    public static ManagedAuthoringRule createRule(
            ManagedAuthoringDraft draft,
            List<String> sourceTextureKeys) {
        Objects.requireNonNull(draft, "draft");
        return createRule(
                draft.targetBlockId(),
                draft.sourceTextureId(),
                draft.originalModelId(),
                draft.requestedMethod(),
                draft.resolvedMethod(),
                draft.compatibility(),
                draft.pane(),
                sourceTextureKeys);
    }

    /**
     * 中文：从文本方法参数构造规范规则；auto 必须由调用方提供已解析方法。
     *
     * English: Constructs a canonical rule from a textual method; auto
     * requires the caller to supply its resolved method.
     */
    public static ManagedAuthoringRule createRule(
            String targetBlockId,
            String sourceTextureId,
            String originalModelId,
            String requestedMethod,
            ConnectionMethod resolvedMethod,
            boolean compatibility,
            boolean pane,
            List<String> sourceTextureKeys) {
        return createRule(
                draft(
                        targetBlockId,
                        sourceTextureId,
                        originalModelId,
                        requestedMethod,
                        resolvedMethod,
                        compatibility,
                        pane),
                sourceTextureKeys);
    }

    /**
     * 中文：把一个规范草稿包装成指定引擎的首次保存项目。
     *
     * English: Wraps one canonical draft into a first-save project for the
     * selected engine family.
     */
    public static ManagedAuthoringProject create(
            EngineFamily family,
            ManagedAuthoringDraft draft,
            List<String> sourceTextureKeys) {
        Objects.requireNonNull(family, "family");
        return ManagedAuthoringTemplates.create(
                family,
                List.of(createRule(draft, sourceTextureKeys)));
    }

    /** 中文：从当前准星表面构造共享草稿；Loader 只提供 Minecraft 客户端事实。 / English: Builds a shared draft from the current crosshair surface. */
    public static Optional<ManagedAuthoringDraft> currentSelection(
            Minecraft minecraft) {
        return currentSelection(minecraft, Optional.empty());
    }

    /** 中文：从当前准星表面构造指定引擎草稿。 / English: Builds a draft for one selected engine family. */
    public static Optional<ManagedAuthoringDraft> currentSelection(
            Minecraft minecraft,
            EngineFamily family) {
        return currentSelection(
                minecraft,
                Optional.of(Objects.requireNonNull(family, "family")));
    }

    public static Optional<ManagedAuthoringDraft> currentSelection(
            Minecraft minecraft,
            EngineFamily family,
            ReloadPublication.Generation runtime) {
        return currentSelection(
                minecraft,
                Optional.of(Objects.requireNonNull(family, "family")),
                runtime);
    }

    private static Optional<ManagedAuthoringDraft> currentSelection(
            Minecraft minecraft,
            Optional<EngineFamily> family) {
        return currentSelection(
                minecraft,
                family,
                ReloadPublication.current());
    }

    private static Optional<ManagedAuthoringDraft> currentSelection(
            Minecraft minecraft,
            Optional<EngineFamily> family,
            ReloadPublication.Generation runtime) {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(runtime, "runtime");
        if (minecraft.level == null
                || !(minecraft.hitResult instanceof BlockHitResult hit)) {
            return Optional.empty();
        }
        BlockState state = minecraft.level.getBlockState(hit.getBlockPos());
        if (state.isAir()) {
            return Optional.empty();
        }
        Optional<MinecraftSurfaceCatalog.FaceSurface> surface =
                runtime.surfaces().preferredFace(state, hit.getDirection());
        if (surface.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(createDraft(
                state,
                surface.orElseThrow(),
                family,
                runtime));
    }

    /** 中文：为一个已捕获表面构造共享草稿。 / English: Builds a shared draft for a captured surface. */
    public static ManagedAuthoringDraft forSurface(
            BlockState state,
            MinecraftSurfaceCatalog.FaceSurface surface,
            EngineFamily family) {
        return forSurface(
                state,
                surface,
                family,
                ReloadPublication.current());
    }

    public static ManagedAuthoringDraft forSurface(
            BlockState state,
            MinecraftSurfaceCatalog.FaceSurface surface,
            EngineFamily family,
            ReloadPublication.Generation runtime) {
        return createDraft(
                Objects.requireNonNull(state, "state"),
                Objects.requireNonNull(surface, "surface"),
                Optional.of(Objects.requireNonNull(family, "family")),
                Objects.requireNonNull(runtime, "runtime"));
    }

    /** 中文：为已发现方块选择稳定代表面。 / English: Selects a stable representative face for a discovered block. */
    public static Optional<ManagedAuthoringDraft> forBlock(
            Block block,
            EngineFamily family) {
        return forBlock(
                block,
                family,
                ReloadPublication.current());
    }

    /** 中文：在显式冻结代次上为方块选择稳定代表面。 / English: Selects a stable representative face for a block on an explicit frozen generation. */
    public static Optional<ManagedAuthoringDraft> forBlock(
            Block block,
            EngineFamily family,
            ReloadPublication.Generation runtime) {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(runtime, "runtime");
        return runtime.surfaces()
                .representative(block)
                .map(candidate -> createDraft(
                        candidate.state(),
                        candidate.surface(),
                        Optional.of(family),
                        runtime));
    }

    /** 中文：从明确字段创建完整 Managed 项目。 / English: Creates a complete Managed project from explicit fields. */
    public static ManagedAuthoringProject create(
            EngineFamily family,
            String targetBlockId,
            String sourceTextureId,
            String originalModelId,
            String requestedMethod,
            boolean compatibility) {
        Objects.requireNonNull(family, "family");
        ConnectionMethod requested =
                parseRequestedMethod(requestedMethod);
        Block block = requireBlock(targetBlockId);
        ConnectionMethod resolved = requested == ConnectionMethod.AUTO
                ? inferred(block).orElseThrow(() ->
                        new IllegalArgumentException(
                                "AUTO_SURFACE_UNRESOLVED"))
                : requested;
        ManagedAuthoringDraft draft = draft(
                targetBlockId,
                sourceTextureId,
                originalModelId,
                requested,
                resolved,
                compatibility,
                block instanceof IronBarsBlock);
        return ManagedAuthoringTemplates.create(
                family,
                List.of(createRule(draft)));
    }

    public static ManagedAuthoringRule createRule(
            String targetBlockId,
            String sourceTextureId,
            String originalModelId,
            String requestedMethod,
            boolean compatibility) {
        ConnectionMethod requested =
                parseRequestedMethod(requestedMethod);
        Block block = requireBlock(targetBlockId);
        ConnectionMethod resolved = requested == ConnectionMethod.AUTO
                ? inferred(block).orElseThrow(() ->
                        new IllegalArgumentException(
                                "AUTO_SURFACE_UNRESOLVED"))
                : requested;
        return createRule(draft(
                targetBlockId,
                sourceTextureId,
                originalModelId,
                requested,
                resolved,
                compatibility,
                block instanceof IronBarsBlock));
    }

    public static ManagedAuthoringRule createRule(
            ManagedAuthoringDraft draft) {
        Objects.requireNonNull(draft, "draft");
        Block block = requireBlock(draft.targetBlockId());
        if (draft.pane() != (block instanceof IronBarsBlock)) {
            throw new IllegalArgumentException("PANE_TARGET_MISMATCH");
        }
        return createRule(
                draft,
                Minecraft.getInstance().getResourceManager());
    }

    public static ManagedAuthoringRule createRule(
            ManagedAuthoringDraft draft,
            ResourceManager resources) {
        Objects.requireNonNull(draft, "draft");
        Objects.requireNonNull(resources, "resources");
        Block block = requireBlock(draft.targetBlockId());
        if (draft.pane() != (block instanceof IronBarsBlock)) {
            throw new IllegalArgumentException("PANE_TARGET_MISMATCH");
        }
        List<String> sourceTextureKeys = MinecraftModelTextureBindings.resolve(
                resources,
                draft.originalModelId(),
                draft.sourceTextureId());
        if (!sourceTextureKeys.isEmpty()) {
            return createRule(draft, draft.originalModelId(), sourceTextureKeys);
        }
        Optional<BlockstateModelFallback.Selected> selected =
                BlockstateModelFallback.select(
                        resources,
                        draft.targetBlockId(),
                        draft.sourceTextureId());
        if (selected.isPresent()) {
            BlockstateModelFallback.Selected fallback = selected.orElseThrow();
            return createRule(draft, fallback.modelId(), fallback.sourceTextureKeys());
        }
        return createRule(draft, draft.originalModelId(), List.of());
    }

    public static Optional<ConnectionMethod> resolvedAuto(Block block) {
        return resolvedAuto(
                Objects.requireNonNull(block, "block"),
                ReloadPublication.current());
    }

    private static Optional<ConnectionMethod> resolvedAuto(
            Block block,
            ReloadPublication.Generation runtime) {
        Optional<ConnectionMethod> prepared = runtime.preparedMethods()
                .autoMethod(block)
                .map(PreparedSurfaceMethods.PreparedAutoMethod::method);
        if (prepared.isPresent()) {
            return prepared;
        }
        return runtime.surfaces()
                .representative(block)
                .map(MinecraftSurfaceCatalog.BlockRepresentative::surface)
                .map(MinecraftSurfaceCatalog.FaceSurface::inferredMethod);
    }

    private static ManagedAuthoringDraft createDraft(
            BlockState state,
            MinecraftSurfaceCatalog.FaceSurface surface,
            Optional<EngineFamily> family,
            ReloadPublication.Generation runtime) {
        Block block = state.getBlock();
        String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
        String blockPath = blockId.substring(blockId.indexOf(':') + 1);
        ConnectionRuleSet<Block> rules = runtime.selectors().rules();
        Optional<ManagedRule> managed = family.flatMap(value ->
                runtime.managedRules().rule(value, blockId));
        ConnectionMethod requested = managed
                .map(ManagedRule::requestedMethod)
                .orElseGet(() -> rules.isTarget(block)
                        ? rules.method(block)
                        : ConnectionMethod.AUTO);
        boolean compatibility = managed
                .map(ManagedRule::compatibility)
                .orElseGet(() -> rules.isTarget(block)
                        && rules.resourcePackMode(block)
                                == ConnectionRuleSet.ResourcePackMode.COMPATIBILITY);
        Optional<PreparedSurfaceMethods.PreparedAutoMethod> prepared =
                requested == ConnectionMethod.AUTO
                        ? runtime.preparedMethods().autoMethod(block)
                        : Optional.empty();
        return draft(
                blockId,
                prepared.map(value -> value.spriteId().toString())
                        .orElseGet(() -> surface.sprite().contents().name().toString()),
                blockId.substring(0, blockId.indexOf(':'))
                        + ":block/" + blockPath,
                requested,
                requested == ConnectionMethod.AUTO
                        ? prepared.map(PreparedSurfaceMethods.PreparedAutoMethod::method)
                                .orElse(surface.inferredMethod())
                        : requested,
                compatibility,
                block instanceof IronBarsBlock);
    }

    private static Block requireBlock(String targetBlockId) {
        Identifier target = Identifier.tryParse(
                Objects.requireNonNull(targetBlockId, "targetBlockId"));
        if (target == null || !BuiltInRegistries.BLOCK.containsKey(target)) {
            throw new IllegalArgumentException("TARGET_BLOCK_UNKNOWN");
        }
        Block block = BuiltInRegistries.BLOCK.getValue(target);
        if (block == null || block == Blocks.AIR) {
            throw new IllegalArgumentException("TARGET_BLOCK_UNKNOWN");
        }
        return block;
    }

    private static ManagedAuthoringRule createRule(
            String targetBlockId,
            String sourceTextureId,
            String originalModelId,
            ConnectionMethod requestedMethod,
            ConnectionMethod resolvedMethod,
            boolean compatibility,
            boolean pane,
            List<String> sourceTextureKeys) {
        return new ManagedAuthoringRule(
                targetBlockId,
                sourceTextureId,
                originalModelId,
                requestedMethod,
                resolvedMethod,
                compatibility,
                pane,
                sourceTextureKeys);
    }

    private static ManagedAuthoringRule createRule(
            ManagedAuthoringDraft draft,
            String originalModelId,
            List<String> sourceTextureKeys) {
        return new ManagedAuthoringRule(
                draft.targetBlockId(),
                draft.sourceTextureId(),
                originalModelId,
                draft.requestedMethod(),
                draft.resolvedMethod(),
                draft.compatibility(),
                draft.pane(),
                sourceTextureKeys);
    }

    private static Optional<ConnectionMethod> inferred(Block block) {
        return resolvedAuto(block)
                .filter(method -> method != ConnectionMethod.NONE);
    }
}
