package com.kltyton.autoseamblend.compat.athena.authoring;

import com.kltyton.autoseamblend.compat.athena.runtime.AthenaConnectionState;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.GeneratedStateRecipe;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 中文：把 Athena 4.0.6 原生五角色象限载体冻结为共同计划。
 * English: Freezes Athena 4.0.6's native five-role quadrant carrier into one shared plan.
 *
 * <p>The common plan owns the locked Athena provider and native {@code CtmState} bridge. Loader
 * callers therefore consume one deterministic five-role plan without parallel resolver facades.
 * The project's logical method domains (47/17/etc.) stay untouched; only the physical carrier is
 * the five native roles.</p>
 */
public record AthenaPhysicalTilePlan(
        ConnectionMethod method,
        List<GeneratedTileRecipe> recipes) {

    public static final int ROLE_COUNT = Role.values().length;

    public AthenaPhysicalTilePlan {
        Objects.requireNonNull(method, "method");
        recipes = List.copyOf(Objects.requireNonNull(recipes, "recipes"));
        if (method == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException("auto must be resolved before Athena planning");
        }
        if (recipes.size() != ROLE_COUNT) {
            throw new IllegalArgumentException("recipe count differs from Athena five-role carrier");
        }
    }

    /**
     * 中文：项目逻辑域计划；每个公开方法都映射到 Athena 原生五角色载体。
     * English: Project logical-domain plan; every public method maps onto Athena's native five-role carrier.
     */
    public static AthenaPhysicalTilePlan forMethod(ConnectionMethod method) {
        Objects.requireNonNull(method, "method");
        if (method == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException("auto must be resolved before Athena planning");
        }
        return fiveRoles(method);
    }

    /**
     * 中文：NeoForge 已接受原生导出载体；NONE 无原生载体，其余方法按五角色输出。
     * English: User-accepted native export carrier; NONE has no native tile plan while every
     * other method outputs the five native roles.
     */
    public static AthenaPhysicalTilePlan forNativeCarrier(ConnectionMethod method) {
        Objects.requireNonNull(method, "method");
        if (method == ConnectionMethod.AUTO || method == ConnectionMethod.NONE) {
            throw new IllegalArgumentException(method + " has no Athena native tile plan");
        }
        return fiveRoles(method);
    }

    /** 中文：冻结一个角色的像素配方；NONE/TOP/FIXED 保留原图。 / English: Freezes one role's pixel recipe; NONE/TOP/FIXED keep the original image. */
    public static GeneratedTileRecipe recipe(
            ConnectionMethod method,
            Role role) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(role, "role");
        if (method == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException("auto must be resolved before Athena planning");
        }
        return switch (method) {
            case NONE, TOP, FIXED -> GeneratedTileRecipe.Source.INSTANCE;
            default -> GeneratedStateRecipe.forConnections(
                    method,
                    recipeConnections(
                            method,
                            NeighborConnections.fromBits(
                                    role.representativeBits())));
        };
    }

    /**
     * 中文：移植 26.1.2 已验收的 recipeConnections：overlay 运行时采样出的原生状态
     * 已经是 applications 的取反（AthenaStateProjection.nativeCarrierState 输出
     * !applications），因此 RUNTIME_BLEND/OVERLAY/OVERLAY_CTM 的配方必须再取反一次
     * （bits ^ 0xFF）才能恢复 application 拓扑；CTM/CTM_COMPACT 与轴向方法保持原样，
     * 不受影响。
     *
     * <p>English: Ports the accepted 26.1.2 recipeConnections. The overlay runtime
     * samples a native state that is already the complement of the applications
     * (AthenaStateProjection.nativeCarrierState emits !applications), so recipes for
     * RUNTIME_BLEND/OVERLAY/OVERLAY_CTM must invert once more (bits ^ 0xFF) to restore
     * the application topology; CTM/CTM_COMPACT and the axis methods stay unchanged.
     */
    private static NeighborConnections recipeConnections(
            ConnectionMethod method,
            NeighborConnections connections) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(connections, "connections");
        return switch (method) {
            case RUNTIME_BLEND, OVERLAY, OVERLAY_CTM ->
                    NeighborConnections.fromBits(
                            connections.bits() ^ 0xFF);
            default -> connections;
        };
    }

    /**
     * 中文：按 Athena 4.0.6 原生 TL 象限真值表选择代表角色。
     * English: Selects the representative role with Athena 4.0.6's native TL-quadrant truth table.
     */
    public static Role roleFor(AthenaConnectionState state) {
        Objects.requireNonNull(state, "state");
        if (state.up() && state.left()) {
            return state.upLeft() ? Role.EMPTY : Role.CENTER;
        }
        if (state.up()) {
            return Role.VERTICAL;
        }
        if (state.left()) {
            return Role.HORIZONTAL;
        }
        return Role.PARTICLE;
    }

    /** 中文：把项目邻接位映射到代表角色。 / English: Maps project connection bits to the representative role. */
    public static Role roleFor(NeighborConnections connections) {
        Objects.requireNonNull(connections, "connections");
        return roleFor(AthenaConnectionState.fromConnections(connections));
    }

    /** 中文：按 Athena 原生角色索引解析角色。 / English: Resolves a role by Athena's native index. */
    public static Role role(int nativeIndex) {
        for (Role role : Role.values()) {
            if (role.nativeIndex() == nativeIndex) {
                return role;
            }
        }
        throw new IllegalArgumentException("Athena role index must be in [0,4]: " + nativeIndex);
    }

    private static AthenaPhysicalTilePlan fiveRoles(ConnectionMethod method) {
        return new AthenaPhysicalTilePlan(
                method,
                Arrays.stream(Role.values())
                        .map(role -> recipe(method, role))
                        .toList());
    }

    /**
     * 中文：Athena 4.0.6 ConnectedTextureMap 的五个原生材质角色。代表位是对称整面
     * 角色图（particle=四边框，empty=四开，center=四开+四内角，vertical=仅左右框，
     * horizontal=仅上下框）；Athena 4.0.6 把一个面拆成四个象限并采样角色图的每个
     * 象限，因此角色图不能是 TL 角代表态——旧值 0x41/0x40/0x01 会在 bottom/right
     * 象限绘出假边框，导致半截内边框。
     *
     * <p>English: The five native material roles of Athena 4.0.6's ConnectedTextureMap.
     * The representative bits are symmetric full-face role images (particle=all borders,
     * empty=all open, center=all open with inside corners, vertical=left/right borders
     * only, horizontal=top/bottom borders only). Athena 4.0.6 splits a face into four
     * quadrants and samples every quadrant of a role image, so a role image cannot be a
     * TL-corner representative state; the old values 0x41/0x40/0x01 painted false
     * borders on the bottom/right quadrants, producing the half inner borders.
     */
    public enum Role {
        PARTICLE("particle", 0, 0x00),
        EMPTY("empty", 1, 0xFF),
        CENTER("center", 2, 0x55),
        VERTICAL("vertical", 3, 0x44),
        HORIZONTAL("horizontal", 4, 0x11);

        private final String jsonKey;
        private final int nativeIndex;
        private final int representativeBits;

        Role(String jsonKey, int nativeIndex, int representativeBits) {
            this.jsonKey = jsonKey;
            this.nativeIndex = nativeIndex;
            this.representativeBits = representativeBits;
        }

        public String jsonKey() {
            return jsonKey;
        }

        public int nativeIndex() {
            return nativeIndex;
        }

        public int representativeBits() {
            return representativeBits;
        }
    }
}
