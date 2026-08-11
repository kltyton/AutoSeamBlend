package com.kltyton.autoseamblend.compat.athena.authoring;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.GeneratedStateRecipe;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import java.util.List;
import java.util.Objects;

/**
 * 中文：冻结 Athena pane_ctm 的七个原生材质角色及其像素配方。
 * English: Freezes Athena pane_ctm's seven native material roles and their pixel recipes.
 *
 * <p>Geometry, cull faces, and the Loader's model wrapper are deliberately absent. Both
 * Loaders consume this same role/recipe plan and keep their native pane model lifecycle.</p>
 */
public final class AthenaPaneTilePlan {
    private AthenaPaneTilePlan() {}

    /** 中文：全部原生 pane 角色数量。 / English: Total native pane role count. */
    public static int roleCount() {
        return Role.values().length;
    }

    /** 中文：需要自动生成材质的连续角色数量。 / English: Count of contiguous roles with generated materials. */
    public static int generatedRoleCount() {
        return Role.EDGE.nativeIndex();
    }

    /**
     * 中文：method 必须已经解析；NONE/TOP/FIXED 只保留原图，其余方法使用验收过的七角色状态。
     * English: The method must already be resolved; NONE/TOP/FIXED preserve the source while all
     * other methods use the accepted seven-role state recipes.
     */
    public static List<PaneTile> forMethod(ConnectionMethod resolvedMethod) {
        Objects.requireNonNull(resolvedMethod, "resolvedMethod");
        if (resolvedMethod == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException("auto must be resolved before pane planning");
        }
        return List.of(
                tile(Role.PARTICLE, resolvedMethod, 0),
                tile(Role.EMPTY, resolvedMethod, 0xFF),
                tile(Role.CENTER, resolvedMethod, 0x55),
                tile(Role.VERTICAL, resolvedMethod, 0x44),
                tile(Role.HORIZONTAL, resolvedMethod, 0x11),
                source(Role.EDGE),
                source(Role.SIDE_EDGE));
    }

    /**
     * 中文：解析 pane 运行时实际方法。通用几何推断可能因全臂 pane 状态把 AUTO 误判为
     * NONE，而已验收的 pane 适配必须进入原生 CTM；因此仅 configured=AUTO 且
     * inferred=NONE 时降级为 CTM，AUTO 与其他非 NONE 推断保持推断结果，显式方法
     * （含 NONE）绝不被改写。
     *
     * <p>English: Resolves the pane runtime method. Generic geometry inference may
     * misjudge AUTO as NONE because of full-arm pane states, while the accepted pane
     * adapter must enter native CTM; therefore only configured=AUTO with inferred=NONE
     * degrades to CTM, AUTO with any other non-NONE inference keeps the inferred method,
     * and explicit methods (including NONE) are never rewritten.
     */
    public static ConnectionMethod resolveRuntimeMethod(
            ConnectionMethod configured,
            ConnectionMethod inferred) {
        Objects.requireNonNull(configured, "configured");
        Objects.requireNonNull(inferred, "inferred");
        if (configured != ConnectionMethod.AUTO) {
            return configured;
        }
        return inferred == ConnectionMethod.NONE
                ? ConnectionMethod.CTM
                : inferred;
    }

    public static Role role(int nativeIndex) {
        for (Role role : Role.values()) {
            if (role.nativeIndex == nativeIndex) {
                return role;
            }
        }
        throw new IllegalArgumentException("Athena pane role index must be in [0,6]: " + nativeIndex);
    }

    /**
     * 中文：返回需要生成连接材质的四个 pane 角色位掩码。
     * English: Returns the connection bit mask for the four generated pane roles.
     */
    public static int generatedConnectionBits(Role role) {
        return switch (Objects.requireNonNull(role, "role")) {
            case EMPTY -> 0xFF;
            case CENTER -> 0x55;
            case VERTICAL -> 0x44;
            case HORIZONTAL -> 0x11;
            case PARTICLE, EDGE, SIDE_EDGE ->
                    throw new IllegalArgumentException(
                            "Athena pane role does not have generated connection bits: " + role);
        };
    }

    private static PaneTile tile(
            Role role,
            ConnectionMethod method,
            int connectionBits) {
        return method == ConnectionMethod.NONE
                        || method == ConnectionMethod.TOP
                        || method == ConnectionMethod.FIXED
                ? source(role)
                : new PaneTile(
                        role,
                        GeneratedStateRecipe.forConnections(
                                method,
                                NeighborConnections.fromBits(connectionBits)));
    }

    private static PaneTile source(Role role) {
        return new PaneTile(role, GeneratedTileRecipe.Source.INSTANCE);
    }

    public enum Role {
        PARTICLE("particle", 0),
        EMPTY("empty", 1),
        CENTER("center", 2),
        VERTICAL("vertical", 3),
        HORIZONTAL("horizontal", 4),
        EDGE("edge", 5),
        SIDE_EDGE("side_edge", 6);

        private final String jsonKey;
        private final int nativeIndex;

        Role(String jsonKey, int nativeIndex) {
            this.jsonKey = jsonKey;
            this.nativeIndex = nativeIndex;
        }

        public String jsonKey() {
            return jsonKey;
        }

        public int nativeIndex() {
            return nativeIndex;
        }
    }

    public record PaneTile(Role role, GeneratedTileRecipe recipe) {
        public PaneTile {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(recipe, "recipe");
        }
    }
}
