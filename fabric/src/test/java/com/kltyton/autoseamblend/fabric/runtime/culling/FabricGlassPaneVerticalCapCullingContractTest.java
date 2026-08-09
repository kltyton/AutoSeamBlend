package com.kltyton.autoseamblend.fabric.runtime.culling;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 契约——竖直堆叠的同 block 玻璃板/IronBarsBlock，内部层边界的重叠
 * UP/DOWN cap 必须在 AutoBlend Fusion capture 之前被丢弃：中心 post cap 只要有同
 * block 上下邻居就删；arm cap 仅当邻居对应 N/E/S/W property=true 时删。无同 block
 * 邻居、不同 block/不同颜色邻居、非 UP/DOWN quad 全部保留；保留的 cap 维持
 * cullFace=UP/DOWN 改写。几何判定使用运行时捕获证据的单位空间（0..1）坐标，语义
 * 按原版玻璃板单位空间几何与运行证据判定。
 *
 * <p>English: RED contract -- for vertically stacked same-block glass panes /
 * IronBarsBlock, overlapping UP/DOWN caps on internal layer seams must be dropped
 * before the AutoBlend Fusion capture: the center post cap is removed whenever a
 * same-block vertical neighbor exists, and an arm cap is removed only when the
 * neighbor's matching N/E/S/W property is true. No same-block neighbor, a different
 * block/color neighbor, and non-UP/DOWN quads are all kept; kept caps keep the
 * cullFace=UP/DOWN rewrite. Geometry uses the unit-space (0..1) coordinates observed
 * at runtime, judged from the vanilla glass pane layout.
 */
class FabricGlassPaneVerticalCapCullingContractTest {
    private static final float POST_MIN = 0.4375F;
    private static final float POST_MAX = 0.5625F;

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void noSameBlockVerticalNeighborKeepsEveryCap() {
        List<QuadRecord> emitted =
                emit(fullCross(), air(), air());

        // 中文：无同 block 上下邻居时 5 UP + 5 DOWN cap 全部保留，竖直大面也保留。
        // English: With no same-block vertical neighbor, all 5 UP + 5 DOWN caps stay
        // and the vertical body quad stays.
        assertEquals(11, emitted.size());
        assertEquals(5, countLabel(emitted, "UP:"));
        assertEquals(5, countLabel(emitted, "DOWN:"));
        assertEquals(1, countLabel(emitted, "VERT:NORTH"));

        // 中文：保留的 cap 必须被改写为 cullFace=UP/DOWN（既有行为不变）。
        // English: Kept caps must be rewritten to cullFace=UP/DOWN (existing behavior).
        assertEquals(
                10,
                emitted.stream()
                        .filter(r -> r.label.startsWith("UP:")
                                || r.label.startsWith("DOWN:"))
                        .filter(r -> r.cullFace == r.nominalFace)
                        .count(),
                "every kept cap must carry cullFace matching its nominal UP/DOWN face");
    }

    @Test
    void fullCrossStackedCullsCenterAndAllArmsAtInternalSeams() {
        // 中文：中层上下都是同色 full-cross pane：5 UP + 5 DOWN cap 全部删除，竖直大面保留。
        // English: Middle layer with full-cross same-block panes above and below: all
        // 5 UP + 5 DOWN caps are dropped; the vertical body quad stays.
        List<QuadRecord> middle =
                emit(fullCross(), fullCross(), fullCross());
        assertEquals(0, countLabel(middle, "UP:"));
        assertEquals(0, countLabel(middle, "DOWN:"));
        assertEquals(1, countLabel(middle, "VERT:NORTH"));

        // 中文：底层上方同色 full-cross、下方空气：UP 内部 cap 全删，DOWN 外表面全保留。
        // English: Bottom layer with a full-cross pane above and air below: internal UP
        // caps are all dropped, outer DOWN caps all stay.
        List<QuadRecord> bottom =
                emit(fullCross(), fullCross(), air());
        assertEquals(0, countLabel(bottom, "UP:"));
        assertEquals(5, countLabel(bottom, "DOWN:"));
    }

    @Test
    void partialNeighborCullsOnlyCenterAndMatchingArms() {
        // 中文：上方邻居 north/east=true，下方邻居 north=true：只删中心和对应 arm，
        // 其余 arm 保留（分别验证 UP 与 DOWN 两个方向）。
        // English: Above neighbor north/east=true, below neighbor north=true: only the
        // center and the matching arms are dropped; the other arms stay (both UP and
        // DOWN directions verified separately).
        BlockState abovePartial =
                pane(true, true, false, false);
        BlockState belowPartial =
                pane(true, false, false, false);
        List<QuadRecord> emitted =
                emit(fullCross(), abovePartial, belowPartial);

        assertEquals(0, countLabel(emitted, "UP:POST"));
        assertEquals(0, countLabel(emitted, "UP:NORTH"));
        assertEquals(0, countLabel(emitted, "UP:EAST"));
        assertEquals(1, countLabel(emitted, "UP:SOUTH"));
        assertEquals(1, countLabel(emitted, "UP:WEST"));

        assertEquals(0, countLabel(emitted, "DOWN:POST"));
        assertEquals(0, countLabel(emitted, "DOWN:NORTH"));
        assertEquals(1, countLabel(emitted, "DOWN:EAST"));
        assertEquals(1, countLabel(emitted, "DOWN:SOUTH"));
        assertEquals(1, countLabel(emitted, "DOWN:WEST"));
    }

    @Test
    void nonUpDownQuadAlwaysSurvivesEvenWithFullCrossNeighbors() {
        List<QuadRecord> emitted =
                emit(fullCross(), fullCross(), fullCross());

        assertEquals(
                1,
                countLabel(emitted, "VERT:NORTH"),
                "non-UP/DOWN quads must never be dropped by cap culling");
    }

    @Test
    void differentBlockOrColorNeighborKeepsEveryCap() {
        // 中文：普通玻璃板与绿色染色玻璃板是不同 Block，竖直方向不视为连接，cap 全保留。
        // English: Plain glass pane and green stained glass pane are different Blocks,
        // so no vertical connection exists and every cap stays.
        BlockState plainCross =
                Blocks.GLASS_PANE.defaultBlockState()
                        .setValue(IronBarsBlock.NORTH, true)
                        .setValue(IronBarsBlock.EAST, true)
                        .setValue(IronBarsBlock.SOUTH, true)
                        .setValue(IronBarsBlock.WEST, true);

        List<QuadRecord> emitted =
                emit(fullCross(), plainCross, air());

        assertEquals(5, countLabel(emitted, "UP:"));
        assertEquals(5, countLabel(emitted, "DOWN:"));
    }

    private static BlockState fullCross() {
        return pane(true, true, true, true);
    }

    private static BlockState air() {
        return Blocks.AIR.defaultBlockState();
    }

    private static BlockState pane(
            boolean north,
            boolean east,
            boolean south,
            boolean west) {
        return Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState()
                .setValue(IronBarsBlock.NORTH, north)
                .setValue(IronBarsBlock.EAST, east)
                .setValue(IronBarsBlock.SOUTH, south)
                .setValue(IronBarsBlock.WEST, west);
    }

    private static int countLabel(
            List<QuadRecord> records,
            String prefix) {
        return (int) records.stream()
                .filter(r -> r.label.startsWith(prefix))
                .count();
    }

    private static List<QuadRecord> emit(
            BlockState state,
            BlockState above,
            BlockState below) {
        RecordingContext context =
                new RecordingContext();
        BakedModel model = FabricGlassPaneSeamCulling.wrap(
                new PaneDelegate());
        model.emitBlockQuads(
                new StubLevel(above, below),
                state,
                BlockPos.ZERO,
                () -> RandomSource.create(0L),
                context);
        return context.emitter.records;
    }

    private record QuadRecord(
            String label,
            Direction nominalFace,
            Direction cullFace) {}

    /**
     * 中文：原版 1.21.1 十字 pane 的单位空间 cap 集合：中心 post + 四 arm，与运行时
     * capture 日志顶点一致（0.4375/0.5625/0.0/1.0）。
     *
     * <p>English: Unit-space cap set of the vanilla 1.21.1 cross pane: center post plus
     * four arms, matching the runtime capture-log vertices (0.4375/0.5625/0.0/1.0).
     */
    private static final List<CapDef> CAPS = List.of(
            new CapDef("UP:POST", Direction.UP,
                    POST_MIN, POST_MAX, POST_MIN, POST_MAX),
            new CapDef("DOWN:POST", Direction.DOWN,
                    POST_MIN, POST_MAX, POST_MIN, POST_MAX),
            new CapDef("UP:NORTH", Direction.UP,
                    POST_MIN, POST_MAX, 0.0F, POST_MIN),
            new CapDef("DOWN:NORTH", Direction.DOWN,
                    POST_MIN, POST_MAX, 0.0F, POST_MIN),
            new CapDef("UP:EAST", Direction.UP,
                    POST_MAX, 1.0F, POST_MIN, POST_MAX),
            new CapDef("DOWN:EAST", Direction.DOWN,
                    POST_MAX, 1.0F, POST_MIN, POST_MAX),
            new CapDef("UP:SOUTH", Direction.UP,
                    POST_MIN, POST_MAX, POST_MAX, 1.0F),
            new CapDef("DOWN:SOUTH", Direction.DOWN,
                    POST_MIN, POST_MAX, POST_MAX, 1.0F),
            new CapDef("UP:WEST", Direction.UP,
                    0.0F, POST_MIN, POST_MIN, POST_MAX),
            new CapDef("DOWN:WEST", Direction.DOWN,
                    0.0F, POST_MIN, POST_MIN, POST_MAX));

    private record CapDef(
            String label,
            Direction face,
            float xMin,
            float xMax,
            float zMin,
            float zMax) {
        private void emit(RecordingEmitter emitter) {
            float y = face == Direction.UP ? 1.0F : 0.0F;
            emitter.label(label);
            emitter.nominalFace(face);
            emitter.cullFace(null);
            emitter.pos(0, xMin, y, zMin);
            emitter.pos(1, xMax, y, zMin);
            emitter.pos(2, xMax, y, zMax);
            emitter.pos(3, xMin, y, zMax);
            emitter.emit();
        }
    }

    /**
     * 中文：发射 10 个 cap + 1 个非 UP/DOWN 竖直大面的原版布局委托；只提供几何/面，
     * 不引入任何生产逻辑。
     *
     * <p>English: Vanilla-layout delegate emitting 10 caps plus one non-UP/DOWN
     * vertical body quad; geometry/faces only, with no production logic.
     */
    private static final class PaneDelegate
            implements BakedModel {
        @Override
        public void emitBlockQuads(
                BlockAndTintGetter level,
                BlockState state,
                BlockPos pos,
                Supplier<RandomSource> randomSupplier,
                RenderContext context) {
            RecordingEmitter emitter =
                    (RecordingEmitter) context.getEmitter();
            for (CapDef cap : CAPS) {
                cap.emit(emitter);
            }
            emitter.label("VERT:NORTH");
            emitter.nominalFace(Direction.NORTH);
            emitter.cullFace(null);
            emitter.pos(0, POST_MIN, 1.0F, 0.0F);
            emitter.pos(1, POST_MAX, 1.0F, 0.0F);
            emitter.pos(2, POST_MAX, 0.0F, 0.0F);
            emitter.pos(3, POST_MIN, 0.0F, 0.0F);
            emitter.emit();
        }

        @Override
        public List<BakedQuad> getQuads(
                BlockState state,
                Direction direction,
                RandomSource random) {
            return List.of();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return true;
        }

        @Override
        public boolean isGui3d() {
            return false;
        }

        @Override
        public boolean usesBlockLight() {
            return false;
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return null;
        }

        @Override
        public ItemTransforms getTransforms() {
            return ItemTransforms.NO_TRANSFORMS;
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }

    /**
     * 中文：三格竖直世界桩：ZERO.above()/ZERO.below() 返回配置状态，其余 AIR。
     *
     * <p>English: Vertical world stub: ZERO.above()/ZERO.below() return the configured
     * states and everything else is AIR.
     */
    private static final class StubLevel
            implements BlockAndTintGetter {
        private final BlockState above;
        private final BlockState below;

        private StubLevel(
                BlockState above,
                BlockState below) {
            this.above = above;
            this.below = below;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (pos.equals(BlockPos.ZERO.above())) {
                return above;
            }
            if (pos.equals(BlockPos.ZERO.below())) {
                return below;
            }
            return air();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return 1;
        }

        @Override
        public int getMinBuildHeight() {
            return 0;
        }

        @Override
        public float getShade(
                Direction direction,
                boolean shade) {
            return 1.0F;
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return null;
        }

        @Override
        public int getBlockTint(
                BlockPos pos,
                ColorResolver resolver) {
            return -1;
        }
    }

    private static final class RecordingContext
            implements RenderContext {
        private final RecordingEmitter emitter =
                new RecordingEmitter();

        @Override
        public QuadEmitter getEmitter() {
            return emitter;
        }

        @Override
        public void pushTransform(
                QuadTransform transform) {
            emitter.applyTransform(transform);
        }

        @Override
        public void popTransform() {
            emitter.clearTransform();
        }

        @Override
        @SuppressWarnings({"removal", "deprecation"})
        public BakedModelConsumer bakedModelConsumer() {
            return new BakedModelConsumer() {
                @Override
                public void accept(BakedModel model) {
                }

                @Override
                public void accept(
                        BakedModel model,
                        BlockState state) {
                }
            };
        }

        @Override
        public boolean isFaceCulled(Direction face) {
            return false;
        }

        @Override
        public ItemDisplayContext itemTransformationMode() {
            return ItemDisplayContext.NONE;
        }
    }

    /**
     * 中文：位置感知记录发射器：emit() 时应用活动 transform，仅记录被保留的 quad
     * （label + nominalFace + cullFace）。
     *
     * <p>English: Position-aware recording emitter: applies the active transform on
     * emit() and records only quads the transform keeps (label + nominalFace +
     * cullFace).
     */
    private static final class RecordingEmitter
            implements QuadEmitter {
        private final List<QuadRecord> records =
                new ArrayList<>();
        private final float[][] positions =
                new float[4][3];
        private String label;
        private Direction nominalFace;
        private Direction cullFace;
        private RenderContext.QuadTransform transform;

        void label(String label) {
            this.label = label;
        }

        void applyTransform(
                RenderContext.QuadTransform active) {
            this.transform = active;
        }

        void clearTransform() {
            this.transform = null;
        }

        @Override
        public QuadEmitter emit() {
            boolean kept =
                    transform == null
                            || transform.transform(this);
            if (kept) {
                records.add(new QuadRecord(
                        label,
                        nominalFace,
                        cullFace));
            }
            clear();
            return this;
        }

        private void clear() {
            label = null;
            nominalFace = null;
            cullFace = null;
            for (int vertex = 0;
                    vertex < 4;
                    vertex++) {
                positions[vertex][0] = 0.0F;
                positions[vertex][1] = 0.0F;
                positions[vertex][2] = 0.0F;
            }
        }

        @Override
        public QuadEmitter square(
                Direction face,
                float left,
                float bottom,
                float right,
                float top,
                float depth) {
            return this;
        }

        @Override
        public QuadEmitter spriteBake(
                TextureAtlasSprite sprite,
                int bakeFlags) {
            return this;
        }

        @Override
        public QuadEmitter pos(
                int vertexIndex,
                float x,
                float y,
                float z) {
            positions[vertexIndex][0] = x;
            positions[vertexIndex][1] = y;
            positions[vertexIndex][2] = z;
            return this;
        }

        @Override
        public QuadEmitter uv(
                int vertexIndex,
                float u,
                float v) {
            return this;
        }

        @Override
        public QuadEmitter color(
                int vertexIndex,
                int color) {
            return this;
        }

        @Override
        public QuadEmitter lightmap(
                int vertexIndex,
                int light) {
            return this;
        }

        @Override
        public QuadEmitter normal(
                int vertexIndex,
                float x,
                float y,
                float z) {
            return this;
        }

        @Override
        public QuadEmitter cullFace(Direction face) {
            this.cullFace = face;
            return this;
        }

        @Override
        public QuadEmitter nominalFace(
                Direction face) {
            this.nominalFace = face;
            return this;
        }

        @Override
        public QuadEmitter material(
                RenderMaterial material) {
            return this;
        }

        @Override
        public QuadEmitter colorIndex(int colorIndex) {
            return this;
        }

        @Override
        public QuadEmitter tag(int tag) {
            return this;
        }

        @Override
        public QuadEmitter copyFrom(QuadView quad) {
            return this;
        }

        @Override
        public QuadEmitter fromVanilla(
                int[] vertices,
                int vertexIndex) {
            return this;
        }

        @Override
        public QuadEmitter fromVanilla(
                BakedQuad quad,
                RenderMaterial material,
                Direction cullFace) {
            return this;
        }

        @Override
        public float x(int vertexIndex) {
            return positions[vertexIndex][0];
        }

        @Override
        public float y(int vertexIndex) {
            return positions[vertexIndex][1];
        }

        @Override
        public float z(int vertexIndex) {
            return positions[vertexIndex][2];
        }

        @Override
        public float posByIndex(
                int vertexIndex,
                int coordinateIndex) {
            return positions[vertexIndex][coordinateIndex];
        }

        @Override
        public org.joml.Vector3f copyPos(
                int vertexIndex,
                org.joml.Vector3f target) {
            if (target == null) {
                target = new org.joml.Vector3f();
            }
            return target.set(
                    positions[vertexIndex][0],
                    positions[vertexIndex][1],
                    positions[vertexIndex][2]);
        }

        @Override
        public int color(int vertexIndex) {
            return -1;
        }

        @Override
        public float u(int vertexIndex) {
            return 0.0F;
        }

        @Override
        public float v(int vertexIndex) {
            return 0.0F;
        }

        @Override
        public org.joml.Vector2f copyUv(
                int vertexIndex,
                org.joml.Vector2f target) {
            return target;
        }

        @Override
        public int lightmap(int vertexIndex) {
            return 0;
        }

        @Override
        public boolean hasNormal(int vertexIndex) {
            return false;
        }

        @Override
        public float normalX(int vertexIndex) {
            return 0.0F;
        }

        @Override
        public float normalY(int vertexIndex) {
            return 0.0F;
        }

        @Override
        public float normalZ(int vertexIndex) {
            return 0.0F;
        }

        @Override
        public org.joml.Vector3f copyNormal(
                int vertexIndex,
                org.joml.Vector3f target) {
            return target;
        }

        @Override
        public Direction cullFace() {
            return cullFace;
        }

        @Override
        public Direction lightFace() {
            return nominalFace;
        }

        @Override
        public Direction nominalFace() {
            return nominalFace;
        }

        @Override
        public org.joml.Vector3f faceNormal() {
            return new org.joml.Vector3f();
        }

        @Override
        public RenderMaterial material() {
            return null;
        }

        @Override
        public int colorIndex() {
            return -1;
        }

        @Override
        public int tag() {
            return 0;
        }

        @Override
        public void toVanilla(
                int[] vertices,
                int vertexIndex) {
        }
    }
}
