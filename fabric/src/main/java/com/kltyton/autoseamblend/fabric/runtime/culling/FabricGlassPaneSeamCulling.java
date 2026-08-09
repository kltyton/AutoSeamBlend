package com.kltyton.autoseamblend.fabric.runtime.culling;

import com.kltyton.autoseamblend.runtime.culling.PaneSeamCullingPolicy;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.PreparedSurfaceMethods;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * 中文：让原版玻璃板端盖 Quad 使用与 Continuity 玻璃板剔除资源包相同的剔除桶。
 *
 * English:
 * Gives vanilla pane cap quads the same cull buckets as Continuity's
 * pane-culling resource pack.
 *
 * <p>中文：1.21.1 Continuity 的 CtmBakedModel 走 Fabric Renderer API 的
 * emitBlockQuads 路径（isVanillaAdapter=false），因此除 getQuads 重分桶外，还必须在
 * QuadTransform 中把未剔除的 UP/DOWN 端盖 Quad 改写为对应方向的 cullFace。
 *
 * <p>English: On 1.21.1 Continuity's CtmBakedModel renders through the Fabric Renderer
 * API emitBlockQuads path (isVanillaAdapter=false), so besides re-bucketing getQuads, the
 * wrapper must also rewrite unculled UP/DOWN cap quads to the matching cull face in a
 * QuadTransform.
 */
public final class FabricGlassPaneSeamCulling {
    private FabricGlassPaneSeamCulling() {}

    public static boolean applies(
            net.minecraft.world.level.block.Block block,
            RuleRuntime.Snapshot ruleSnapshot,
            PreparedSurfaceMethods.Snapshot preparedMethods,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        return PaneSeamCullingPolicy.applies(
                Objects.requireNonNull(block, "block"),
                Objects.requireNonNull(
                        ruleSnapshot,
                        "ruleSnapshot"),
                Objects.requireNonNull(
                        preparedMethods,
                        "preparedMethods"),
                Objects.requireNonNull(
                        surfaces,
                        "surfaces"));
    }

    public static BakedModel wrap(
            BakedModel model) {
        Objects.requireNonNull(model, "model");
        return model instanceof PaneCullingModel
                ? model
                : new PaneCullingModel(model);
    }

    private static final class PaneCullingModel
            extends ForwardingBakedModel {
        private PaneCullingModel(BakedModel delegate) {
            // 中文：统一使用无参构造器并直接赋值受保护的 wrapped 字段，兼容
            // Fabric renderer API 3.4.0/3.4.1 两个构造面。
            // English: Use the no-arg constructor and assign the protected wrapped field
            // directly so both Fabric renderer API 3.4.0 and 3.4.1 compile surfaces work.
            super();
            this.wrapped = Objects.requireNonNull(
                    delegate,
                    "delegate");
        }

        @Override
        public List<BakedQuad> getQuads(
                BlockState state,
                Direction direction,
                RandomSource random) {
            if (direction == Direction.UP
                    || direction == Direction.DOWN) {
                ArrayList<BakedQuad> result =
                        new ArrayList<>(
                                wrapped.getQuads(
                                        state,
                                        direction,
                                        random));
                for (BakedQuad quad
                        : wrapped.getQuads(
                                state,
                                null,
                                random)) {
                    if (quad.getDirection()
                            == direction) {
                        result.add(quad);
                    }
                }
                return List.copyOf(result);
            }
            if (direction == null) {
                ArrayList<BakedQuad> result =
                        new ArrayList<>();
                for (BakedQuad quad
                        : wrapped.getQuads(
                                state,
                                null,
                                random)) {
                    if (quad.getDirection()
                                    != Direction.UP
                            && quad.getDirection()
                                    != Direction.DOWN) {
                        result.add(quad);
                    }
                }
                return List.copyOf(result);
            }
            return wrapped.getQuads(
                    state,
                    direction,
                    random);
        }

        /**
         * 中文：保留 Fabric Renderer API 发射路径；每次发射预解析上下邻居（同 block
         * 才有效），并在 AutoBlend Fusion capture 之前丢弃与邻居重叠的 UP/DOWN 端盖
         * quad：中心 post cap 只要存在同 block 上下邻居就删，arm cap 仅当邻居对应
         * N/E/S/W property=true 时删。保留的端盖继续把 cullFace 从 null 改写为
         * UP/DOWN。变换按发射实例持有邻居状态，天然跨区块线程安全。
         *
         * <p>English: Keeps the Fabric Renderer API emission path; pre-resolves the
         * vertical neighbors per emission (only a same-block neighbor counts) and drops
         * UP/DOWN cap quads that overlap the neighbor before the AutoBlend Fusion
         * capture: the center post cap is removed whenever a same-block vertical
         * neighbor exists, and an arm cap is removed only when the neighbor's matching
         * N/E/S/W property is true. Kept caps still get their cullFace rewritten from
         * null to UP/DOWN. The transform holds its neighbor states per emission, so it
         * is naturally safe across chunk-builder threads.
         */
        @Override
        public void emitBlockQuads(
                BlockAndTintGetter level,
                BlockState state,
                BlockPos pos,
                Supplier<RandomSource> randomSupplier,
                RenderContext context) {
            if (!state.hasProperty(IronBarsBlock.NORTH)
                    || !state.hasProperty(
                            IronBarsBlock.SOUTH)
                    || !state.hasProperty(
                            IronBarsBlock.WEST)
                    || !state.hasProperty(
                            IronBarsBlock.EAST)) {
                // 中文：四个水平连接属性缺一即原样委托，防止部分属性状态在
                // getValue 时崩溃；原版玻璃板/IronBarsBlock 恒四属性齐全。
                // English: Delegate unchanged when any of the four horizontal
                // properties is absent, so partial-property states never reach
                // getValue; vanilla panes/IronBarsBlock always define all four.
                super.emitBlockQuads(
                        level,
                        state,
                        pos,
                        randomSupplier,
                        context);
                return;
            }
            context.pushTransform(
                    new PaneCapCullTransform(
                            sameBlockNeighbor(
                                    level,
                                    state,
                                    pos,
                                    Direction.UP),
                            sameBlockNeighbor(
                                    level,
                                    state,
                                    pos,
                                    Direction.DOWN)));
            try {
                super.emitBlockQuads(
                        level,
                        state,
                        pos,
                        randomSupplier,
                        context);
            } finally {
                context.popTransform();
            }
        }

        /**
         * 中文：返回 pos 上方/下方与当前方块同 block 的邻居可见外观状态；不同 block
         * （含不同颜色染色玻璃板）或空气按 null 处理，仅在邻居方块相同时建立竖直连接。
         *
         * <p>English: Returns the vertical neighbor's visible-appearance state when it
         * is the same block as the current state; air or a different block (including
         * differently colored stained glass panes) resolves to null, so a vertical
         * connection exists only between same-block panes.
         */
        private static BlockState sameBlockNeighbor(
                BlockAndTintGetter level,
                BlockState state,
                BlockPos pos,
                Direction direction) {
            BlockPos neighborPos =
                    pos.relative(direction);
            BlockState neighbor = level
                    .getBlockState(neighborPos)
                    .getAppearance(
                            level,
                            neighborPos,
                            direction.getOpposite(),
                            state,
                            pos);
            return neighbor.getBlock()
                            == state.getBlock()
                    ? neighbor
                    : null;
        }
    }

    /**
     * 中文：按上下邻居丢弃重叠 UP/DOWN 端盖的按发射实例变换。几何判定使用运行时
     * 单位空间（0..1）坐标，按原版玻璃板几何与同轮运行捕获证据独立判定：端盖中心
     * 距方块中心小于阈值视为中心 post cap，否则按主轴线方向归类为对应 arm cap。
     *
     * <p>English: Per-emission transform that drops overlapping UP/DOWN caps based on
     * the vertical neighbors. Geometry is judged from the vanilla glass pane layout in
     * the observed runtime unit space (0..1): a cap whose center sits within the post
     * radius of the block center is the center post cap; otherwise it is the arm cap of
     * its dominant horizontal axis.
     */
    private static final class PaneCapCullTransform
            implements RenderContext.QuadTransform {
        /** 中文：单位空间块中心。 / English: Block center in unit space. */
        private static final float UNIT_CENTER = 0.5F;
        /** 中文：中心 post cap 半径阈值的平方（0.1²），避免 sqrt。 / English: Squared center post cap radius threshold (0.1 squared), avoiding sqrt. */
        private static final float CENTER_RADIUS_SQUARED =
                0.1F * 0.1F;

        private final BlockState stateAbove;
        private final BlockState stateBelow;

        private PaneCapCullTransform(
                BlockState stateAbove,
                BlockState stateBelow) {
            this.stateAbove = stateAbove;
            this.stateBelow = stateBelow;
        }

        @Override
        public boolean transform(
                MutableQuadView quad) {
            Direction face = quad.nominalFace();
            if (face != Direction.UP
                    && face != Direction.DOWN) {
                return true;
            }
            BlockState neighbor =
                    face == Direction.UP
                            ? stateAbove
                            : stateBelow;
            if (neighbor == null) {
                rewriteCullFace(quad, face);
                return true;
            }
            float centerX = (quad.x(0)
                            + quad.x(1)
                            + quad.x(2)
                            + quad.x(3))
                    / 4.0F;
            float centerZ = (quad.z(0)
                            + quad.z(1)
                            + quad.z(2)
                            + quad.z(3))
                    / 4.0F;
            float dx = centerX - UNIT_CENTER;
            float dz = centerZ - UNIT_CENTER;
            boolean keep;
            // 中文：原版玻璃板 post 为中央 7/16..9/16 单元（单位空间 0.4375..0.5625），
            // arm cap 中心距为 4.5/16≈0.28125；平方距离 0.01 阈值与此几何及运行
            // 捕获证据一致。
            // English: The vanilla pane post is the central 7/16..9/16 cell (unit space
            // 0.4375..0.5625) and arm cap centers sit 4.5/16≈0.28125 away; the squared
            // threshold 0.01 matches that geometry and the captured runtime evidence.
            if (dx * dx + dz * dz
                    < CENTER_RADIUS_SQUARED) {
                // 中文：中心 post 恒存在，同 block 上下邻居必然重叠，端盖删除。
                // English: The center post always exists, so a same-block vertical
                // neighbor always overlaps this cap; drop it.
                keep = false;
            } else {
                // 中文：arm cap 落在四条轴线上：主轴线取 |dx|/|dz| 较大者，符号直接
                // 映射 N/E/S/W，不依赖方向枚举顺序或 ordinal 表。
                // English: Arm caps lie on the four axes: the dominant axis is the
                // larger of |dx|/|dz|, and its sign maps straight to N/E/S/W without
                // any Direction enum ordering or ordinal table.
                boolean eastWest =
                        Math.abs(dx) >= Math.abs(dz);
                BooleanProperty side = eastWest
                        ? (dx >= 0.0F
                                ? IronBarsBlock.EAST
                                : IronBarsBlock.WEST)
                        : (dz >= 0.0F
                                ? IronBarsBlock.SOUTH
                                : IronBarsBlock.NORTH);
                // 中文：arm 仅在邻居对应方向连接属性为 true 时重叠；false 或无属性
                // 时不删，保留可见的边缘 cap。
                // English: An arm cap overlaps only when the neighbor's matching
                // direction property is true; false or absent keeps the visible cap.
                keep = !Boolean.TRUE.equals(
                                neighbor.getValue(side));
            }
            if (keep) {
                rewriteCullFace(quad, face);
            }
            return keep;
        }

        /** 中文：保留的端盖维持既有 cullFace=UP/DOWN 改写行为。 / English: Kept caps keep the existing cullFace=UP/DOWN rewrite. */
        private static void rewriteCullFace(
                MutableQuadView quad,
                Direction face) {
            if (quad.cullFace() == null) {
                quad.cullFace(face);
            }
        }
    }
}
