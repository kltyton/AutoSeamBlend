package com.kltyton.autoseamblend.runtime.overlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 中文：提供 overlay 采样所需的稳定平面方向和四直边加四正交对角邻位。
 * English: Provides stable planar directions and four cardinals plus four orthogonal diagonals
 * used by overlay sampling.
 */
public final class PlanarOverlayNeighborhood {
    private static final Map<Direction, List<Direction>> PLANAR_DIRECTIONS =
            buildPlanarDirections();

    private PlanarOverlayNeighborhood() {}

    /**
     * 中文：返回与目标面正交的稳定四方向序列。
     * English: Returns the stable four-direction sequence orthogonal to a target face.
     */
    public static List<Direction> planarDirections(Direction face) {
        return PLANAR_DIRECTIONS.get(Objects.requireNonNull(face, "face"));
    }

    /**
     * 中文：按稳定顺序返回四直边和四正交对角邻位。
     * English: Returns four cardinals and four orthogonal diagonals in stable order.
     */
    public static List<NeighborOffset> neighbors(Direction face) {
        return neighbors(planarDirections(face));
    }

    /**
     * 中文：使用调用方提供的四方向顺序生成相同的邻位路径，保留引擎方向顺序。
     * English: Builds the same neighbor paths from caller-provided directions while preserving
     * the caller's engine order.
     */
    public static List<NeighborOffset> neighbors(List<Direction> directions) {
        Objects.requireNonNull(directions, "directions");
        if (directions.size() != 4 || directions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("overlay neighborhood requires four directions");
        }
        ArrayList<NeighborOffset> result = new ArrayList<>(8);
        for (Direction direction : directions) {
            result.add(new NeighborOffset(List.of(direction)));
        }
        for (int first = 0; first < directions.size(); first++) {
            for (int second = first + 1; second < directions.size(); second++) {
                Direction left = directions.get(first);
                Direction right = directions.get(second);
                if (left.getAxis() != right.getAxis()) {
                    result.add(new NeighborOffset(List.of(left, right)));
                }
            }
        }
        return List.copyOf(result);
    }

    private static Map<Direction, List<Direction>> buildPlanarDirections() {
        EnumMap<Direction, List<Direction>> result = new EnumMap<>(Direction.class);
        for (Direction face : Direction.values()) {
            ArrayList<Direction> directions = new ArrayList<>(4);
            for (Direction direction : Direction.values()) {
                if (direction.getAxis() != face.getAxis()) {
                    directions.add(direction);
                }
            }
            result.put(face, List.copyOf(directions));
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 中文：一个从原点开始依次应用方向的不可变邻位路径。
     * English: An immutable neighbor path that applies directions successively from an origin.
     */
    public record NeighborOffset(List<Direction> path) {
        public NeighborOffset {
            path = List.copyOf(Objects.requireNonNull(path, "path"));
            if (path.isEmpty() || path.size() > 2 || path.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("neighbor path must contain one or two directions");
            }
        }

        public BlockPos positionFrom(BlockPos origin) {
            BlockPos position = Objects.requireNonNull(origin, "origin");
            for (Direction direction : path) {
                position = position.relative(direction);
            }
            return position;
        }
    }
}
