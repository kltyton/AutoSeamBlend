package com.kltyton.autoseamblend.neoforge.compat.athena.runtime.overlay;

import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeStateSampler;
import com.kltyton.autoseamblend.compat.athena.runtime.AthenaStateProjection;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.authoring.preview.PreviewQuery;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolution;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolver.Donor;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import earth.terrarium.athena.api.client.neoforge.WrappedGetter;
import earth.terrarium.athena.api.client.utils.CtmState;
import java.util.Objects;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：把 overlay 的接收面连续性投影到 Athena 原生八方向 CTM 状态。
 *
 * English: Projects receiver continuity for an overlay into Athena's native eight-way CTM state.
 */
public final class AthenaNativeOverlayStateSampler {
    private AthenaNativeOverlayStateSampler() {}

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

    /** 中文：创作预览复用同一 Athena 原生状态入口，只替换文档连接谓词。 / English: Authoring preview reuses the same native Athena state entry and substitutes only its document connection predicate. */
    public static CtmState state(
            PreviewQuery query,
            Donor donor) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(donor, "donor");
        return AthenaNativeStateSampler.sampleOverlay(
                new WrappedGetter(query.level()),
                query.state(),
                query.pos(),
                query.face(),
                (neighborState, neighborAppearance) ->
                        query.connects(
                                        donor.state(),
                                        neighborAppearance)
                                && !query.connects(
                                        query.state(),
                                        neighborState),
                query.usesDocumentConnectionBlocks()
                        ? (ignoredPos, neighborAppearance) ->
                                neighborAppearance.getBlock()
                                        == query.state().getBlock()
                        : (ignoredPos, neighborAppearance) ->
                                OverlayDonorResolution
                                        .receivesOverlayFrom(
                                                EngineFamily.ATHENA,
                                                donor,
                                                neighborAppearance,
                                                query.face(),
                                                query.rules().rules(),
                                                query.surfaces()));
    }

}
