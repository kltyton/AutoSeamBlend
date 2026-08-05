package com.kltyton.autoseamblend.neoforge.compat.ctm_mod.runtime;

import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.texture.geometry.TextureBasis;
import com.kltyton.autoseamblend.texture.geometry.TextureCorner;
import com.kltyton.autoseamblend.texture.geometry.TextureEdge;
import com.kltyton.autoseamblend.texture.geometry.WorldDirection;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import io.github.chiselteam.ctm.api.geometry.StandardCTMKey;
import io.github.chiselteam.ctm.api.model.CTMVariant;
import io.github.chiselteam.ctm.api.strategy.CTMKind;
import io.github.chiselteam.ctm.api.strategy.CTMLogic;
import io.github.chiselteam.ctm.client.baked.StandardCTMBlockStateModel;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：公开 CTM Lib 原生标准键，同时保持 AutoSeamBlend 选择器组为权威。
 *
 * English:
 * Exposes CTM Lib's native standard key while keeping AutoSeamBlend selector groups authoritative.
 */
public final class CtmModNativeConnectionSampler
        extends StandardCTMBlockStateModel {
    /**
     * 中文：由 Loader 提供的方块外观解析边界；Minecraft 通用 BlockState 本身不携带 Loader 的外观扩展。
     *
     * <p>English: Loader-supplied block-appearance boundary; common Minecraft BlockState does not
     * carry a loader-specific appearance extension itself.
     */
    @FunctionalInterface
    public interface AppearanceResolver {
        BlockState resolve(
                BlockAndTintGetter level,
                BlockPos pos,
                Direction face,
                BlockState state,
                BlockState otherState,
                BlockPos otherPos);
    }

    private final ConnectionRuleSet<Block> rules;
    private final Block target;
    private final Set<Block> documentConnectionBlocks;
    private final boolean overlay;
    private final AppearanceResolver appearanceResolver;

    public CtmModNativeConnectionSampler(
            TextureAtlasSprite source,
            Block target,
            ConnectionRuleSet<Block> rules,
            boolean overlay,
            AppearanceResolver appearanceResolver) {
        this(source, target, rules, Set.of(), overlay, appearanceResolver);
    }

    public CtmModNativeConnectionSampler(
            TextureAtlasSprite source,
            Block target,
            ConnectionRuleSet<Block> rules,
            Set<Block> documentConnectionBlocks,
            boolean overlay,
            AppearanceResolver appearanceResolver) {
        super(
                Set.of(),
                Set.of(),
                false,
                Map.of(),
                Map.of(),
                Objects.requireNonNull(source, "source"),
                CTMVariant.of(
                        Objects.requireNonNull(target, "target"),
                        CTMKind.STANDARD));
        this.rules = Objects.requireNonNull(rules, "rules");
        this.target = target;
        this.documentConnectionBlocks = Set.copyOf(
                Objects.requireNonNull(
                        documentConnectionBlocks,
                        "documentConnectionBlocks"));
        this.overlay = overlay;
        this.appearanceResolver = Objects.requireNonNull(
                appearanceResolver,
                "appearanceResolver");
    }

    public NeighborConnections sample(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction face,
            TextureBasis basis,
            RandomSource random) {
        return sample(
                sampleKey(level, pos, state, random),
                face,
                basis);
    }

    /**
     * 中文：优先轴方法需要独立保存四个对角；标准 47 状态键会按设计丢弃没有相邻直边的对角。
     *
     * <p>English: Priority-axis methods retain all four diagonals independently; the standard
     * 47-state key intentionally discards a diagonal without its adjacent cardinal edges.
     */
    public NeighborConnections sampleIndependent(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction face,
            TextureBasis basis) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(basis, "basis");
        if (basis.face() != world(face)) {
            basis = TextureBasis.canonical(world(face));
        }
        int bits = 0;
        for (TextureEdge edge : TextureEdge.values()) {
            Direction side = direction(basis, edge);
            if (shouldConnectSide(
                    level,
                    pos,
                    state,
                    face,
                    side)) {
                bits |= 1 << edge.connectionBit();
            }
        }
        for (TextureCorner corner : TextureCorner.values()) {
            Direction first = direction(
                    basis,
                    corner.firstEdge());
            Direction second = direction(
                    basis,
                    corner.secondEdge());
            if (isCornerBlockPresent(
                    level,
                    pos,
                    state,
                    face,
                    first,
                    second)) {
                bits |= 1 << corner.connectionBit();
            }
        }
        return NeighborConnections.fromBits(bits);
    }

    public StandardCTMKey sampleKey(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            RandomSource random) {
        StandardCTMKey nativeKey = computeCTMKey(
                level,
                pos,
                state,
                random);
        // 中文：CTM Lib 26.1.2.2 按 EAST、WEST 计算最后两面，却按 west、east 写入记录；在适配边界恢复字段语义。
        // English: CTM Lib 26.1.2.2 computes EAST then WEST but stores record fields as west then east; restore their semantic order here.
        return new StandardCTMKey(
                nativeKey.down(),
                nativeKey.up(),
                nativeKey.north(),
                nativeKey.south(),
                nativeKey.east(),
                nativeKey.west());
    }

    public NeighborConnections sample(
            StandardCTMKey key,
            Direction face,
            TextureBasis basis) {
        return decode(
                Objects.requireNonNull(key, "key"),
                Objects.requireNonNull(face, "face"),
                Objects.requireNonNull(basis, "basis"));
    }

    @Override
    protected boolean shouldConnectSide(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction face,
            Direction side) {
        return matches(
                level,
                pos,
                state,
                face,
                pos.relative(side));
    }

    @Override
    protected boolean isCornerBlockPresent(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction face,
            Direction side1,
            Direction side2) {
        return matches(
                level,
                pos,
                state,
                face,
                pos.relative(side1).relative(side2));
    }

    private boolean matches(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction face,
            BlockPos neighborPos) {
        BlockState neighbor = level.getBlockState(neighborPos);
        if (overlay) {
            BlockState neighborAppearance = appearanceResolver.resolve(
                    level,
                    neighborPos,
                    face,
                    neighbor,
                    state,
                    pos);
            return !neighborAppearance.is(Blocks.AIR)
                    && connects(
                            target,
                            neighborAppearance.getBlock());
        }
        BlockState originAppearance = appearanceResolver.resolve(
                level,
                pos,
                face,
                state,
                neighbor,
                neighborPos);
        if (originAppearance.is(Blocks.AIR)) {
            return false;
        }
        BlockState neighborAppearance = appearanceResolver.resolve(
                level,
                neighborPos,
                face,
                neighbor,
                state,
                pos);
        return !neighborAppearance.is(Blocks.AIR)
                && connects(
                        originAppearance.getBlock(),
                        neighborAppearance.getBlock());
    }

    private boolean connects(Block current, Block neighbor) {
        if (!documentConnectionBlocks.isEmpty()) {
            return documentConnectionBlocks.contains(neighbor);
        }
        return rules.isTarget(current)
                ? rules.connects(current, neighbor)
                : current == neighbor;
    }

    private static NeighborConnections decode(
            StandardCTMKey key,
            Direction face,
            TextureBasis basis) {
        if (basis.face() != world(face)) {
            basis = TextureBasis.canonical(world(face));
        }
        Direction[] plane =
                CTMLogic.AXIS_PLANE_DIRECTIONS[
                        face.getAxis().ordinal()];
        int bits = 0;
        for (int corner = 0; corner < 4; corner++) {
            CTMLogic logic = key.get(face, corner);
            boolean horizontal =
                    logic == CTMLogic.HORIZONTAL
                            || logic == CTMLogic.CORNER
                            || logic == CTMLogic.CORNERLESS;
            boolean vertical =
                    logic == CTMLogic.VERTICAL
                            || logic == CTMLogic.CORNER
                            || logic == CTMLogic.CORNERLESS;
            boolean first = (corner & 1) == 0
                    ? horizontal
                    : vertical;
            boolean second = (corner & 1) == 0
                    ? vertical
                    : horizontal;
            TextureEdge firstEdge =
                    edge(basis, plane[corner]);
            TextureEdge secondEdge =
                    edge(
                            basis,
                            plane[(corner + 1) & 3]);
            if (first) {
                bits |= 1 << firstEdge.connectionBit();
            }
            if (second) {
                bits |= 1 << secondEdge.connectionBit();
            }
            if (logic == CTMLogic.CORNERLESS) {
                bits |= 1
                        << TextureCorner
                                .between(firstEdge, secondEdge)
                                .connectionBit();
            }
        }
        return NeighborConnections.fromBits(bits);
    }

    private static TextureEdge edge(
            TextureBasis basis,
            Direction direction) {
        WorldDirection world = world(direction);
        if (world == basis.left()) {
            return TextureEdge.LEFT;
        }
        if (world == basis.down()) {
            return TextureEdge.DOWN;
        }
        if (world == basis.right()) {
            return TextureEdge.RIGHT;
        }
        if (world == basis.up()) {
            return TextureEdge.UP;
        }
        throw new IllegalArgumentException(
                "direction "
                        + direction
                        + " does not lie in "
                        + basis.face()
                        + " texture plane");
    }

    private static WorldDirection world(
            Direction direction) {
        return WorldDirection.valueOf(
                direction.name());
    }

    private static Direction direction(
            TextureBasis basis,
            TextureEdge edge) {
        WorldDirection direction = switch (edge) {
            case LEFT -> basis.left();
            case DOWN -> basis.down();
            case RIGHT -> basis.right();
            case UP -> basis.up();
        };
        return Direction.valueOf(direction.name());
    }
}
