package com.kltyton.autoseamblend.runtime.surface;

import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 中文：统一 TOP 方法的轴向、正向邻居和连接规则判定。
 *
 * English: Shares the TOP method's axis, positive-neighbor, and connection-rule predicate.
 */
public final class TopSurfaceConnectionPolicy {
    private TopSurfaceConnectionPolicy() {}

    /**
     * 中文：返回当前面对应的 TOP donor 面；空值表示当前面不参与 TOP 连接。
     *
     * English: Returns the TOP donor face for the current face, or empty when TOP does not
     * connect for this query.
     */
    public static Optional<Direction> resolve(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction face,
            ConnectionRuleSet<Block> rules) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(rules, "rules");
        Direction.Axis axis = state.hasProperty(BlockStateProperties.AXIS)
                ? state.getValue(BlockStateProperties.AXIS)
                : Direction.Axis.Y;
        if (face.getAxis() == axis) {
            return Optional.empty();
        }
        Direction top = Direction.fromAxisAndDirection(
                axis,
                Direction.AxisDirection.POSITIVE);
        BlockState neighbor = level.getBlockState(pos.relative(top));
        Block block = state.getBlock();
        boolean connected = rules.isTarget(block)
                ? rules.connects(block, neighbor.getBlock())
                : block == neighbor.getBlock();
        return connected ? Optional.of(top) : Optional.empty();
    }
}
