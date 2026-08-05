package com.kltyton.autoseamblend.engine.routing;

import com.kltyton.autoseamblend.engine.registry.EngineRegistryRuntimeState;
import com.kltyton.autoseamblend.engine.routing.query.EngineQueryRouting;
import com.kltyton.autoseamblend.engine.routing.query.EngineQueryRouterCore;
import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.engine.routing.query.MinecraftEngineQueryContext;
import com.kltyton.autoseamblend.engine.routing.query.NativeObservationBridge;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 中文：Loader 中立的唯一精确查询入口；引擎选择、五级来源、策略与槽位补齐全部委托给公共
 * 注册表和 QueryArbiter。Loader 只注入引擎注册表 fallback。
 *
 * English:
 * Sole Loader-neutral exact-query entry point. Engine selection, five-tier
 * provenance, policy, and slot completion all delegate to the common registry
 * and {@link EngineQueryRouting}. Loaders inject only the engine-registry
 * fallback.
 */
public final class EngineQueryRouter {
    private static final AtomicReference<
                    EngineRegistryRuntimeState>
            ENGINES = new AtomicReference<>();
    private static final AtomicReference<
                    Supplier<EngineRegistryRuntimeState>>
            FALLBACK = new AtomicReference<>();
    private EngineQueryRouter() {}

    public static void initialize(
            EngineRegistryRuntimeState engines) {
        ENGINES.set(Objects.requireNonNull(
                engines,
                "engines"));
        EngineQueryRouterCore.reset(ReloadPublication.current());
    }

    /**
     * 中文：注册 Loader 的引擎注册表 fallback（例如 NeoForge 的 ModList 发现注册表）。
     *
     * English: Registers the Loader engine-registry fallback (for example the
     * NeoForge ModList-based discovery registry).
     */
    public static void installFallback(
            Supplier<EngineRegistryRuntimeState> fallback) {
        FALLBACK.set(Objects.requireNonNull(
                fallback,
                "fallback"));
    }

    public static void registerNativeQueryOwnership(
            NativeQueryOwnershipProvider provider) {
        NativeObservationBridge.register(
                Objects.requireNonNull(provider, "provider"));
    }

    public static Optional<EngineQuerySelection> current(
            Minecraft minecraft) {
        Objects.requireNonNull(minecraft, "minecraft");
        return ReloadPublication.read(
                runtime -> current(
                        minecraft,
                        runtime));
    }

    private static Optional<EngineQuerySelection> current(
            Minecraft minecraft,
            ReloadPublication.Generation runtime) {
        if (minecraft.level == null
                || !(minecraft.hitResult
                        instanceof BlockHitResult hit)) {
            return fallback(runtime);
        }
        BlockState state = minecraft.level
                .getBlockState(hit.getBlockPos());
        if (state.isAir()) {
            return fallback(runtime);
        }
        Optional<EngineQuerySelection> exact = runtime.surfaces()
                .preferredFace(
                        state,
                        hit.getDirection())
                .flatMap(surface -> select(
                        runtime,
                        state,
                        minecraft.level,
                        hit.getBlockPos(),
                        surface.representativeQuad(),
                        surface.sprite()));
        // 中文：工作台需要一个已安装引擎；准星查询没有候选表面不等于没有安装引擎。
        // English: The workbench needs an installed engine; a crosshair query miss does not mean no engine is installed.
        return exact
                .or(() -> summary(
                        runtime,
                        state,
                        false))
                .or(() -> fallback(runtime));
    }

    public static Optional<EngineQuerySelection> select(
            BlockState state) {
        Objects.requireNonNull(state, "state");
        return ReloadPublication.read(
                runtime -> summary(
                        runtime,
                        state,
                        false));
    }

    public static Optional<EngineQuerySelection> select(
            BlockState state,
            boolean continuityNativeExact) {
        Objects.requireNonNull(state, "state");
        return ReloadPublication.read(
                runtime -> summary(
                        runtime,
                        state,
                        continuityNativeExact));
    }

    /** 中文：仅供同步预 Atlas 规划显式读取一个未发布的不可变代次。 / English: Allows synchronous pre-atlas planning to read one explicit unpublished immutable generation. */
    public static Optional<EngineQuerySelection> select(
            BlockState state,
            ReloadPublication.Generation runtime) {
        return summary(
                Objects.requireNonNull(runtime, "runtime"),
                Objects.requireNonNull(state, "state"),
                false);
    }

    /** 中文：在调用方捕获的根代次上执行 Continuity 摘要选择。 / English: Performs Continuity summary selection against the root generation captured by the caller. */
    public static Optional<EngineQuerySelection> select(
            BlockState state,
            boolean continuityNativeExact,
            ReloadPublication.Generation runtime) {
        return summary(
                Objects.requireNonNull(runtime, "runtime"),
                Objects.requireNonNull(state, "state"),
                continuityNativeExact);
    }

    public static Optional<EngineQuerySelection> select(
            BlockState state,
            BlockAndTintGetter level,
            BlockPos pos,
            BakedQuad quad,
            TextureAtlasSprite sprite) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(quad, "quad");
        Objects.requireNonNull(sprite, "sprite");
        return ReloadPublication.read(
                runtime -> select(
                        runtime,
                        state,
                        level,
                        pos,
                        quad,
                        sprite));
    }

    /** 中文：在调用方捕获的根代次上执行精确 Quad 查询。 / English: Performs an exact quad query against the root generation captured by the caller. */
    public static Optional<EngineQuerySelection> select(
            ReloadPublication.Generation runtime,
            BlockState state,
            BlockAndTintGetter level,
            BlockPos pos,
            BakedQuad quad,
            TextureAtlasSprite sprite) {
        return EngineQueryRouterCore.exact(
                engines(),
                runtime,
                state,
                level,
                pos,
                quad,
                sprite,
                MinecraftEngineQueryContext::new);
    }

    public static Optional<EngineQuerySelection> fallback() {
        return ReloadPublication.read(
                EngineQueryRouter::fallback);
    }

    private static Optional<EngineQuerySelection> fallback(
            ReloadPublication.Generation runtime) {
        return EngineQueryRouterCore.fallback(engines(), runtime);
    }

    private static Optional<EngineQuerySelection> summary(
            ReloadPublication.Generation runtime,
            BlockState state,
            boolean continuityNativeExact) {
        return EngineQueryRouterCore.summary(
                engines(),
                runtime,
                state,
                continuityNativeExact);
    }

    private static EngineRegistryRuntimeState
            engines() {
        EngineRegistryRuntimeState state =
                ENGINES.get();
        if (state != null) {
            return state;
        }
        Supplier<EngineRegistryRuntimeState> fallback =
                FALLBACK.get();
        if (fallback == null) {
            throw new IllegalStateException(
                    "ENGINE_REGISTRY_FALLBACK_UNAVAILABLE");
        }
        return fallback.get();
    }
}
