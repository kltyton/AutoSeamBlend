package com.kltyton.autoseamblend.frontend.model.preview;

import com.kltyton.autoseamblend.authoring.preview.PreviewNeighborPosition;
import com.kltyton.autoseamblend.frontend.model.PreviewViewModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：始终以 matchBlocks 接收者为中心、以 connectBlocks 连接对象为邻居的只读预览场景；白色羊毛只可能存在于界面状态。
 *
 * English:
 * Read-only preview scene that always centers a matchBlocks receiver and uses
 * connectBlocks objects as neighbors. White wool can exist only as transient
 * UI state.
 */
public final class PreviewSceneState {
    private final Kind kind;
    private final List<BlockState> centerCycle;
    private final List<BlockState> neighborCycle;
    private final List<Block> explicitConnectionBlocks;
    private final boolean connectionPlaceholder;
    private final EnumMap<PreviewNeighborPosition, BlockState>
            neighbors =
                    new EnumMap<>(
                            PreviewNeighborPosition.class);
    private final PreviewCameraState camera = new PreviewCameraState();
    private int centerIndex;
    private int neighborIndex;
    private PreviewNeighborPosition focusedNeighbor;
    private HoveredFace hoveredFace;
    private long sceneRevision;

    private PreviewSceneState(
            Kind kind,
            List<BlockState> centerCycle,
            List<BlockState> neighborCycle,
            List<Block> explicitConnectionBlocks,
            boolean connectionPlaceholder) {
        this.kind = Objects.requireNonNull(
                kind,
                "kind");
        this.centerCycle = List.copyOf(
                Objects.requireNonNull(
                        centerCycle,
                        "centerCycle"));
        if (this.centerCycle.isEmpty()) {
            throw new IllegalArgumentException(
                    "preview scene needs a center state");
        }
        this.neighborCycle = List.copyOf(
                Objects.requireNonNull(
                        neighborCycle,
                        "neighborCycle"));
        this.explicitConnectionBlocks =
                List.copyOf(
                        Objects.requireNonNull(
                                explicitConnectionBlocks,
                                "explicitConnectionBlocks"));
        this.connectionPlaceholder =
                connectionPlaceholder;
    }

    /**
     * 中文：叠加方法仍以 matchBlocks 接收者为中心；connectBlocks 邻居只作为连接对象和纹理供体。
     *
     * English:
     * Additive overlays still center a matchBlocks receiver. connectBlocks
     * neighbors act only as connection objects and texture donors.
     */
    public static PreviewSceneState additiveOverlay(
            BlockState fallbackReceiver,
            List<BlockState> receivers,
            List<BlockState> connections) {
        Objects.requireNonNull(
                fallbackReceiver,
                "fallbackReceiver");
        List<BlockState> centers = receiverCycle(
                fallbackReceiver,
                receivers);
        List<BlockState> neighborCandidates =
                connectionCycle(connections);
        boolean placeholder = connections.isEmpty();
        return new PreviewSceneState(
                Kind.ADDITIVE_OVERLAY,
                centers,
                neighborCandidates,
                neighborCandidates.stream()
                        .map(BlockState::getBlock)
                        .toList(),
                placeholder);
    }

    /**
     * 中文：普通连接方法以匹配目标为中心，周围方块只负责提供原生邻接状态。
     *
     * English:
     * Ordinary connection methods keep the matched target at the center; the
     * surrounding blocks provide only native adjacency state.
     */
    public static PreviewSceneState connection(
            BlockState fallbackReceiver,
            List<BlockState> receivers,
            List<BlockState> connections) {
        Objects.requireNonNull(
                fallbackReceiver,
                "fallbackReceiver");
        List<BlockState> neighbors =
                connectionCycle(connections);
        return new PreviewSceneState(
                Kind.CONNECTION,
                receiverCycle(
                        fallbackReceiver,
                        receivers),
                neighbors,
                neighbors.stream()
                        .map(BlockState::getBlock)
                        .toList(),
                connections.isEmpty());
    }

    public static PreviewSceneState passthrough(
            BlockState fallbackReceiver,
            List<BlockState> receivers) {
        return new PreviewSceneState(
                Kind.PASSTHROUGH,
                receiverCycle(
                        Objects.requireNonNull(
                                fallbackReceiver,
                                "fallbackReceiver"),
                        receivers),
                List.of(),
                List.of(),
                false);
    }

    private static List<BlockState> receiverCycle(
            BlockState fallbackReceiver,
            List<BlockState> receivers) {
        ArrayList<BlockState> result = new ArrayList<>(
                Objects.requireNonNull(
                        receivers,
                        "receivers"));
        if (result.isEmpty()) {
            result.add(fallbackReceiver);
        }
        return List.copyOf(result);
    }

    private static List<BlockState> connectionCycle(
            List<BlockState> connections) {
        ArrayList<BlockState> result = new ArrayList<>(
                Objects.requireNonNull(
                        connections,
                        "connections"));
        if (result.isEmpty()) {
            result.add(Blocks.WHITE_WOOL
                    .defaultBlockState());
        }
        return List.copyOf(result);
    }

    public Kind kind() {
        return kind;
    }

    public boolean additiveOverlay() {
        return kind == Kind.ADDITIVE_OVERLAY;
    }

    public BlockState centerState() {
        return centerCycle.get(
                centerIndex % centerCycle.size());
    }

    public boolean cycleCenter() {
        if (centerCycle.size() < 2) {
            return false;
        }
        centerIndex = (centerIndex + 1)
                % centerCycle.size();
        sceneRevision = Math.addExact(
                sceneRevision,
                1);
        return true;
    }

    public Map<PreviewNeighborPosition, BlockState>
            neighbors() {
        return Collections.unmodifiableMap(
                new EnumMap<>(neighbors));
    }

    /**
     * 中文：把空悬停位置按下一次点击将放置的连接对象临时加入查询，但不改变场景。
     *
     * English:
     * Temporarily adds the connection object that the next click would place at
     * an empty hovered slot without mutating the scene.
     */
    public Map<PreviewNeighborPosition, BlockState>
            previewNeighbors() {
        EnumMap<PreviewNeighborPosition, BlockState>
                preview = new EnumMap<>(neighbors);
        Optional<PreviewNeighborPosition> hoveredNeighbor =
                hoveredFace == null
                        ? Optional.empty()
                        : hoveredFace.neighbor();
        hoveredNeighbor.filter(position ->
                        !preview.containsKey(position))
                .ifPresent(position ->
                        nextNeighborState().ifPresent(state ->
                                preview.put(position, state)));
        return Collections.unmodifiableMap(preview);
    }

    public boolean toggle(
            PreviewNeighborPosition position) {
        Objects.requireNonNull(position, "position");
        if (neighbors.remove(position) != null) {
            if (position == focusedNeighbor) {
                focusedNeighbor = neighbors.isEmpty()
                        ? null
                        : neighbors.keySet()
                                .iterator()
                                .next();
            }
            sceneRevision = Math.addExact(
                    sceneRevision,
                    1);
            return true;
        }
        if (neighborCycle.isEmpty()
                || kind == Kind.PASSTHROUGH) {
            return false;
        }
        BlockState selected =
                neighborCycle.get(
                        neighborIndex
                                % neighborCycle.size());
        neighborIndex = Math.addExact(
                neighborIndex,
                1);
        neighbors.put(position, selected);
        focusedNeighbor = position;
        sceneRevision = Math.addExact(
                sceneRevision,
                1);
        return true;
    }

    /**
     * 中文：悬停位置优先于最近点击位置，作为当前精确面查询的邻居。
     *
     * English:
     * The hovered slot takes precedence over the most recently clicked slot as
     * the neighbor driving the exact-face query.
     */
    public Optional<PreviewNeighborPosition>
            activeNeighbor() {
        if (hoveredFace != null
                && hoveredFace.neighbor().isPresent()) {
            return hoveredFace.neighbor();
        }
        return Optional.ofNullable(focusedNeighbor);
    }

    public Optional<BlockState> activeNeighborState() {
        return activeNeighbor().flatMap(position -> {
            BlockState placed = neighbors.get(position);
            return placed == null
                    ? nextNeighborState()
                    : Optional.of(placed);
        });
    }

    /**
     * 中文：更新瞬时悬停槽位；它既不增加场景代次，也不会被保存。
     *
     * English:
     * Updates the transient hovered slot without advancing the scene revision
     * or making the value persistent.
     */
    public boolean setHoveredFace(
            HoveredFace value) {
        if (Objects.equals(hoveredFace, value)) {
            return false;
        }
        hoveredFace = value;
        return true;
    }

    public Optional<HoveredFace> hoveredFace() {
        return Optional.ofNullable(hoveredFace);
    }

    private Optional<BlockState> nextNeighborState() {
        if (neighborCycle.isEmpty()
                || kind == Kind.PASSTHROUGH) {
            return Optional.empty();
        }
        return Optional.of(neighborCycle.get(
                neighborIndex % neighborCycle.size()));
    }

    public boolean connectionPlaceholder() {
        return connectionPlaceholder;
    }

    public List<Block> explicitConnectionBlocks() {
        return explicitConnectionBlocks;
    }

    public void clearNeighbors() {
        if (!neighbors.isEmpty()) {
            sceneRevision = Math.addExact(
                    sceneRevision,
                    1);
        }
        neighbors.clear();
        focusedNeighbor = null;
        hoveredFace = null;
        neighborIndex = 0;
    }

    public void rotate(
            double deltaX,
            double deltaY) {
        camera.rotate(deltaX, deltaY);
    }

    public void pan(
            double deltaX,
            double deltaY) {
        camera.pan(deltaX, deltaY);
    }

    public void zoom(double delta) {
        camera.zoom(delta);
    }

    public void resetCamera() {
        camera.reset();
    }

    public float yaw() {
        return camera.yaw();
    }

    public float pitch() {
        return camera.pitch();
    }

    public float panX() {
        return camera.panX();
    }

    public float panY() {
        return camera.panY();
    }

    public float zoom() {
        return camera.zoom();
    }

    /** 中文：返回渲染与拾取共享的不可变相机快照。 / English: Returns the immutable camera snapshot shared by rendering and picking. */
    public PreviewViewModel.Camera camera() {
        return camera.snapshot();
    }

    /** 中文：场景几何变化代次。 / English: Scene-geometry revision. */
    public long sceneRevision() {
        return sceneRevision;
    }

    /** 中文：相机投影变化代次。 / English: Camera-projection revision. */
    public long cameraRevision() {
        return camera.revision();
    }

    public enum Kind {
        ADDITIVE_OVERLAY,
        CONNECTION,
        PASSTHROUGH
    }

    /**
     * 中文：鼠标射线首先命中的预览方块与其外露面；空 Optional 表示中心方块。
     *
     * English:
     * Preview block and exposed face first hit by the pointer ray. An empty
     * Optional identifies the center block.
     */
    public record HoveredFace(
            Optional<PreviewNeighborPosition> neighbor,
            Direction face) {
        public HoveredFace {
            neighbor = Objects.requireNonNull(
                    neighbor,
                    "neighbor");
            Objects.requireNonNull(face, "face");
        }

        public BlockPos offset() {
            return neighbor.map(
                            value -> new BlockPos(
                                    value.x(),
                                    value.y(),
                                    value.z()))
                    .orElse(BlockPos.ZERO);
        }
    }
}
