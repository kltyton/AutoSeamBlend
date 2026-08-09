package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeStateSampler;
import com.kltyton.autoseamblend.compat.athena.runtime.AthenaStateProjection;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolution;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolver.Donor;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import earth.terrarium.athena.api.client.fabric.WrappedGetter;
import earth.terrarium.athena.api.client.utils.CtmState;
import java.util.Objects;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：把 overlay 的接收面连续性投影到 Athena 原生八方向 CTM 状态；逐段移植已验收
 * 1.21.1 ce33d6c FabricAthenaNativeOverlayStateSampler，仅做 26.1.2 loader
 * WrappedGetter/Quad 适配，全部采样语义留在 common
 * {@link AthenaNativeStateSampler#sampleOverlay}。
 *
 * <p>English: Projects receiver continuity for an overlay into Athena's native eight-way CTM
 * state; ported stage by stage from the accepted 1.21.1 ce33d6c
 * FabricAthenaNativeOverlayStateSampler with only the 26.1.2 loader WrappedGetter/Quad
 * adaptation, while every sampling semantic stays in the common
 * {@link AthenaNativeStateSampler#sampleOverlay}.
 */
public final class FabricAthenaNativeOverlayStateSampler {
    private FabricAthenaNativeOverlayStateSampler() {}

    public static CtmState state(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState receiver,
            Donor donor,
            Direction face,
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(donor, "donor");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(surfaces, "surfaces");
        return AthenaNativeStateSampler.sampleOverlay(
                new WrappedGetter(level),
                receiver,
                pos,
                face,
                (neighborState, neighborAppearance) ->
                        AthenaStateProjection.connects(
                                        rules,
                                        donor.state().getBlock(),
                                        neighborAppearance.getBlock())
                                && !AthenaStateProjection.connects(
                                        rules,
                                        receiver.getBlock(),
                                        neighborState.getBlock()),
                (ignoredPos, neighborAppearance) ->
                        OverlayDonorResolution
                                .receivesOverlayFrom(
                                        EngineFamily.ATHENA,
                                        donor,
                                        neighborAppearance,
                                        face,
                                        rules,
                                        surfaces));
    }
}
