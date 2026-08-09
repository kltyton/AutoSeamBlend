package com.kltyton.autoseamblend.runtime.overlay;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：共享 overlay 供体发现的 Loader 中立静态入口；Loader 只注入引擎路由查询。
 *
 * English: Loader-neutral static entry for shared overlay-donor discovery;
 * loaders inject only the engine-route lookup.
 */
public final class OverlayDonorResolution {
    private static final AtomicReference<
                    OverlayDonorResolver.RouteLookup>
            ROUTE_LOOKUP = new AtomicReference<>();

    private OverlayDonorResolution() {}

    /**
     * 中文：注册 Loader 的引擎路由查询（例如 NeoForge 精确查询路由）。
     *
     * English: Registers the Loader engine-route lookup (for example the
     * NeoForge exact-query router).
     */
    public static void installRouteLookup(
            OverlayDonorResolver.RouteLookup routeLookup) {
        ROUTE_LOOKUP.set(Objects.requireNonNull(
                routeLookup,
                "routeLookup"));
    }

    public static List<Direction> planarDirections(
            Direction face) {
        return OverlayDonorResolver.planarDirections(face);
    }

    public static List<OverlayDonorResolver.Donor> resolveAll(
            BlockAndTintGetter level,
            BlockPos pos,
            Direction face,
            BlockState receiver,
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces,
            EngineFamily family,
            List<Direction> candidateDirections) {
        return core().resolveAll(
                level,
                pos,
                face,
                receiver,
                rules,
                surfaces,
                family,
                candidateDirections);
    }

    public static ConnectionMethod resolveMethod(
            EngineFamily family,
            BlockState state,
            FaceSurface surface,
            ConnectionRuleSet<Block> rules) {
        return core().resolveMethod(
                family,
                state,
                surface,
                rules);
    }

    public static ConnectionMethod resolveMethod(
            EngineFamily family,
            BlockState state,
            FaceSurface surface,
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        return core().resolveMethod(
                family,
                state,
                surface,
                rules,
                surfaces);
    }

    public static boolean receivesOverlayFrom(
            EngineFamily family,
            OverlayDonorResolver.Donor donor,
            BlockState receiver,
            Direction face,
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        return core().receivesOverlayFrom(
                family,
                donor,
                receiver,
                face,
                rules,
                surfaces);
    }

    private static OverlayDonorResolver core() {
        OverlayDonorResolver.RouteLookup lookup =
                ROUTE_LOOKUP.get();
        if (lookup == null) {
            throw new IllegalStateException(
                    "OVERLAY_ROUTE_LOOKUP_UNAVAILABLE");
        }
        return new OverlayDonorResolver(lookup);
    }
}
