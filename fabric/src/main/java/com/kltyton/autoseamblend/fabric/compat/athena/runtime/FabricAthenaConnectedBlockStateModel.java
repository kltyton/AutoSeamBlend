package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：Athena 动态模型包装器，保留原生 Athena 发射路径；AutoBlend 完成通过
 * {@link FabricAthenaNativeQuadProcessor} 在后续验证轮接入发射。
 *
 * English: Athena dynamic-model wrapper that retains the native Athena emission
 * path; AutoBlend completion is routed through
 * {@link FabricAthenaNativeQuadProcessor} in the next validated iteration.
 */
public final class FabricAthenaConnectedBlockStateModel
        extends WrapperBlockStateModel {
    private final BlockState bakedState;

    public FabricAthenaConnectedBlockStateModel(
            BlockStateModel delegate,
            BlockState bakedState) {
        super(Objects.requireNonNull(
                delegate,
                "delegate"));
        this.bakedState = Objects.requireNonNull(
                bakedState,
                "bakedState");
    }

    @Override
    public void collectParts(
            RandomSource random,
            List<BlockStateModelPart> output) {
        super.collectParts(random, output);
    }

    @Override
    public void emitQuads(
            QuadEmitter emitter,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            RandomSource random,
            Predicate<Direction> cullTest) {
        super.emitQuads(
                emitter,
                level,
                pos,
                state,
                random,
                cullTest);
    }
}
