package com.kltyton.autoseamblend.compat.athena.runtime;

import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.texture.geometry.TextureBasis;
import com.kltyton.autoseamblend.texture.geometry.WorldDirection;
import java.util.Objects;
import java.util.Set;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：集中 Athena 八方向状态的共同投影、overlay 载体与连接判定。
 * English: Centralizes shared Athena eight-way projections, overlay carriers, and connection predicates.
 *
 * <p>Loader adapters only convert their native {@code CtmState} to and from
 * {@link AthenaConnectionState}; no Loader-specific or Athena engine type crosses
 * this class. Shared Minecraft query types are intentionally part of the projection
 * port so both loaders use the same occlusion and collision semantics.</p>
 */
public final class AthenaStateProjection {
    private AthenaStateProjection() {}

    /**
     * 中文：把 overlay 应用状态转换成 Athena 原生接收面状态。
     * English: Converts overlay applications into Athena's native receiver state.
     */
    public static AthenaConnectionState nativeCarrierState(
            AthenaConnectionState applications,
            AthenaConnectionState supportingReceivers) {
        Objects.requireNonNull(applications, "applications");
        Objects.requireNonNull(supportingReceivers, "supportingReceivers");
        return AthenaConnectionState.of(
                !applications.up(),
                !applications.down(),
                !applications.left(),
                !applications.right(),
                nativeCorner(
                        applications.upLeft(),
                        supportingReceivers.up(),
                        supportingReceivers.left()),
                nativeCorner(
                        applications.upRight(),
                        supportingReceivers.up(),
                        supportingReceivers.right()),
                nativeCorner(
                        applications.downLeft(),
                        supportingReceivers.down(),
                        supportingReceivers.left()),
                nativeCorner(
                        applications.downRight(),
                        supportingReceivers.down(),
                        supportingReceivers.right()));
    }

    /**
     * 中文：按面规范方向读取一个直边并映射到实际纹理方向。
     * English: Reads one cardinal edge in face-canonical coordinates and maps it to texture space.
     */
    public static boolean edge(
            AthenaConnectionState state,
            TextureBasis canonical,
            WorldDirection direction) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(canonical, "canonical");
        Objects.requireNonNull(direction, "direction");
        if (direction == canonical.up()) return state.up();
        if (direction == canonical.down()) return state.down();
        if (direction == canonical.left()) return state.left();
        if (direction == canonical.right()) return state.right();
        throw new IllegalArgumentException(
                "Direction is not in the CTM face plane: " + direction);
    }

    /**
     * 中文：按面规范方向读取一个对角并映射到实际纹理方向。
     * English: Reads one diagonal in face-canonical coordinates and maps it to texture space.
     */
    public static boolean corner(
            AthenaConnectionState state,
            TextureBasis canonical,
            WorldDirection first,
            WorldDirection second) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(canonical, "canonical");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (samePair(first, second, canonical.up(), canonical.left())) {
            return state.upLeft();
        }
        if (samePair(first, second, canonical.up(), canonical.right())) {
            return state.upRight();
        }
        if (samePair(first, second, canonical.down(), canonical.left())) {
            return state.downLeft();
        }
        if (samePair(first, second, canonical.down(), canonical.right())) {
            return state.downRight();
        }
        throw new IllegalArgumentException(
                "Directions do not form a CTM corner: " + first + ", " + second);
    }

    /**
     * 中文：把面规范状态投影到实际 Quad 的纹理方向。
     * English: Projects a face-canonical state into the actual Quad texture directions.
     */
    public static AthenaConnectionState projectToTextureSpace(
            AthenaConnectionState canonicalState,
            TextureBasis textureBasis,
            WorldDirection worldFace) {
        Objects.requireNonNull(canonicalState, "canonicalState");
        Objects.requireNonNull(textureBasis, "textureBasis");
        Objects.requireNonNull(worldFace, "worldFace");
        if (textureBasis.face() != worldFace) {
            return canonicalState;
        }
        TextureBasis canonical = TextureBasis.canonical(worldFace);
        return AthenaConnectionState.of(
                edge(canonicalState, canonical, textureBasis.up()),
                edge(canonicalState, canonical, textureBasis.down()),
                edge(canonicalState, canonical, textureBasis.left()),
                edge(canonicalState, canonical, textureBasis.right()),
                corner(canonicalState, canonical, textureBasis.up(), textureBasis.left()),
                corner(canonicalState, canonical, textureBasis.up(), textureBasis.right()),
                corner(canonicalState, canonical, textureBasis.down(), textureBasis.left()),
                corner(canonicalState, canonical, textureBasis.down(), textureBasis.right()));
    }

    /**
     * 中文：无文档连接覆盖时按配置规则或同方块身份判定连接。
     * English: Resolves a connection through configured rules or same-object identity without a document override.
     */
    public static <T> boolean connects(
            ConnectionRuleSet<T> rules,
            T current,
            T neighbor) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(neighbor, "neighbor");
        return rules.isTarget(current)
                ? rules.connects(current, neighbor)
                : current == neighbor;
    }

    /**
     * 中文：文档声明连接方块时使用文档集合，否则回退到配置规则或身份连接。
     * English: Uses document-declared connection blocks when present, otherwise configured rules or identity.
     */
    public static <T> boolean connects(
            ConnectionRuleSet<T> rules,
            T current,
            T neighbor,
            Set<T> documentConnectionBlocks) {
        Objects.requireNonNull(documentConnectionBlocks, "documentConnectionBlocks");
        if (!documentConnectionBlocks.isEmpty()) {
            return documentConnectionBlocks.contains(neighbor);
        }
        return connects(rules, current, neighbor);
    }

    /**
     * 中文：执行共享的遮挡与完整碰撞门控，再调用 loader 传入的应用谓词。
     * English: Applies the shared occlusion/full-collision gate before invoking the loader-supplied application predicate.
     */
    public static boolean applies(
            BlockAndTintGetter level,
            BlockPos neighborPos,
            BlockState neighborState,
            BlockState neighborAppearance,
            Direction face,
            OverlayApplication application) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(neighborPos, "neighborPos");
        Objects.requireNonNull(neighborState, "neighborState");
        Objects.requireNonNull(neighborAppearance, "neighborAppearance");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(application, "application");
        BlockPos outsidePos = neighborPos.relative(face);
        BlockState outsideState = level.getBlockState(outsidePos);
            return !outsideState.isSolidRender(level, outsidePos)
                && neighborState.isCollisionShapeFullBlock(level, neighborPos)
                && application.test(neighborState, neighborAppearance);
    }

    @FunctionalInterface
    public interface OverlayApplication {
        boolean test(BlockState neighborState, BlockState neighborAppearance);
    }

    private static boolean nativeCorner(
            boolean application,
            boolean firstReceiver,
            boolean secondReceiver) {
        return !application || !firstReceiver || !secondReceiver;
    }

    private static boolean samePair(
            WorldDirection first,
            WorldDirection second,
            WorldDirection expectedFirst,
            WorldDirection expectedSecond) {
        return first == expectedFirst && second == expectedSecond
                || first == expectedSecond && second == expectedFirst;
    }
}
