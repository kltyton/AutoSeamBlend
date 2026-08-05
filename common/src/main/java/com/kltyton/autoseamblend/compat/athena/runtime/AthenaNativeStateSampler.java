package com.kltyton.autoseamblend.compat.athena.runtime;

import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.texture.geometry.TextureBasis;
import com.kltyton.autoseamblend.texture.geometry.WorldDirection;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import earth.terrarium.athena.api.client.utils.AppearanceAndTintGetter;
import earth.terrarium.athena.api.client.utils.CtmState;
import earth.terrarium.athena.api.client.utils.CtmUtils;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：集中 Athena 原生八邻域采样和面纹理空间投影；Loader 只提供外观 getter 与原生谓词。
 * English: Centralizes Athena eight-neighbor sampling and face texture-space projection; loaders
 * only provide their native appearance getter and loader-specific predicates.
 */
public final class AthenaNativeStateSampler {
    private AthenaNativeStateSampler() {}

    /**
     * 中文：按项目连接规则执行 Athena 原生采样；文档连接集合优先于配置规则。
     * English: Samples through Athena using project connection rules; document-declared blocks
     * take precedence over configured rules.
     */
    public static CtmState sample(
            AppearanceAndTintGetter getter,
            BlockState originState,
            BlockPos pos,
            Direction face,
            ConnectionRuleSet<Block> rules,
            Set<Block> documentConnectionBlocks) {
        Objects.requireNonNull(getter, "getter");
        Objects.requireNonNull(originState, "originState");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(documentConnectionBlocks, "documentConnectionBlocks");
        return sample(
                getter,
                originState,
                pos,
                face,
                (neighborPos, neighborState, neighborAppearance) ->
                        AthenaStateProjection.connects(
                                rules,
                                originState.getBlock(),
                                neighborAppearance.getBlock(),
                                documentConnectionBlocks));
    }

    /**
     * 中文：共享 overlay 的 application/supporting 双状态采样与原生 carrier 投影。
     * English: Shares overlay application/supporting dual-state sampling and native carrier
     * projection.
     */
    public static CtmState sampleOverlay(
            AppearanceAndTintGetter getter,
            BlockState receiver,
            BlockPos pos,
            Direction face,
            AthenaStateProjection.OverlayApplication application,
            BiPredicate<BlockPos, BlockState> sameOverlay) {
        Objects.requireNonNull(getter, "getter");
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(application, "application");
        Objects.requireNonNull(sameOverlay, "sameOverlay");
        CtmState applications = CtmState.from(
                getter,
                receiver,
                pos,
                face,
                (neighborPos, neighborState, neighborAppearance) ->
                        AthenaStateProjection.applies(
                                getter,
                                neighborPos,
                                neighborState,
                                neighborAppearance,
                                face,
                                application));
        CtmState supportingReceivers = CtmState.from(
                getter,
                receiver,
                pos,
                face,
                (neighborPos, neighborState, neighborAppearance) ->
                        AthenaStateProjection.applies(
                                getter,
                                neighborPos,
                                neighborState,
                                neighborAppearance,
                                face,
                                (ignoredState, appearance) ->
                                        sameOverlay.test(neighborPos, appearance)));
        return AthenaCtmStateBridge.toNative(AthenaStateProjection.nativeCarrierState(
                AthenaCtmStateBridge.toCommon(applications),
                AthenaCtmStateBridge.toCommon(supportingReceivers)));
    }

    /**
     * 中文：把 Athena 八方向状态无损投影为项目连接位；不重新选择原生槽位。
     *
     * English: Projects Athena's eight-direction state losslessly into project connection bits
     * without selecting a native slot again.
     */
    public static NeighborConnections connections(CtmState state) {
        return AthenaCtmStateBridge.toCommon(
                        Objects.requireNonNull(state, "state"))
                .toConnections();
    }

    /**
     * 中文：使用 Athena 原生回调采样，供需要保留 loader 原生谓词的桥接路径使用。
     * English: Samples with an Athena-native callback for bridges that must preserve a loader's
     * native predicate.
     */
    public static CtmState sample(
            AppearanceAndTintGetter getter,
            BlockState originState,
            BlockPos pos,
            Direction face,
            CtmState.ConnectionCheck check) {
        Objects.requireNonNull(getter, "getter");
        Objects.requireNonNull(originState, "originState");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(check, "check");
        return CtmState.from(getter, originState, pos, face, check);
    }

    /**
     * 中文：采样并投影到实际 Quad 纹理方向。
     * English: Samples and projects into the actual Quad texture directions.
     */
    public static CtmState sampleInTextureSpace(
            AppearanceAndTintGetter getter,
            BlockState originState,
            BlockPos pos,
            Direction face,
            TextureBasis textureBasis,
            ConnectionRuleSet<Block> rules,
            Set<Block> documentConnectionBlocks) {
        CtmState canonical = sample(
                getter,
                originState,
                pos,
                face,
                rules,
                documentConnectionBlocks);
        return projectToTextureSpace(canonical, textureBasis, WorldDirection.valueOf(face.name()));
    }

    /**
     * 中文：使用 Athena 原生查询谓词采样并投影；CtmUtils/CtmState 参数组织保持在 common。
     * English: Samples and projects with Athena's native query predicate; CtmUtils/CtmState
     * argument wiring stays in common.
     */
    public static CtmState sampleInTextureSpace(
            AppearanceAndTintGetter getter,
            BlockState originState,
            BlockPos pos,
            Direction face,
            TextureBasis textureBasis,
            BiPredicate<BlockState, BlockState> nativePredicate) {
        Objects.requireNonNull(nativePredicate, "nativePredicate");
        return sampleInTextureSpace(
                getter,
                originState,
                pos,
                face,
                textureBasis,
                CtmUtils.check(
                        getter,
                        originState,
                        pos,
                        face,
                        nativePredicate));
    }

    /**
     * 中文：使用已构造的 Athena ConnectionCheck 采样并投影到纹理空间。
     * English: Samples with an existing Athena ConnectionCheck and projects into texture space.
     */
    public static CtmState sampleInTextureSpace(
            AppearanceAndTintGetter getter,
            BlockState originState,
            BlockPos pos,
            Direction face,
            TextureBasis textureBasis,
            CtmState.ConnectionCheck check) {
        CtmState canonical = sample(
                getter,
                originState,
                pos,
                face,
                check);
        return projectToTextureSpace(canonical, textureBasis, WorldDirection.valueOf(face.name()));
    }

    /**
     * 中文：将已有 Athena 状态投影到 Quad 纹理方向，保留退化面方向的原状态。
     * English: Projects an existing Athena state into Quad texture directions while preserving
     * the canonical state for a non-matching face.
     */
    public static CtmState projectToTextureSpace(
            CtmState canonical,
            TextureBasis textureBasis,
            WorldDirection worldFace) {
        Objects.requireNonNull(canonical, "canonical");
        return AthenaCtmStateBridge.toNative(
                AthenaStateProjection.projectToTextureSpace(
                        AthenaCtmStateBridge.toCommon(canonical),
                        textureBasis,
                        worldFace));
    }
}
