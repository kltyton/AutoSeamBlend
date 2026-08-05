package com.kltyton.autoseamblend.authoring.preview;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/**
 * 中文：把预览邻居覆盖到一个只读世界切片，绝不写入真实客户端关卡。
 *
 * English:
 * Overlays preview neighbors onto a read-only world slice and never writes to
 * the real client level.
 */
public final class VirtualPreviewLevel
        implements BlockAndTintGetter {
    private final BlockAndTintGetter delegate;
    private final BlockPos origin;
    private final BlockState center;
    private final Map<BlockPos, BlockState> states;

    public VirtualPreviewLevel(
            BlockAndTintGetter delegate,
            BlockPos origin,
            BlockState center,
            Map<PreviewNeighborPosition, BlockState>
                    neighbors) {
        this.delegate = Objects.requireNonNull(
                delegate,
                "delegate");
        this.origin = Objects.requireNonNull(
                        origin,
                        "origin")
                .immutable();
        this.center = Objects.requireNonNull(
                center,
                "center");
        EnumMap<PreviewNeighborPosition, BlockState>
                copy =
                        new EnumMap<>(
                                PreviewNeighborPosition.class);
        copy.putAll(
                Objects.requireNonNull(
                        neighbors,
                        "neighbors"));
        java.util.LinkedHashMap<BlockPos, BlockState>
                resolved =
                        new java.util.LinkedHashMap<>();
        copy.forEach((position, state) ->
                resolved.put(
                        this.origin.offset(
                                new BlockPos(
                                        position.x(),
                                        position.y(),
                                        position.z())),
                        Objects.requireNonNull(
                                state,
                                "neighbor state")));
        states = Map.copyOf(resolved);
    }

    @Override
    public BlockState getBlockState(
            BlockPos pos) {
        if (origin.equals(pos)) {
            return center;
        }
        return states.getOrDefault(
                pos,
                Blocks.AIR.defaultBlockState());
    }

    @Override
    public FluidState getFluidState(
            BlockPos pos) {
        return getBlockState(pos)
                .getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(
            BlockPos pos) {
        return null;
    }

    @Override
    public int getHeight() {
        return delegate.getHeight();
    }

    @Override
    public int getMinY() {
        return delegate.getMinY();
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return delegate.getLightEngine();
    }

    @Override
    public CardinalLighting cardinalLighting() {
        return delegate.cardinalLighting();
    }

    @Override
    public int getBlockTint(
            BlockPos pos,
            ColorResolver resolver) {
        return delegate.getBlockTint(
                origin,
                resolver);
    }
}
