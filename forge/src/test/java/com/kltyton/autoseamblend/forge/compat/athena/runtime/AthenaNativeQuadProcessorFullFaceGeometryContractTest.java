package com.kltyton.autoseamblend.forge.compat.athena.runtime;

import com.kltyton.autoseamblend.forge.testing.ForgeTestBootstrap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeProvider;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：{@link AthenaNativeQuadProcessor} fullFace 替换路径的几何与 UV 契约测试。
 * 锁定 Athena 4.0.6 原生合同（common {@link AthenaNativeProvider} 已按
 * ConnectedBlockModel 字节码对齐）：allTrue 状态返回单个 {@code withSprite(1)}
 * 完整面 quad，非 allTrue 状态返回四个象限子矩形 quad。fullFace 非 overlay 对每个
 * nativeQuad 走原生烘焙（ForgeAthenaUtils.bakeQuad），因此 allTrue 自然输出 1 张
 * 整面（几何/UV 面积各 1），partial 自然输出 4 张互不重叠、总面积 1、各采样自己
 * native bounds 精灵子区域的象限 quad。禁止把每个 nativeQuad 整面重贴成完整源面
 * （partial 会得到总面积 4 的重叠面，叠色/接缝）。
 *
 * <p>English: Geometry/UV contract tests for the fullFace replacement path of
 * {@link AthenaNativeQuadProcessor}. Locks Athena 4.0.6's native contract (common
 * {@link AthenaNativeProvider} now mirrors ConnectedBlockModel bytecode): the allTrue
 * state yields a single {@code withSprite(1)} full-face quad while non-allTrue states
 * yield four quadrant sub-rect quads. The fullFace non-overlay path bakes every native
 * quad natively (ForgeAthenaUtils.bakeQuad), so allTrue naturally emits one full face
 * (geometry/UV area 1) and partial states naturally emit four non-overlapping quadrants
 * (total area 1, each sampling the sprite sub-region of its native bounds). Re-texturing
 * every native quad into a full-source face is forbidden (partial would produce four
 * coincident faces with total area 4, overdraw/seams).
 */
class AthenaNativeQuadProcessorFullFaceGeometryContractTest {
    private static final float EPS = 1.0e-4F;
    private static final int ATLAS_SIZE = 2048;

    @BeforeAll
    static void bootstrapRegistries() {
        // 中文：独立 JVM 测试需要游戏版本与注册表引导，否则 Blocks/FeatureFlags
        // 静态初始化抛 ExceptionInInitializerError；与既有 delegate 测试同型。
        // English: Standalone JVM tests need a game version and registry bootstrap or
        // Blocks/FeatureFlags static init throws ExceptionInInitializerError; same shape
        // as the existing delegate tests, test-only initialization.
        ForgeTestBootstrap.bootStrap();
    }

    @Test
    void allTrueEmitsSingleFullFaceQuad() {
        // 中文：origin 周围 3x3x3 全玻璃（allTrue），4.0.6 合同为单张
        // AthenaQuad.withSprite(1) 完整面；fullFace 非 overlay 原生烘焙后必须恰好
        // 输出 1 张整面 quad：几何面积 1、UV 面积 1、精灵为 EMPTY 角色槽（索引 1）。
        // English: A full 3x3x3 glass cube around origin (allTrue) follows 4.0.6's
        // single AthenaQuad.withSprite(1) full face; after the native fullFace
        // non-overlay bake exactly one full-face quad must be emitted: geometry
        // area 1, UV area 1, and the EMPTY role sprite (slot 1).
        GridLevel level = new GridLevel();
        fillCube(level);
        TextureAtlasSprite[] stateSprites = stateSprites();
        List<BakedQuad> quads =
                processFullFace(level, stateSprites);
        assertEquals(
                1,
                quads.size(),
                "allTrue must emit exactly one full-face quad");
        assertEquals(
                1.0,
                area(quads.get(0)),
                EPS,
                "the single allTrue quad must cover the full face");
        assertEquals(
                expectedUvArea(stateSprites[1]),
                uvArea(quads.get(0)),
                EPS,
                "the single allTrue quad must sample the full sprite"
                        + " (minus uvShrinkRatio)");
        assertSame(
                stateSprites[1],
                quads.get(0).getSprite(),
                "allTrue must use the EMPTY role sprite (slot 1)");
    }

    @Test
    void partialVerticalColumnEmitsFourNonOverlappingQuadrants() {
        // 中文：NORTH 面仅上下（+Y/-Y）有玻璃邻层（部分状态）时，合同输出四个象限
        // 子矩形 quad；fullFace 非 overlay 原生烘焙后必须互不重叠、几何总面积 1，
        // 任何 quad 都不得覆盖整面。
        // English: With only the +Y/-Y glass neighbors on the NORTH face (partial
        // state) the contract emits four quadrant sub-rect quads; after the native
        // fullFace non-overlay bake they must be non-overlapping with total geometry
        // area 1, and no quad may cover the whole face.
        GridLevel level = new GridLevel();
        level.set(
                BlockPos.ZERO.offset(0, 1, 0),
                Blocks.GLASS.defaultBlockState());
        level.set(
                BlockPos.ZERO.offset(0, -1, 0),
                Blocks.GLASS.defaultBlockState());
        List<BakedQuad> quads =
                processFullFace(level, stateSprites());
        assertEquals(
                4,
                quads.size(),
                "partial states must emit four native quadrant quads");
        assertEquals(
                1.0,
                totalCoverageArea(quads),
                EPS,
                "four quadrant quads must tile the source face exactly once");
        for (BakedQuad quad : quads) {
            assertTrue(
                    area(quad) <= 0.5 + EPS,
                    "no quadrant quad may cover the full source face: "
                            + area(quad));
        }
        assertNoOverlap(quads);
    }

    @Test
    void partialUvSubRegionsPartitionSpriteExactlyOnce() {
        // 中文：部分状态下每个象限 quad 的 UV 必须落在对应 native bounds 的精灵
        // 子区域（UV 总面积 1），任何 quad 都不得采样整张精灵。
        // English: Under partial states every quadrant quad's UVs must stay inside the
        // sprite sub-region of its native bounds (total UV area 1); no quad may sample
        // the whole sprite.
        GridLevel level = new GridLevel();
        level.set(
                BlockPos.ZERO.offset(0, 1, 0),
                Blocks.GLASS.defaultBlockState());
        level.set(
                BlockPos.ZERO.offset(0, -1, 0),
                Blocks.GLASS.defaultBlockState());
        List<BakedQuad> quads =
                processFullFace(level, stateSprites());
        assertEquals(
                4,
                quads.size(),
                "partial states must emit four native quadrant quads");
        assertEquals(
                expectedUvArea(stateSprites()[1]),
                totalUvArea(quads),
                EPS,
                "UV sub-regions must partition the sprite exactly once"
                        + " (minus uvShrinkRatio)");
        for (BakedQuad quad : quads) {
            assertTrue(
                    uvArea(quad) <= 0.5 + EPS,
                    "no quadrant quad may sample the full sprite: "
                            + uvArea(quad));
        }
    }

    @Test
    void completeMissingAcceptsAllTrueSingleQuad() {
        // 中文：common provider 按 4.0.6 合同在 allTrue 时只返回单张
        // withSprite(1) quad；completeMissing 必须接受该单 quad（不再要求四个象限），
        // 并补全为 EMPTY 角色槽（索引 1）的整面重贴。
        // English: Under allTrue the common provider returns a single withSprite(1)
        // quad per the 4.0.6 contract; completeMissing must accept that single quad
        // (no four-quadrant assumption anymore) and complete it as a full-face
        // retexture with the EMPTY role sprite (slot 1).
        GridLevel level = new GridLevel();
        fillCube(level);
        TextureAtlasSprite[] stateSprites = stateSprites();
        BakedQuad source =
                fullFrameQuad(
                        stateSprites[0],
                        Direction.NORTH);
        Optional<BakedQuad> completed =
                AthenaNativeQuadProcessor.completeMissing(
                        source,
                        stateSprites,
                        level,
                        BlockPos.ZERO,
                        Blocks.GLASS.defaultBlockState(),
                        selfConnectRules());
        assertTrue(
                completed.isPresent(),
                "allTrue single-quad selection must be completable");
        assertSame(
                stateSprites[1],
                completed.orElseThrow().getSprite(),
                "allTrue completion must use the EMPTY role sprite (slot 1)");
        assertEquals(
                1.0,
                area(completed.orElseThrow()),
                EPS,
                "allTrue completion must stay a full-face quad");
    }

    @Test
    void completeMissingRejectsPartialMixedSlots() {
        // 中文：仅单个 +Y 邻层（NORTH 面）产生部分状态，四个象限角槽不一致
        // （2 个非 0 角色槽 + 2 个 slot0）；completeMissing 必须拒绝，不能补全成
        // 单一整面。
        // English: A single +Y neighbor on the NORTH face yields a partial state whose
        // four quadrant corner slots differ (two non-zero role slots plus two slot0);
        // completeMissing must reject it instead of collapsing it to one full face.
        GridLevel level = new GridLevel();
        level.set(
                BlockPos.ZERO.offset(0, 1, 0),
                Blocks.GLASS.defaultBlockState());
        TextureAtlasSprite[] stateSprites = stateSprites();
        Optional<BakedQuad> completed =
                AthenaNativeQuadProcessor.completeMissing(
                        fullFrameQuad(
                                stateSprites[0],
                                Direction.NORTH),
                        stateSprites,
                        level,
                        BlockPos.ZERO,
                        Blocks.GLASS.defaultBlockState(),
                        selfConnectRules());
        assertTrue(
                completed.isEmpty(),
                "partial mixed-slot selections must not be completed");
    }

    private static void fillCube(GridLevel level) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.set(
                            BlockPos.ZERO.offset(dx, dy, dz),
                            Blocks.GLASS.defaultBlockState());
                }
            }
        }
    }

    private static TextureAtlasSprite[] stateSprites() {
        TextureAtlasSprite[] stateSprites =
                new TextureAtlasSprite[AthenaNativeProvider.ROLE_COUNT];
        for (int slot = 0;
                slot < stateSprites.length;
                slot++) {
            stateSprites[slot] = TestSprite.at(slot * 16, 0);
        }
        return stateSprites;
    }

    private static List<BakedQuad> processFullFace(
            GridLevel level,
            TextureAtlasSprite[] stateSprites) {
        return AthenaNativeQuadProcessor.process(
                fullFrameQuad(
                        stateSprites[0],
                        Direction.NORTH),
                stateSprites,
                level,
                BlockPos.ZERO,
                Blocks.GLASS.defaultBlockState(),
                selfConnectRules(),
                true,
                Optional.empty());
    }

    /**
     * 中文：构造 BLOCK 格式、几何覆盖整面（NORTH，z=0）、UV 覆盖源精灵 atlas bounds
     * 的完整源面 quad。1.21.1 DefaultVertexFormat.BLOCK 为 32 字节/顶点（8 ints）：
     * Position/Color/UV0/UV2/Normal+padding，与 FaceBakery 和 BakedQuadTextureBasis
     * 的 8-int 步长一致。
     *
     * <p>English: Builds a full-frame BLOCK-format BakedQuad whose geometry covers the
     * whole NORTH face (z=0) and whose UVs span the source sprite's atlas bounds.
     * 1.21.1 DefaultVertexFormat.BLOCK is 32 bytes per vertex (8 ints):
     * Position/Color/UV0/UV2/Normal+padding, matching FaceBakery and
     * BakedQuadTextureBasis' 8-int stride.
     */
    private static BakedQuad fullFrameQuad(
            TextureAtlasSprite sprite,
            Direction face) {
        int stride = 8;
        int[] vertices = new int[4 * stride];
        float[][] positions = switch (face) {
            case DOWN -> new float[][] {
                {0.0F, 0.0F, 0.0F},
                {0.0F, 0.0F, 1.0F},
                {1.0F, 0.0F, 1.0F},
                {1.0F, 0.0F, 0.0F}
            };
            case UP -> new float[][] {
                {0.0F, 1.0F, 0.0F},
                {0.0F, 1.0F, 1.0F},
                {1.0F, 1.0F, 1.0F},
                {1.0F, 1.0F, 0.0F}
            };
            case NORTH -> new float[][] {
                {1.0F, 1.0F, 0.0F},
                {1.0F, 0.0F, 0.0F},
                {0.0F, 0.0F, 0.0F},
                {0.0F, 1.0F, 0.0F}
            };
            case SOUTH -> new float[][] {
                {0.0F, 1.0F, 1.0F},
                {0.0F, 0.0F, 1.0F},
                {1.0F, 0.0F, 1.0F},
                {1.0F, 1.0F, 1.0F}
            };
            case WEST -> new float[][] {
                {0.0F, 1.0F, 0.0F},
                {0.0F, 0.0F, 0.0F},
                {0.0F, 0.0F, 1.0F},
                {0.0F, 1.0F, 1.0F}
            };
            case EAST -> new float[][] {
                {1.0F, 1.0F, 0.0F},
                {1.0F, 0.0F, 0.0F},
                {1.0F, 0.0F, 1.0F},
                {1.0F, 1.0F, 1.0F}
            };
        };
        float[][] corners = {
            {sprite.getU0(), sprite.getV0()},
            {sprite.getU1(), sprite.getV0()},
            {sprite.getU1(), sprite.getV1()},
            {sprite.getU0(), sprite.getV1()}
        };
        for (int vertex = 0; vertex < 4; vertex++) {
            int base = vertex * stride;
            vertices[base] =
                    Float.floatToRawIntBits(
                            positions[vertex][0]);
            vertices[base + 1] =
                    Float.floatToRawIntBits(
                            positions[vertex][1]);
            vertices[base + 2] =
                    Float.floatToRawIntBits(
                            positions[vertex][2]);
            vertices[base + 3] = 0xFFFFFFFF;
            vertices[base + 4] =
                    Float.floatToRawIntBits(
                            corners[vertex][0]);
            vertices[base + 5] =
                    Float.floatToRawIntBits(
                            corners[vertex][1]);
        }
        return new BakedQuad(
                vertices,
                -1,
                face,
                sprite,
                true);
    }

    private static double totalCoverageArea(
            List<BakedQuad> quads) {
        return quads.stream()
                .mapToDouble(
                        AthenaNativeQuadProcessorFullFaceGeometryContractTest::area)
                .sum();
    }

    private static double area(BakedQuad quad) {
        int[] axes = faceAxes(quad.getDirection());
        double[] minimum = {
            Double.POSITIVE_INFINITY,
            Double.POSITIVE_INFINITY
        };
        double[] maximum = {
            Double.NEGATIVE_INFINITY,
            Double.NEGATIVE_INFINITY
        };
        for (int vertex = 0; vertex < 4; vertex++) {
            for (int axis = 0; axis < 2; axis++) {
                float value = position(
                        quad,
                        vertex,
                        axes[axis]);
                minimum[axis] = Math.min(
                        minimum[axis],
                        value);
                maximum[axis] = Math.max(
                        maximum[axis],
                        value);
            }
        }
        return (maximum[0] - minimum[0])
                * (maximum[1] - minimum[1]);
    }

    private static double totalUvArea(
            List<BakedQuad> quads) {
        return quads.stream()
                .mapToDouble(
                        AthenaNativeQuadProcessorFullFaceGeometryContractTest::uvArea)
                .sum();
    }

    /**
     * 中文：Forge 47.x 的 FaceBakery.bakeQuad 先按 sprite.uvShrinkRatio() f 把每个 UV
     * 边向中心收缩，随后 Forge 补丁的 fillVertex 再用 0.999/0.001 把每顶点 UV 与对角
     * 角点混合（防贴图渗色）。因此任意子矩形烘焙后每轴归一化跨度恒为 (1-f)*0.998，
     * 面积恒为 ((1-f)*0.998)^2，与象限切分无关。数值已用合并 jar 的 fillVertex 字节码
     * 验证（0.9921171588 vs 运行时 0.9921172385，误差 8e-8）。
     *
     * <p>English: Forge 47.x FaceBakery.bakeQuad first shrinks every UV edge toward the
     * sprite center by sprite.uvShrinkRatio() f, then Forge's patched fillVertex blends
     * each vertex UV with its opposite corner at 0.999/0.001 (anti-bleed). Every
     * sub-rect bake therefore has a normalized per-axis span of (1-f)*0.998 and area
     * ((1-f)*0.998)^2 regardless of quadrant subdivision. The value was verified against
     * the merged jar's fillVertex bytecode (0.9921171588 vs runtime 0.9921172385,
     * 8e-8 error).
     */
    private static double expectedUvArea(
            TextureAtlasSprite sprite) {
        float shrink = sprite.uvShrinkRatio();
        double scale = (1.0 - shrink) * 0.998;
        return scale * scale;
    }

    private static double uvArea(BakedQuad quad) {
        TextureAtlasSprite sprite = quad.getSprite();
        float uSpan =
                sprite.getU1() - sprite.getU0();
        float vSpan =
                sprite.getV1() - sprite.getV0();
        double minimumU = Double.POSITIVE_INFINITY;
        double maximumU = Double.NEGATIVE_INFINITY;
        double minimumV = Double.POSITIVE_INFINITY;
        double maximumV = Double.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < 4; vertex++) {
            int base = vertex * 8;
            double u =
                    (Float.intBitsToFloat(
                                    quad.getVertices()[base + 4])
                            - sprite.getU0())
                            / uSpan;
            double v =
                    (Float.intBitsToFloat(
                                    quad.getVertices()[base + 5])
                            - sprite.getV0())
                            / vSpan;
            minimumU = Math.min(minimumU, u);
            maximumU = Math.max(maximumU, u);
            minimumV = Math.min(minimumV, v);
            maximumV = Math.max(maximumV, v);
        }
        return (maximumU - minimumU)
                * (maximumV - minimumV);
    }

    private static int[] faceAxes(Direction face) {
        return switch (face) {
            case DOWN, UP -> new int[] {0, 2};
            case NORTH, SOUTH -> new int[] {0, 1};
            case WEST, EAST -> new int[] {1, 2};
        };
    }

    private static float position(
            BakedQuad quad,
            int vertex,
            int component) {
        return Float.intBitsToFloat(
                quad.getVertices()[vertex * 8 + component]);
    }

    private static void assertNoOverlap(
            List<BakedQuad> quads) {
        for (int first = 0; first < quads.size(); first++) {
            for (int second = first + 1;
                    second < quads.size();
                    second++) {
                assertTrue(
                        overlapArea(
                                quads.get(first),
                                quads.get(second))
                                <= EPS,
                        "quad " + first + " and " + second
                                + " must not overlap");
            }
        }
    }

    private static double overlapArea(
            BakedQuad left,
            BakedQuad right) {
        int[] axes = faceAxes(left.getDirection());
        double overlapX = Math.max(
                0.0,
                Math.min(
                                maximum(left, axes[0]),
                                maximum(right, axes[0]))
                        - Math.max(
                                minimum(left, axes[0]),
                                minimum(right, axes[0])));
        double overlapY = Math.max(
                0.0,
                Math.min(
                                maximum(left, axes[1]),
                                maximum(right, axes[1]))
                        - Math.max(
                                minimum(left, axes[1]),
                                minimum(right, axes[1])));
        return overlapX * overlapY;
    }

    private static double minimum(
            BakedQuad quad,
            int component) {
        double minimum = Double.POSITIVE_INFINITY;
        for (int vertex = 0; vertex < 4; vertex++) {
            minimum = Math.min(
                    minimum,
                    position(quad, vertex, component));
        }
        return minimum;
    }

    private static double maximum(
            BakedQuad quad,
            int component) {
        double maximum = Double.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < 4; vertex++) {
            maximum = Math.max(
                    maximum,
                    position(quad, vertex, component));
        }
        return maximum;
    }

    /**
     * 中文：同 ID 自连接规则：auto 桶、non-compatibility 模式，仅注册
     * minecraft:glass，与既有 CTM 网格契约测试一致。
     *
     * <p>English: Same-id self-connecting rules: auto bucket, non-compatibility mode,
     * registering only minecraft:glass; identical to the existing CTM grid tests.
     */
    private static ConnectionRuleSet<Block> selfConnectRules() {
        return ConnectionRuleSet.compile(
                        Map.of(
                                "auto",
                                Map.of(
                                        "non-compatibility",
                                        List.of("minecraft:glass"))),
                        Map.of(),
                        new ConnectionRuleSet.Resolver<Block>() {
                            @Override
                            public boolean isValidId(
                                    String id) {
                                return true;
                            }

                            @Override
                            public Optional<Block> block(
                                    String id) {
                                return Optional.ofNullable(
                                        BuiltInRegistries.BLOCK.get(
                                                new ResourceLocation(
                                                        id)));
                            }

                            @Override
                            public Set<Block> tag(
                                    String id) {
                                return Set.of();
                            }

                            @Override
                            public String id(Block value) {
                                return BuiltInRegistries.BLOCK
                                        .getKey(value)
                                        .toString();
                            }
                        })
                .rules();
    }

    /**
     * 中文：位于假定 2048x2048 方块 Atlas 指定 (x,y) 的 16x16 测试精灵，使 UV 归一化
     * 与精灵识别可测量。
     *
     * <p>English: 16x16 test sprite at (x,y) of the assumed 2048x2048 block atlas so
     * UV normalization and sprite identity are measurable.
     */
    private static final class TestSprite
            extends TextureAtlasSprite {
        private TestSprite(
                ResourceLocation atlasLocation,
                SpriteContents contents,
                int x,
                int y) {
            super(
                    atlasLocation,
                    contents,
                    ATLAS_SIZE,
                    ATLAS_SIZE,
                    x,
                    y);
        }

        private static TestSprite at(int x, int y) {
            return new TestSprite(
                    TextureAtlas.LOCATION_BLOCKS,
                    MissingTextureAtlasSprite.create(),
                    x,
                    y);
        }
    }

    /**
     * 中文：网格 BlockAndTintGetter 测试替身；缺省为 AIR，可按世界偏移放置方块状态。
     *
     * <p>English: Grid BlockAndTintGetter test double; defaults to AIR and accepts
     * block states placed by world offset.
     */
    private static final class GridLevel
            implements BlockAndTintGetter {
        private final Map<BlockPos, BlockState> states =
                new HashMap<>();

        private void set(
                BlockPos pos,
                BlockState state) {
            states.put(pos.immutable(), state);
        }

        @Override
        public BlockState getBlockState(
                BlockPos pos) {
            return states.getOrDefault(
                    pos.immutable(),
                    Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(
                BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(
                BlockPos pos) {
            return null;
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
        public LevelLightEngine getLightEngine() {
            return null;
        }

        @Override
        public float getShade(
                Direction direction,
                boolean shade) {
            return 1.0F;
        }

        @Override
        public int getBlockTint(
                BlockPos pos,
                ColorResolver resolver) {
            return -1;
        }
    }
}
