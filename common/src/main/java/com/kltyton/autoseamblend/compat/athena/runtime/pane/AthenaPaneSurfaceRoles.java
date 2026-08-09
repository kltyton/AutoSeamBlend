package com.kltyton.autoseamblend.compat.athena.runtime.pane;

import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.StateSurface;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.world.level.block.Block;
import net.minecraft.resources.ResourceLocation;

/**
 * 中文：双 Loader 共享的 pane 表面角色选择组件。语义：body=水平轴主面（最大面积条带面）；
 * edge=竖直轴优先精灵不同于 body 候选的 cap 面，仅当全部竖直面共享 body 精灵时才稳定
 * 回退最大竖直面；body 再按非 edge 精灵过滤。纯数据驱动，无方块/精灵白名单；典型输入
 * （竖直面全为 cap）下与既有 NeoForge 选择逐值一致。Loader 专属的首次 bake 采集与模型
 * 包装不进入本组件。
 *
 * <p>English: Loader-shared pane surface-role selection component. Semantics: body is the
 * horizontal main face (the largest strip face); edge prefers the vertical cap face whose
 * sprite differs from the body candidate, stably falling back to the largest vertical face
 * only when every vertical face shares the body sprite; body then keeps the non-edge-sprite
 * filter. Purely data-driven with no block/sprite whitelists; typical inputs (vertical faces
 * all caps) stay value-identical to the accepted NeoForge selection. Loader-specific
 * first-bake collection and model wrapping never enter this component.
 */
public final class AthenaPaneSurfaceRoles {
    private AthenaPaneSurfaceRoles() {}

    /** 中文：pane 的 body/edge 角色结果。 / English: The pane body/edge role result. */
    public record Roles(
            FaceSurface body,
            FaceSurface edge) {
        public Roles {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(edge, "edge");
        }
    }

    /**
     * 中文：从表面快照选择 pane body/edge 角色；任何一面缺失时返回 empty。
     *
     * <p>English: Selects the pane body/edge roles from a surface snapshot; returns empty
     * when either axis lacks a candidate.
     */
    public static Optional<Roles> select(
            StateSurface stateSurface) {
        Objects.requireNonNull(
                stateSurface,
                "stateSurface");
        List<FaceSurface> vertical =
                stateSurface.faces()
                        .entrySet()
                        .stream()
                        .filter(entry -> entry.getKey()
                                .getAxis()
                                .isVertical())
                        .flatMap(entry ->
                                entry.getValue()
                                        .stream())
                        .filter(surface ->
                                !surface.fullyTransparent())
                        .toList();
        List<FaceSurface> horizontal =
                stateSurface.faces()
                        .entrySet()
                        .stream()
                        .filter(entry -> entry.getKey()
                                .getAxis()
                                .isHorizontal())
                        .flatMap(entry ->
                                entry.getValue()
                                        .stream())
                        .filter(surface ->
                                !surface.fullyTransparent())
                        .toList();
        if (horizontal.isEmpty()
                || vertical.isEmpty()) {
            return Optional.empty();
        }
        // 中文：body 候选=水平面最大面（条带大面）；edge 优先选竖直面中精灵与 body 候选
        // 不同的 cap 面（首次 bake 的 PaneCullingModel 组合可能在竖直面混入 body 精灵，
        // 按最大面积会把 body 当 edge），无不同精灵时才回退最大面积；随后 body 再按
        // 非 edge 精灵过滤，保持既有 NeoForge 选择语义不变。
        // English: The body candidate is the largest horizontal face (the strip face); edge
        // prefers the vertical face whose sprite differs from the body candidate (the cap --
        // the first-bake PaneCullingModel composition can mix body sprites into vertical
        // faces, and largest-area would wrongly pick the body as edge), falling back to the
        // largest area only when every vertical face shares the body sprite; body then keeps
        // the non-edge-sprite filter, preserving the accepted NeoForge selection semantics.
        FaceSurface bodyCandidate = largest(horizontal);
        ResourceLocation bodyCandidateSprite =
                bodyCandidate.sprite()
                        .contents()
                        .name();
        FaceSurface edge = vertical
                .stream()
                .filter(surface ->
                        !surface.sprite()
                                .contents()
                                .name()
                                .equals(
                                        bodyCandidateSprite))
                .max(comparingArea())
                .orElseGet(() -> largest(vertical));
        ResourceLocation edgeId = edge.sprite()
                .contents()
                .name();
        FaceSurface body = horizontal
                .stream()
                .filter(surface ->
                        !surface.sprite()
                                .contents()
                                .name()
                                .equals(edgeId))
                .max(comparingArea())
                .orElse(bodyCandidate);
        return Optional.of(new Roles(
                body,
                edge));
    }

    /**
     * 中文：当前 state 缺少与 body 不同的 edge/cap 竖直面候选（edge 精灵退化等于 body）
     * 时，按 0d5bce0 已验收的跨状态回退语义，只从同一 Block 的其他连接状态借用稳定 cap
     * FaceSurface；body 始终保持当前 state。无可用 sibling 时明确安全回退（返回当前退化
     * 角色，不抛异常、不空）。纯数据驱动，不依赖方块 ID/精灵名白名单。
     *
     * <p>English: When the current state lacks an edge/cap vertical-face candidate differing
     * from body (edge sprite degenerates to body), borrow a stable cap FaceSurface from
     * another connection state of the same Block per the accepted 0d5bce0 cross-state
     * fallback semantics; body always stays the current state. Without a usable sibling the
     * result safely falls back to the current degenerate roles (never throws, never empty).
     * Purely data-driven with no block-id or sprite whitelists.
     */
    public static Optional<Roles> selectWithSiblingFallback(
            StateSurface current,
            Collection<StateSurface> siblings) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(siblings, "siblings");
        Optional<Roles> direct = select(current);
        if (direct.isEmpty()) {
            return Optional.empty();
        }
        Roles roles = direct.orElseThrow();
        if (!roles.edge().sprite()
                .contents()
                .name()
                .equals(
                        roles.body().sprite()
                                .contents()
                                .name())) {
            return Optional.of(roles);
        }
        Block block = current.state().getBlock();
        for (StateSurface sibling : siblings) {
            if (sibling == current
                    || sibling.state().getBlock() != block
                    || sibling.state() == current.state()) {
                continue;
            }
            Optional<Roles> siblingRoles =
                    select(sibling);
            if (siblingRoles.isEmpty()) {
                continue;
            }
            FaceSurface cap =
                    siblingRoles.orElseThrow().edge();
            if (!cap.sprite()
                    .contents()
                    .name()
                    .equals(
                            roles.body().sprite()
                                    .contents()
                                    .name())) {
                return Optional.of(new Roles(
                        roles.body(),
                        cap));
            }
        }
        return Optional.of(roles);
    }

    private static FaceSurface largest(
            List<FaceSurface> faces) {
        return faces.stream()
                .max(comparingArea())
                .orElseThrow();
    }

    private static Comparator<FaceSurface>
            comparingArea() {
        return Comparator.comparingDouble(
                surface -> area(
                        surface
                                .representativeQuad()));
    }

    private static double area(BakedQuad quad) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < 4; vertex++) {
            float[] position = position(quad, vertex);
            minX = Math.min(minX, position[0]);
            minY = Math.min(minY, position[1]);
            minZ = Math.min(minZ, position[2]);
            maxX = Math.max(maxX, position[0]);
            maxY = Math.max(maxY, position[1]);
            maxZ = Math.max(maxZ, position[2]);
        }
        float x = maxX - minX;
        float y = maxY - minY;
        float z = maxZ - minZ;
        return Math.max(
                x * y,
                Math.max(x * z, y * z));
    }

    private static float[] position(
            BakedQuad quad,
            int vertex) {
        int base = vertex * 8;
        int[] vertices = quad.getVertices();
        return new float[] {
            Float.intBitsToFloat(vertices[base]),
            Float.intBitsToFloat(vertices[base + 1]),
            Float.intBitsToFloat(vertices[base + 2])
        };
    }
}
