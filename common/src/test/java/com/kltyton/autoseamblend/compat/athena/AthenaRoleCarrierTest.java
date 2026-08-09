package com.kltyton.autoseamblend.compat.athena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringProject;
import com.kltyton.autoseamblend.authoring.model.ManagedAuthoringRule;
import com.kltyton.autoseamblend.authoring.template.ManagedAuthoringTemplates;
import com.kltyton.autoseamblend.compat.athena.authoring.AthenaPaneTilePlan;
import com.kltyton.autoseamblend.compat.athena.authoring.AthenaPhysicalTilePlan;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.GeneratedStateRecipe;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe.BlendConnections;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe.BorderConnections;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe.CompactConnections;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 中文：冻结 Athena 4.0.6 五角色/七角色载体的行为测试。
 * English: Behavior tests freezing Athena 4.0.6's five-role and seven-role carriers.
 */
class AthenaRoleCarrierTest {
    private static final int UP = 1 << 6;
    private static final int LEFT = 1 << 0;
    private static final int UP_LEFT = 1 << 7;
    private static final int DOWN = 1 << 2;
    private static final int RIGHT = 1 << 4;
    private static final int UP_RIGHT = 1 << 5;
    private static final int DOWN_LEFT = 1 << 1;
    private static final int DOWN_RIGHT = 1 << 3;

    @Test
    void fiveRolesMatchNativeNamesIndicesAndRepresentativeBits() {
        List<String> keys = List.of(
                "particle",
                "empty",
                "center",
                "vertical",
                "horizontal");
        List<Integer> indices = List.of(0, 1, 2, 3, 4);
        List<Integer> bits = List.of(0x00, 0xFF, 0x55, 0x44, 0x11);

        assertEquals(5, AthenaPhysicalTilePlan.ROLE_COUNT);
        AthenaPhysicalTilePlan.Role[] roles =
                AthenaPhysicalTilePlan.Role.values();
        assertEquals(5, roles.length);
        for (int index = 0; index < roles.length; index++) {
            assertEquals(keys.get(index), roles[index].jsonKey());
            assertEquals(indices.get(index), roles[index].nativeIndex());
            assertEquals(bits.get(index), roles[index].representativeBits());
            assertEquals(roles[index], AthenaPhysicalTilePlan.role(index));
        }
    }

    @Test
    void everyStateMatchesRoleRepresentativeQuadrantTopology() {
        // 中文：锁定 26.1.2 已验收 47-state tile 的象限像素语义。对全部 256 个连接
        // 状态，逐个象限比较“原完整状态”与“该象限所选角色代表图”的局部拓扑：两条
        // 正交边必须一致，对角位仅在两边均连接时有效（与 47-slice 归一化一致）。
        // 旧代表位（CENTER 0x41 / VERTICAL 0x40 / HORIZONTAL 0x01）是 TL 角代表态，
        // 在 bottom/right 象限的两条正交边不等价，正是半截内边框的像素来源。
        //
        // English: Locks the quadrant pixel semantics of the accepted 26.1.2 47-state
        // tile. For all 256 connection states and each quadrant, the local topology of
        // the full original state must equal the same quadrant of the role representative
        // selected for that corner: the two orthogonal edges must match, and the diagonal
        // bit is only valid when both edges connect (47-slice normalization). The old
        // representatives (CENTER 0x41 / VERTICAL 0x40 / HORIZONTAL 0x01) were TL-corner
        // states and break the bottom/right quadrants, which is the pixel source of the
        // half inner borders.
        for (int stateBits = 0; stateBits <= 0xFF; stateBits++) {
            NeighborConnections state =
                    NeighborConnections.fromBits(stateBits);
            for (Quadrant quadrant : Quadrant.values()) {
                boolean first = quadrant.first(state);
                boolean second = quadrant.second(state);
                boolean diagonal =
                        quadrant.diagonal(state)
                                && first
                                && second;
                AthenaPhysicalTilePlan.Role role =
                        roleFor(first, second, diagonal);
                NeighborConnections representative =
                        NeighborConnections.fromBits(
                                role.representativeBits());
                String context = "state 0x"
                        + Integer.toHexString(stateBits)
                        + " quadrant "
                        + quadrant
                        + " role "
                        + role.jsonKey();
                assertEquals(
                        first,
                        quadrant.first(representative),
                        context + " first orthogonal edge");
                assertEquals(
                        second,
                        quadrant.second(representative),
                        context + " second orthogonal edge");
                boolean representativeDiagonal =
                        quadrant.diagonal(representative)
                                && quadrant.first(representative)
                                && quadrant.second(representative);
                assertEquals(
                        diagonal,
                        representativeDiagonal,
                        context + " diagonal");
            }
        }
    }

    @Test
    void paneSevenRolesMatchNativeNamesAndIndices() {
        List<String> keys = List.of(
                "particle",
                "empty",
                "center",
                "vertical",
                "horizontal",
                "edge",
                "side_edge");

        assertEquals(7, AthenaPaneTilePlan.roleCount());
        assertEquals(5, AthenaPaneTilePlan.generatedRoleCount());
        AthenaPaneTilePlan.Role[] roles =
                AthenaPaneTilePlan.Role.values();
        assertEquals(7, roles.length);
        for (int index = 0; index < roles.length; index++) {
            assertEquals(keys.get(index), roles[index].jsonKey());
            assertEquals(index, roles[index].nativeIndex());
            assertEquals(roles[index], AthenaPaneTilePlan.role(index));
        }
    }

    @Test
    void paneRuntimeMethodDegradesInferredNoneToCtmOnlyForAutoConfigured() {
        // 中文：锁定 pane 专用运行时方法退化。通用几何推断可能因全臂 pane 状态误判
        // NONE，而已验收的 pane 适配必须进入原生 CTM：configured=AUTO 且 inferred=NONE
        // 时结果必须为 CTM；AUTO+CTM 保持 CTM；AUTO+其他非 NONE 推断结果保持原样；
        // 显式 NONE 与其他显式方法绝不能被改写。
        //
        // English: Locks the pane-specific runtime method degradation. Generic geometry
        // inference may misjudge NONE because of full-arm pane states, while the accepted
        // pane adapter must enter native CTM: configured=AUTO with inferred=NONE must
        // resolve to CTM; AUTO+CTM stays CTM; AUTO with any other non-NONE inference keeps
        // the inferred method; explicit NONE and every other explicit method are never
        // rewritten.
        assertEquals(
                ConnectionMethod.CTM,
                AthenaPaneTilePlan.resolveRuntimeMethod(
                        ConnectionMethod.AUTO,
                        ConnectionMethod.NONE));
        assertEquals(
                ConnectionMethod.CTM,
                AthenaPaneTilePlan.resolveRuntimeMethod(
                        ConnectionMethod.AUTO,
                        ConnectionMethod.CTM));
        assertEquals(
                ConnectionMethod.OVERLAY,
                AthenaPaneTilePlan.resolveRuntimeMethod(
                        ConnectionMethod.AUTO,
                        ConnectionMethod.OVERLAY));
        assertEquals(
                ConnectionMethod.RUNTIME_BLEND,
                AthenaPaneTilePlan.resolveRuntimeMethod(
                        ConnectionMethod.AUTO,
                        ConnectionMethod.RUNTIME_BLEND));
        assertEquals(
                ConnectionMethod.OVERLAY_CTM,
                AthenaPaneTilePlan.resolveRuntimeMethod(
                        ConnectionMethod.AUTO,
                        ConnectionMethod.OVERLAY_CTM));
        assertEquals(
                ConnectionMethod.NONE,
                AthenaPaneTilePlan.resolveRuntimeMethod(
                        ConnectionMethod.NONE,
                        ConnectionMethod.NONE));
        assertEquals(
                ConnectionMethod.NONE,
                AthenaPaneTilePlan.resolveRuntimeMethod(
                        ConnectionMethod.NONE,
                        ConnectionMethod.CTM));
        assertEquals(
                ConnectionMethod.CTM,
                AthenaPaneTilePlan.resolveRuntimeMethod(
                        ConnectionMethod.CTM,
                        ConnectionMethod.CTM));
        assertEquals(
                ConnectionMethod.OVERLAY,
                AthenaPaneTilePlan.resolveRuntimeMethod(
                        ConnectionMethod.OVERLAY,
                        ConnectionMethod.OVERLAY));
    }

    @Test
    void quadrantTruthTableSelectsNativeRoles() {
        assertEquals(
                AthenaPhysicalTilePlan.Role.EMPTY,
                roleFor(bits(UP | LEFT | UP_LEFT)));
        assertEquals(
                AthenaPhysicalTilePlan.Role.CENTER,
                roleFor(bits(UP | LEFT)));
        assertEquals(
                AthenaPhysicalTilePlan.Role.VERTICAL,
                roleFor(bits(UP)));
        assertEquals(
                AthenaPhysicalTilePlan.Role.HORIZONTAL,
                roleFor(bits(LEFT)));
        assertEquals(
                AthenaPhysicalTilePlan.Role.PARTICLE,
                roleFor(bits(0)));
        assertEquals(
                AthenaPhysicalTilePlan.Role.EMPTY,
                roleFor(bits(0xFF)));
        assertEquals(
                AthenaPhysicalTilePlan.Role.PARTICLE,
                roleFor(bits(1 << 2)));
    }

    @Test
    void roleRecipesAcrossPublicMethods() {
        List<ConnectionMethod> connecting = List.of(
                ConnectionMethod.CTM,
                ConnectionMethod.CTM_COMPACT,
                ConnectionMethod.HORIZONTAL,
                ConnectionMethod.VERTICAL,
                ConnectionMethod.HORIZONTAL_VERTICAL,
                ConnectionMethod.VERTICAL_HORIZONTAL,
                ConnectionMethod.RUNTIME_BLEND,
                ConnectionMethod.OVERLAY,
                ConnectionMethod.OVERLAY_CTM);
        for (ConnectionMethod method : connecting) {
            assertEquals(
                    5,
                    AthenaPhysicalTilePlan
                            .forMethod(method)
                            .recipes()
                            .size());
        }
        for (ConnectionMethod method : List.of(
                ConnectionMethod.FIXED,
                ConnectionMethod.TOP,
                ConnectionMethod.NONE)) {
            for (AthenaPhysicalTilePlan.Role role
                    : AthenaPhysicalTilePlan.Role.values()) {
                assertEquals(
                        GeneratedTileRecipe.Source.INSTANCE,
                        AthenaPhysicalTilePlan.recipe(method, role));
            }
        }
        for (AthenaPhysicalTilePlan.Role role
                : AthenaPhysicalTilePlan.Role.values()) {
            assertTrue(
                    AthenaPhysicalTilePlan.recipe(
                                    ConnectionMethod.CTM,
                                    role)
                            instanceof BorderConnections);
            assertEquals(
                    NeighborConnections.fromBits(
                            role.representativeBits()),
                    ((BorderConnections) AthenaPhysicalTilePlan.recipe(
                                    ConnectionMethod.CTM,
                                    role))
                            .connections());
            assertTrue(
                    AthenaPhysicalTilePlan.recipe(
                                    ConnectionMethod.CTM_COMPACT,
                                    role)
                            instanceof CompactConnections);
            assertTrue(
                    AthenaPhysicalTilePlan.recipe(
                                    ConnectionMethod.RUNTIME_BLEND,
                                    role)
                            instanceof BlendConnections);
            assertTrue(
                    AthenaPhysicalTilePlan.recipe(
                                    ConnectionMethod.OVERLAY,
                                    role)
                            instanceof BlendConnections);
            assertTrue(
                    AthenaPhysicalTilePlan.recipe(
                                    ConnectionMethod.OVERLAY_CTM,
                                    role)
                            instanceof BlendConnections);
            assertTrue(
                    AthenaPhysicalTilePlan.recipe(
                                    ConnectionMethod.HORIZONTAL,
                                    role)
                            instanceof BorderConnections);
            assertTrue(
                    AthenaPhysicalTilePlan.recipe(
                                    ConnectionMethod.VERTICAL,
                                    role)
                            instanceof BorderConnections);
        }
        assertEquals(
                7,
                AthenaPaneTilePlan.forMethod(ConnectionMethod.CTM)
                        .size());
        List<AthenaPaneTilePlan.PaneTile> paneTiles =
                AthenaPaneTilePlan.forMethod(ConnectionMethod.CTM);
        for (int index = 0; index < 5; index++) {
            assertTrue(
                    paneTiles.get(index).recipe()
                            instanceof BorderConnections);
        }
        for (int index = 5; index < 7; index++) {
            assertEquals(
                    GeneratedTileRecipe.Source.INSTANCE,
                    paneTiles.get(index).recipe());
        }
    }

    @Test
    void blendAndOverlayRecipesInvertRepresentativeBitsLikeAccepted2612() {
        // 中文：移植 26.1.2 已验收的 recipeConnections 语义：overlay 运行时原生状态
        // 已是 !applications（AthenaStateProjection.nativeCarrierState），配方必须再取反
        // （representativeBits ^ 0xFF）才能恢复 application 拓扑。对全部五个角色与
        // RUNTIME_BLEND/OVERLAY/OVERLAY_CTM，recipe 与 forNativeCarrier 计划级配方都
        // 必须是取反后的 BlendConnections。
        //
        // English: Ports the accepted 26.1.2 recipeConnections semantics: the overlay
        // runtime native state is already !applications
        // (AthenaStateProjection.nativeCarrierState), so the recipe must invert again
        // (representativeBits ^ 0xFF) to restore the application topology. For all five
        // roles and RUNTIME_BLEND/OVERLAY/OVERLAY_CTM, both the recipe and the
        // forNativeCarrier plan-level recipe must be the inverted BlendConnections.
        for (AthenaPhysicalTilePlan.Role role
                : AthenaPhysicalTilePlan.Role.values()) {
            for (ConnectionMethod method : List.of(
                    ConnectionMethod.RUNTIME_BLEND,
                    ConnectionMethod.OVERLAY,
                    ConnectionMethod.OVERLAY_CTM)) {
                GeneratedTileRecipe recipe =
                        AthenaPhysicalTilePlan.recipe(
                                method,
                                role);
                assertTrue(
                        recipe instanceof BlendConnections,
                        method + " recipe for " + role.jsonKey()
                                + " must be a BlendConnections");
                assertEquals(
                        role.representativeBits() ^ 0xFF,
                        ((BlendConnections) recipe)
                                .connections()
                                .bits(),
                        method + " recipe for " + role.jsonKey()
                                + " must invert representative bits");
                GeneratedTileRecipe planRecipe =
                        AthenaPhysicalTilePlan.forNativeCarrier(
                                        method)
                                .recipes()
                                .get(role.nativeIndex());
                assertTrue(
                        planRecipe instanceof BlendConnections,
                        method + " native carrier recipe for "
                                + role.jsonKey()
                                + " must be a BlendConnections");
                assertEquals(
                        role.representativeBits() ^ 0xFF,
                        ((BlendConnections) planRecipe)
                                .connections()
                                .bits(),
                        method + " native carrier recipe for "
                                + role.jsonKey()
                                + " must invert representative bits");
            }
        }
    }

    @Test
    void borderAndAxisRecipesKeepRepresentativeBitsWithoutInversion() {
        // 中文：CTM/CTM_COMPACT/轴向方法不取反（与 26.1.2 default 分支一致）；防止
        // overlay 取反被过度扩散到 CTM 玻璃等替换路径。轴向方法的配方仍按
        // GeneratedStateRecipe.connectionsForMethod 做轴向投影（与 26.1.2 相同），
        // 但绝不做 0xFF 取反。
        //
        // English: CTM/CTM_COMPACT and the axis methods must NOT invert representative
        // bits (matching 26.1.2's default branch), guarding against the overlay inversion
        // leaking into CTM glass and other replacement paths. Axis-method recipes still
        // apply GeneratedStateRecipe.connectionsForMethod's axis projection (same as
        // 26.1.2) but never the 0xFF inversion.
        for (AthenaPhysicalTilePlan.Role role
                : AthenaPhysicalTilePlan.Role.values()) {
            for (ConnectionMethod method : List.of(
                    ConnectionMethod.CTM,
                    ConnectionMethod.CTM_COMPACT,
                    ConnectionMethod.HORIZONTAL,
                    ConnectionMethod.VERTICAL,
                    ConnectionMethod.HORIZONTAL_VERTICAL,
                    ConnectionMethod.VERTICAL_HORIZONTAL)) {
                GeneratedTileRecipe recipe =
                        AthenaPhysicalTilePlan.recipe(
                                method,
                                role);
                assertTrue(
                        recipe instanceof BorderConnections
                                || recipe instanceof CompactConnections,
                        method + " recipe for " + role.jsonKey()
                                + " must be a connection recipe");
                assertEquals(
                        expectedBits(
                                method,
                                role.representativeBits()),
                        connections(recipe).bits(),
                        method + " recipe for " + role.jsonKey()
                                + " must keep representative bits"
                                + " (no 0xFF inversion)");
            }
        }
    }

    @Test
    void noPhysical47SentinelReachesConsumers() {
        assertEquals(5, AthenaPhysicalTilePlan.ROLE_COUNT);
        assertEquals(
                5,
                AthenaPhysicalTilePlan.forMethod(ConnectionMethod.CTM)
                        .recipes()
                        .size());
        for (int index = 0; index < 5; index++) {
            assertEquals(
                    AthenaPhysicalTilePlan.Role.values()[index],
                    AthenaPhysicalTilePlan.role(index));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> AthenaPhysicalTilePlan.role(5));
    }

    @Test
    void authoringTemplateWritesRoleObjectWithSameBlockConnect() {
        // 中文：authoring 文档必须保留 26.1.2 已验收的 connect_to 同块连接合同
        // （Athena 4.0.6 CtmUtils 仍解析该键）；五角色对象是载体迁移，不改变连接合同。
        // English: The authoring document must keep the 26.1.2-accepted connect_to
        // same-block contract (Athena 4.0.6's CtmUtils still parses the key); the
        // five-role object is a carrier migration, not a connection-contract change.
        ManagedAuthoringRule rule = new ManagedAuthoringRule(
                "minecraft:glass",
                "minecraft:block/glass",
                "minecraft:block/glass",
                ConnectionMethod.CTM,
                ConnectionMethod.CTM,
                true,
                false,
                List.of("all"));
        ManagedAuthoringProject project =
                ManagedAuthoringTemplates.create(
                        EngineFamily.ATHENA,
                        List.of(rule));
        String document = new String(
                project.documents()
                        .getFirst()
                        .content(),
                StandardCharsets.UTF_8);
        assertTrue(document.contains(
                "\"athena:loader\": \"athena:ctm\""));
        assertTrue(document.contains("\"connect_to\""));
        assertTrue(document.contains("\"sameBlock\""));
        assertTrue(document.contains("\"method\""));
        assertTrue(document.contains("\"compatibility\""));
        assertTrue(document.contains("\"ctm_textures\""));
        for (AthenaPhysicalTilePlan.Role role
                : AthenaPhysicalTilePlan.Role.values()) {
            assertTrue(document.contains(
                    "\"" + role.jsonKey() + "\": "
                            + "\"minecraft:block/glass\""));
        }

        ManagedAuthoringRule paneRule = new ManagedAuthoringRule(
                "minecraft:glass_pane",
                "minecraft:block/glass_pane",
                "minecraft:block/glass_pane",
                ConnectionMethod.CTM,
                ConnectionMethod.CTM,
                true,
                true,
                List.of("all"));
        ManagedAuthoringProject paneProject =
                ManagedAuthoringTemplates.create(
                        EngineFamily.ATHENA,
                        List.of(paneRule));
        String paneDocument = new String(
                paneProject.documents()
                        .getFirst()
                        .content(),
                StandardCharsets.UTF_8);
        assertTrue(paneDocument.contains(
                "\"athena:loader\": \"athena:pane_ctm\""));
        assertTrue(paneDocument.contains("\"connect_to\""));
        assertTrue(paneDocument.contains("\"sameBlock\""));
        assertTrue(paneDocument.contains(
                "\"edge\": \"minecraft:block/glass_pane\""));
        assertTrue(paneDocument.contains(
                "\"side_edge\": \"minecraft:block/glass_pane\""));
    }

    private static NeighborConnections bits(int bits) {
        return NeighborConnections.fromBits(bits);
    }

    private static AthenaPhysicalTilePlan.Role roleFor(
            NeighborConnections connections) {
        return AthenaPhysicalTilePlan.roleFor(connections);
    }

    private static NeighborConnections connections(
            GeneratedTileRecipe recipe) {
        if (recipe instanceof BorderConnections border) {
            return border.connections();
        }
        if (recipe instanceof CompactConnections compact) {
            return compact.connections();
        }
        throw new IllegalArgumentException(
                "expected a connections recipe: " + recipe);
    }

    private static int expectedBits(
            ConnectionMethod method,
            int representativeBits) {
        return switch (method) {
            case CTM, CTM_COMPACT -> representativeBits;
            case HORIZONTAL, VERTICAL,
                    HORIZONTAL_VERTICAL,
                    VERTICAL_HORIZONTAL ->
                    GeneratedStateRecipe.connectionsForMethod(
                                    method,
                                    NeighborConnections.fromBits(
                                            representativeBits))
                            .bits();
            default -> throw new IllegalArgumentException(
                    "no non-inverted recipe for " + method);
        };
    }

    /**
     * 中文：按 Athena 4.0.6 CtmUtils.getTexture 真值表由单个象限的三位角状态选择
     * 角色：两边均连 + 对角连 -> empty；两边均连 + 对角断 -> center；仅第一边连 ->
     * vertical；仅第二边连 -> horizontal；均不连 -> particle。
     *
     * <p>English: Selects the role from one quadrant's three-bit corner state with
     * Athena 4.0.6's CtmUtils.getTexture truth table: both edges + diagonal ->
     * empty; both edges, diagonal open -> center; first edge only -> vertical;
     * second edge only -> horizontal; none -> particle.
     */
    private static AthenaPhysicalTilePlan.Role roleFor(
            boolean first,
            boolean second,
            boolean diagonal) {
        if (first && second) {
            return diagonal
                    ? AthenaPhysicalTilePlan.Role.EMPTY
                    : AthenaPhysicalTilePlan.Role.CENTER;
        }
        if (first) {
            return AthenaPhysicalTilePlan.Role.VERTICAL;
        }
        if (second) {
            return AthenaPhysicalTilePlan.Role.HORIZONTAL;
        }
        return AthenaPhysicalTilePlan.Role.PARTICLE;
    }

    /**
     * 中文：面纹理空间四个象限；first/second 为两条正交边，diagonal 为对应角位。
     *
     * <p>English: The four quadrants of face texture space; first/second are the two
     * orthogonal edges and diagonal is the matching corner bit.
     */
    private enum Quadrant {
        TOP_LEFT(
                "top-left",
                UP,
                LEFT,
                UP_LEFT),
        TOP_RIGHT(
                "top-right",
                UP,
                RIGHT,
                UP_RIGHT),
        BOTTOM_LEFT(
                "bottom-left",
                DOWN,
                LEFT,
                DOWN_LEFT),
        BOTTOM_RIGHT(
                "bottom-right",
                DOWN,
                RIGHT,
                DOWN_RIGHT);

        private final String label;
        private final int firstBit;
        private final int secondBit;
        private final int diagonalBit;

        Quadrant(
                String label,
                int firstBit,
                int secondBit,
                int diagonalBit) {
            this.label = label;
            this.firstBit = firstBit;
            this.secondBit = secondBit;
            this.diagonalBit = diagonalBit;
        }

        private boolean first(NeighborConnections connections) {
            return (connections.bits() & firstBit) != 0;
        }

        private boolean second(NeighborConnections connections) {
            return (connections.bits() & secondBit) != 0;
        }

        private boolean diagonal(NeighborConnections connections) {
            return (connections.bits() & diagonalBit) != 0;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
