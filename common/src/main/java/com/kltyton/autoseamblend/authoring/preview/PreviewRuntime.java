package com.kltyton.autoseamblend.authoring.preview;

import com.kltyton.autoseamblend.engine.registry.EngineRegistryRuntimeState;
import com.kltyton.autoseamblend.engine.routing.query.EngineQueryRouterCore;
import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.engine.routing.query.MinecraftEngineQueryContext;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 中文：通过查询选定的引擎预览提供器解析当前注视表面；Loader 只注入引擎注册表与 tint 解析。
 *
 * English:
 * Resolves the current looked-at surface through the query-selected engine preview provider;
 * loaders inject only the engine registry and tint resolution.
 */
public final class PreviewRuntime {
    private PreviewRuntime() {}

    public static void register(
            PreviewProvider provider) {
        PreviewProviderRegistry.register(provider);
    }

    /** 中文：只报告中立预览 provider 是否已经注册，不触发任何游戏查询。 / English: Reports neutral preview-provider registration without executing a game query. */
    public static boolean available(String engineId) {
        return PreviewProviderRegistry.available(engineId);
    }

    public static Optional<NativePreviewSnapshot> current(
            Minecraft minecraft,
            EngineRegistryRuntimeState engines,
            PreviewTint tint) {
        return current(
                minecraft,
                Optional.empty(),
                engines,
                tint);
    }

    public static Optional<NativePreviewSnapshot> current(
            Minecraft minecraft,
            ConnectionMethod requestedMethod,
            EngineRegistryRuntimeState engines,
            PreviewTint tint) {
        return current(
                minecraft,
                Optional.of(Objects.requireNonNull(
                        requestedMethod,
                        "requestedMethod")),
                engines,
                tint);
    }

    private static Optional<NativePreviewSnapshot> current(
            Minecraft minecraft,
            Optional<ConnectionMethod> requestedOverride,
            EngineRegistryRuntimeState engines,
            PreviewTint tint) {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(
                requestedOverride,
                "requestedOverride");
        Objects.requireNonNull(engines, "engines");
        Objects.requireNonNull(tint, "tint");
        if (minecraft.level == null
                || !(minecraft.hitResult
                        instanceof BlockHitResult hit)) {
            return Optional.empty();
        }
        BlockState state = minecraft.level.getBlockState(
                hit.getBlockPos());
        if (state.isAir()) {
            return Optional.empty();
        }
        return ReloadPublication.read(runtime ->
                resolve(
                        minecraft.level,
                        hit.getBlockPos(),
                        state,
                        hit.getDirection(),
                        requestedOverride,
                        Set.of(),
                        Optional.empty(),
                        Optional.empty(),
                        false,
                        runtime,
                        engines,
                        tint));
    }

    /**
     * 中文：对工作室的只读虚拟邻居场景执行与 Runtime 相同的引擎预览查询。
     *
     * English:
     * Executes the same engine preview query as Runtime against the studio's
     * read-only virtual-neighbor scene.
     */
    public static Optional<NativePreviewSnapshot> scene(
            Minecraft minecraft,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction face,
            ConnectionMethod requestedMethod,
            List<Block> connectionBlocks,
            Optional<BlockState> donorState,
            Optional<BlockPos> donorPosition,
            boolean connectionPlaceholder,
            EngineRegistryRuntimeState engines,
            PreviewTint tint) {
        Objects.requireNonNull(minecraft, "minecraft");
        BlockAndTintGetter checkedLevel = Objects.requireNonNull(
                level,
                "level");
        BlockPos checkedPos = Objects.requireNonNull(pos, "pos");
        BlockState checkedState = Objects.requireNonNull(state, "state");
        Direction checkedFace = Objects.requireNonNull(face, "face");
        Optional<ConnectionMethod> requested = Optional.of(
                Objects.requireNonNull(
                        requestedMethod,
                        "requestedMethod"));
        Set<Block> connected = Set.copyOf(
                Objects.requireNonNull(
                        connectionBlocks,
                        "connectionBlocks"));
        Optional<BlockState> checkedDonor = Objects.requireNonNull(
                donorState,
                "donorState");
        Optional<BlockPos> checkedDonorPosition = Objects.requireNonNull(
                donorPosition,
                "donorPosition");
        Objects.requireNonNull(engines, "engines");
        Objects.requireNonNull(tint, "tint");
        return ReloadPublication.read(runtime ->
                resolve(
                        checkedLevel,
                        checkedPos,
                        checkedState,
                        checkedFace,
                        requested,
                        connected,
                        checkedDonor,
                        checkedDonorPosition,
                        connectionPlaceholder,
                        runtime,
                        engines,
                        tint));
    }

    private static Optional<NativePreviewSnapshot> resolve(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction face,
            Optional<ConnectionMethod> requestedOverride,
            Set<Block> connectionBlocks,
            Optional<BlockState> donorState,
            Optional<BlockPos> donorPosition,
            boolean connectionPlaceholder,
            ReloadPublication.Generation runtime,
            EngineRegistryRuntimeState engines,
            PreviewTint tint) {
        RuleRuntime.Snapshot rules =
                runtime.selectors();
        MinecraftSurfaceCatalog.Snapshot surfaces =
                runtime.surfaces();
        Optional<FaceSurface> surface = surfaces.preferredFace(
                state,
                face);
        if (surface.isEmpty()) {
            return Optional.empty();
        }
        FaceSurface selected = surface.orElseThrow();
        Optional<EngineQuerySelection>
                selection =
                        EngineQueryRouterCore.exact(
                                engines,
                                runtime,
                                state,
                                level,
                                pos,
                                selected.representativeQuad(),
                                selected.sprite(),
                                MinecraftEngineQueryContext::new);
        if (selection.isEmpty()) {
            return Optional.empty();
        }
        return PreviewSnapshotResolver.resolve(
                new PreviewSnapshotRequest(
                        level,
                        pos,
                        state,
                        face,
                        selected,
                        rules,
                        surfaces,
                        selection.orElseThrow(),
                        requestedOverride,
                        connectionBlocks,
                        donorState,
                        donorPosition,
                        connectionPlaceholder),
                tint::color);
    }

    /**
     * 中文：Loader 注入的原生方块 tint 解析契约。
     *
     * English: Native block-tint resolution contract injected by the Loader.
     */
    @FunctionalInterface
    public interface PreviewTint {
        int color(
                BlockAndTintGetter level,
                BlockPos pos,
                BlockState state,
                FaceSurface surface);
    }
}
