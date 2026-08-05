package com.kltyton.autoseamblend.compat.ctm_mod.runtime;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.compat.ctm_mod.runtime.overlay.CtmModOverlayTopology;
import com.kltyton.autoseamblend.authoring.preview.PreviewQuery;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolution;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolver.Donor;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 中文：为 CTM 适配器采样 Continuity 原生 overlay-17 状态。直边由 overlay 应用谓词决定；
 * 即使两条相邻直边都没有应用，同层邻居门控仍可保留视觉上的孤立角。
 * <p>
 * English:
 * Samples the Continuity-native overlay-17 state for the CTM adapter. Cardinal applications come
 * from the overlay predicate, while the same-overlay-neighbor gate may retain a visually isolated
 * corner even when neither adjacent cardinal applies.
 */
public final class CtmModOverlayStateSampler {
    private static final AtomicReference<AppearanceResolver>
            APPEARANCE_RESOLVER = new AtomicReference<>();

    private CtmModOverlayStateSampler() {}

    /**
     * 中文：注册 Loader 独占的方块外观解析（NeoForge BlockState.getAppearance）。
     *
     * English: Registers the Loader-exclusive block-appearance resolution
     * (NeoForge BlockState.getAppearance).
     */
    public static void installAppearanceResolver(
            AppearanceResolver resolver) {
        APPEARANCE_RESOLVER.set(Objects.requireNonNull(
                resolver,
                "resolver"));
    }

    public static NeighborConnections sample(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState receiver,
            Direction face,
            Donor donor,
            ConnectionRuleSet<Block> rules,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(donor, "donor");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(surfaces, "surfaces");

        return sample(
                level,
                pos,
                receiver,
                face,
                (candidate, candidateAppearance) ->
                        connects(
                                        rules,
                                        donor.state().getBlock(),
                                        candidateAppearance.getBlock())
                                && !connects(
                                        rules,
                                        receiver.getBlock(),
                                        candidate.getBlock()),
                appearance -> OverlayDonorResolution
                        .receivesOverlayFrom(
                                EngineFamily.CTM_MOD,
                                donor,
                                appearance,
                                face,
                                rules,
                                surfaces));
    }

    public static NeighborConnections sample(
            PreviewQuery query,
            Donor donor) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(donor, "donor");
        return sample(
                query.level(),
                query.pos(),
                query.state(),
                query.face(),
                (candidate, candidateAppearance) ->
                        query.connects(
                                        donor.state(),
                                        candidateAppearance)
                                && !query.connects(
                                        query.state(),
                                        candidate),
                appearance -> appearance.getBlock()
                        == query.state().getBlock());
    }

    private static NeighborConnections sample(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState receiver,
            Direction face,
            CandidatePredicate appliesOverlay,
            Predicate<BlockState> sameOverlayPredicate) {
        Objects.requireNonNull(appliesOverlay, "appliesOverlay");
        Objects.requireNonNull(
                sameOverlayPredicate,
                "sameOverlayPredicate");

        List<Direction> directions = planarDirections(face);
        boolean[] applications = new boolean[4];
        boolean[] sameOverlay = new boolean[4];
        boolean[] cornerApplications = new boolean[4];
        for (int index = 0; index < directions.size(); index++) {
            BlockPos neighborPos = pos.relative(directions.get(index));
            BlockState neighbor = level.getBlockState(neighborPos);
            BlockState neighborAppearance = appearance(
                    level,
                    pos,
                    receiver,
                    neighborPos,
                    face);
            applications[index] = visibleFullBlock(
                            level,
                            neighborPos,
                            neighbor,
                            face)
                    && appliesOverlay.test(
                            neighbor,
                            neighborAppearance);
            sameOverlay[index] = sameOverlayPredicate.test(
                    neighborAppearance);
        }
        for (int index = 0; index < directions.size(); index++) {
            int next = (index + 1) & 3;
            // 中文：锁定 Continuity 只在这两条直边均未应用时考虑该角，并要求至少一侧属于同一 overlay；这不是标准 CTM 的“两条直边都连接”门控。
            // English: Locked Continuity considers this corner only when neither adjacent cardinal applies and at least one side has the same overlay; this is not standard CTM's both-cardinals-connected gate.
            if (applications[index]
                    || applications[next]
                    || (!sameOverlay[index]
                            && !sameOverlay[next])) {
                continue;
            }
            BlockPos cornerPos = pos
                    .relative(directions.get(index))
                    .relative(directions.get(next));
            BlockState corner = level.getBlockState(cornerPos);
            BlockState cornerAppearance = appearance(
                    level,
                    pos,
                    receiver,
                    cornerPos,
                    face);
            cornerApplications[index] = visibleFullBlock(
                            level,
                            cornerPos,
                            corner,
                            face)
                    && appliesOverlay.test(
                            corner,
                            cornerAppearance);
        }
        return CtmModOverlayTopology.state(
                applications,
                sameOverlay,
                cornerApplications);
    }

    /**
     * 中文：overlay 原生方向固定为面规范的左、下、右、上，不随接收 Quad 的 UV 旋转。
     *
     * <p>English: Native overlay directions are canonical left, down, right, and up for the face;
     * they do not follow the receiver quad's UV rotation.
     */
    public static List<Direction> planarDirections(Direction face) {
        return CtmModOverlayTopology.planarDirections(face);
    }

    private static boolean visibleFullBlock(
            BlockAndTintGetter level,
            BlockPos candidatePos,
            BlockState candidate,
            Direction face) {
        return candidate.isCollisionShapeFullBlock(
                        level,
                        candidatePos)
                && !level.getBlockState(
                                candidatePos.relative(face))
                        .isSolidRender();
    }

    private static BlockState appearance(
            BlockAndTintGetter level,
            BlockPos origin,
            BlockState receiver,
            BlockPos candidatePos,
            Direction face) {
        BlockState candidate = level.getBlockState(candidatePos);
        AppearanceResolver resolver =
                APPEARANCE_RESOLVER.get();
        if (resolver == null) {
            return candidate;
        }
        return resolver.appearance(
                candidate,
                level,
                candidatePos,
                face,
                receiver,
                origin);
    }

    /**
     * 中文：Loader 独占方块外观解析契约。
     *
     * English: Loader-exclusive block-appearance resolution contract.
     */
    @FunctionalInterface
    public interface AppearanceResolver {
        BlockState appearance(
                BlockState candidate,
                BlockAndTintGetter level,
                BlockPos candidatePos,
                Direction face,
                BlockState receiver,
                BlockPos origin);
    }

    private static boolean connects(
            ConnectionRuleSet<Block> rules,
            Block current,
            Block neighbor) {
        return rules.isTarget(current)
                ? rules.connects(current, neighbor)
                : current == neighbor;
    }

    @FunctionalInterface
    private interface CandidatePredicate {
        boolean test(
                BlockState candidate,
                BlockState candidateAppearance);
    }
}
