package com.kltyton.autoseamblend.neoforge.compat.athena.runtime;

import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeStateSampler;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.texture.geometry.TextureBasis;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import earth.terrarium.athena.api.client.neoforge.WrappedGetter;
import earth.terrarium.athena.api.client.utils.CtmState;
import java.util.Set;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** 中文：把 Athena 感知外观且面定向的 CTM 状态适配到项目 IR。 / English: Adapts Athena's appearance-aware, face-oriented CTM state to the project IR. */
final class AthenaNativeConnectionSampler {
    private AthenaNativeConnectionSampler() {}

    static NeighborConnections sample(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState originState,
            Direction face,
            ConnectionRuleSet<Block> rules) {
        return sample(
                level,
                pos,
                originState,
                face,
                rules,
                Set.of());
    }

    static NeighborConnections sample(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState originState,
            Direction face,
            ConnectionRuleSet<Block> rules,
            Set<Block> documentConnectionBlocks) {
        return connections(state(
                        level,
                        pos,
                        originState,
                        face,
                        rules,
                        documentConnectionBlocks));
    }

    /** 中文：只做 Athena 八方向字段到项目位掩码的无损投影。 / English: Losslessly projects Athena's eight directional fields to the project bit mask. */
    static NeighborConnections connections(CtmState state) {
        return AthenaNativeStateSampler.connections(state);
    }

    static CtmState state(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState originState,
            Direction face,
            ConnectionRuleSet<Block> rules) {
        return state(
                level,
                pos,
                originState,
                face,
                rules,
                Set.of());
    }

    static CtmState stateInTextureSpace(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState originState,
            Direction face,
            TextureBasis textureBasis,
            ConnectionRuleSet<Block> rules) {
        // 中文：Athena 4.7.3 的字段顺序本身正确；这里只把面规范方向旋转/镜像到实际 Quad 的纹理空间。
        // English: Athena 4.7.3's field order is already correct; only rotate/mirror the face-canonical state into the actual quad texture space.
        return AthenaNativeStateSampler.sampleInTextureSpace(
                new WrappedGetter(level),
                originState,
                pos,
                face,
                textureBasis,
                rules,
                Set.of());
    }

    static CtmState state(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState originState,
            Direction face,
            ConnectionRuleSet<Block> rules,
            Set<Block> documentConnectionBlocks) {
        return AthenaNativeStateSampler.sample(
                new WrappedGetter(level),
                originState,
                pos,
                face,
                rules,
                documentConnectionBlocks);
    }

}
