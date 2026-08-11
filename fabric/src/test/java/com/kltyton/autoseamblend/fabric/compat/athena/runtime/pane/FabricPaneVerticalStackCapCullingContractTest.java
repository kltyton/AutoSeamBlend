package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.engine.EngineDescriptor;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import com.kltyton.autoseamblend.engine.routing.ModelOwnershipRuntime;
import com.kltyton.autoseamblend.engine.routing.NativeCaptureHealth;
import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.engine.routing.query.EngineRouteProvenance;
import com.kltyton.autoseamblend.engine.routing.query.EngineRouteSelection;
import com.kltyton.autoseamblend.reload.rule.ManagedRuleSnapshot;
import com.kltyton.autoseamblend.reload.rule.NativeRuleSnapshot;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.PreparedSurfaceMethods;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSetCatalog;
import com.kltyton.autoseamblend.texture.atlas.ResolvedSpriteCatalog;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Block;
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
 * 中文：RED 契约——竖直堆叠的同组 thin pane，内部层边界（层与层相接处）的 UP/DOWN
 * EDGE cap 必须为 0，最顶 UP 与最底 DOWN 外表面 cap 保留；不同连接组的堆叠（不连接）
 * 时全部 cap 保留。判定复用 common 同块/同连接组语义，Fabric 只做 cull 接线，不依赖
 * 方块 ID/精灵白名单。
 *
 * <p>English: RED contract -- vertically stacked thin panes in the same connection group
 * must emit zero UP/DOWN EDGE caps on internal layer boundaries (the seams between
 * stacked cells), while the top-most UP and
 * bottom-most DOWN outer caps stay; stacking panes from different groups (not connected)
 * keeps every cap. The predicate reuses the common same-block/same-group semantics (the
 * identity fallback for empty explicit rules); Fabric only wires the culling, with no
 * block-id/sprite whitelist.
 */
class FabricPaneVerticalStackCapCullingContractTest {
    private static final long GENERATION = 1L;
    private static final String EDGE_TEXTURE =
            "minecraft:block/green_stained_glass_pane_top";

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void sameBlockThreeLayerStackCullsInternalCapsKeepsOuterCaps() {
        BlockState pane = Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState()
                .setValue(IronBarsBlock.NORTH, true)
                .setValue(IronBarsBlock.SOUTH, true);
        ConnectionRuleSet<Block> rules =
                compileTargets(List.of(
                        "minecraft:green_stained_glass_pane"));
        BakedModel model = model(pane, rules);
        VerticalPaneLevel level =
                new VerticalPaneLevel(pane, pane, pane);

        // 中文：底层 DOWN 外表面保留；底层 UP 内部（上方有 pane）必须为 0。
        // English: The bottom-most DOWN outer cap stays; the bottom UP (internal, a
        // pane exists above) must be zero.
        EmittedFaces bottom = emit(model, pane, level, 0);
        assertTrue(
                countEdge(bottom, Direction.DOWN) > 0,
                "bottom-most DOWN outer cap must stay");
        assertEquals(
                0,
                countEdge(bottom, Direction.UP),
                "internal UP cap must be zero when a same-group pane sits above");

        // 中文：中层上下都是 pane，UP/DOWN 内部 cap 必须都为 0。
        // English: The middle layer has panes both above and below; both internal
        // UP and DOWN caps must be zero.
        EmittedFaces middle = emit(model, pane, level, 1);
        assertEquals(
                0,
                countEdge(middle, Direction.UP),
                "internal UP cap must be zero at a vertical seam");
        assertEquals(
                0,
                countEdge(middle, Direction.DOWN),
                "internal DOWN cap must be zero at a vertical seam");

        // 中文：顶层 UP 外表面保留；顶层 DOWN 内部必须为 0。
        // English: The top-most UP outer cap stays; the top DOWN (internal) must be zero.
        EmittedFaces top = emit(model, pane, level, 2);
        assertTrue(
                countEdge(top, Direction.UP) > 0,
                "top-most UP outer cap must stay");
        assertEquals(
                0,
                countEdge(top, Direction.DOWN),
                "internal DOWN cap must be zero when a same-group pane sits below");
    }

    @Test
    void differentGroupStackKeepsEveryCap() {
        BlockState green = Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState()
                .setValue(IronBarsBlock.NORTH, true)
                .setValue(IronBarsBlock.SOUTH, true);
        BlockState plain = Blocks.GLASS_PANE
                .defaultBlockState()
                .setValue(IronBarsBlock.NORTH, true)
                .setValue(IronBarsBlock.SOUTH, true);
        // 中文：两个 block 各自独立 auto 目标、无共享 tag 组：竖直方向不连接，cap 保留。
        // English: Two independent auto targets with no shared tag group: no vertical
        // connection, so both caps stay.
        ConnectionRuleSet<Block> rules =
                compileTargets(List.of(
                        "minecraft:green_stained_glass_pane",
                        "minecraft:glass_pane"));
        BakedModel model = model(green, rules);
        VerticalPaneLevel level =
                new VerticalPaneLevel(plain, green, plain);

        EmittedFaces middle = emit(model, green, level, 1);
        assertTrue(
                countEdge(middle, Direction.UP) > 0
                        && countEdge(middle, Direction.DOWN) > 0,
                "different-group stacking must keep internal caps (not connected)");
    }

    private static int countEdge(
            EmittedFaces faces,
            Direction face) {
        return faces.counts().getOrDefault(
                face,
                0);
    }

    private static BakedModel model(
            BlockState pane,
            ConnectionRuleSet<Block> rules) {
        TextureAtlasSprite paneSprite =
                sprite("minecraft:block/green_stained_glass");
        TextureAtlasSprite edgeSprite =
                sprite(EDGE_TEXTURE);
        MinecraftSurfaceCatalog.Snapshot surfaces =
                MinecraftSurfaceCatalog.Snapshot.empty(
                        GENERATION);
        ReloadPublication.Generation generation =
                new ReloadPublication.Generation(
                        GENERATION,
                        NativeRuleSnapshot.empty(GENERATION),
                        ManagedRuleSnapshot.empty(GENERATION),
                        new RuleRuntime.Snapshot(
                                GENERATION,
                                rules,
                                false,
                                1,
                                "test",
                                List.of()),
                        PreparedSurfaceMethods.Snapshot.empty(
                                GENERATION),
                        GeneratedSpriteSetCatalog.Snapshot.empty(
                                GENERATION),
                        surfaces,
                        ModelOwnershipRuntime.Snapshot.empty(
                                GENERATION),
                        NativeCaptureHealth.Snapshot.empty(
                                GENERATION),
                        ResolvedSpriteCatalog.empty(
                                GENERATION));
        return FabricAthenaGeneratedPaneModelFactory
                .create(
                        FabricPaneVerticalStackCapCullingContractTest
                                ::spriteForMaterial,
                        generation,
                        surfaces,
                        pane,
                        new SourceDelegate(
                                paneSprite,
                                edgeSprite),
                        Optional.of(selection()))
                .orElseThrow();
    }

    private static EngineQuerySelection selection() {
        EngineDescriptor athena = new EngineDescriptor(
                "athena",
                EngineFamily.ATHENA,
                "athena",
                "athena",
                "4.0.6",
                "athena-fabric-4.0.6");
        EngineRouteSelection route =
                new EngineRouteSelection(
                        athena,
                        EngineRouteProvenance.config(
                                SourceTier.CONFIG_COMPATIBILITY,
                                0),
                        List.of(),
                        Optional.empty(),
                        ConnectionMethod.AUTO);
        return new EngineQuerySelection(
                route,
                Optional.empty(),
                PreparedSurfaceMethods.Snapshot.empty(
                        GENERATION));
    }

    private static ConnectionRuleSet<Block> compileTargets(
            List<String> blockIds) {
        ConnectionRuleSet.Compilation<Block> compilation =
                ConnectionRuleSet.compile(
                        Map.of(
                                "auto",
                                Map.of(
                                        "non-compatibility",
                                        blockIds)),
                        Map.of(),
                        RESOLVER);
        assertTrue(
                compilation.valid(),
                "test rules must compile: "
                        + compilation.diagnostics());
        return compilation.rules();
    }

    private static final ConnectionRuleSet.Resolver<Block>
            RESOLVER = new ConnectionRuleSet.Resolver<>() {
                @Override
                public boolean isValidId(String id) {
                    return net.minecraft.core.registries
                            .BuiltInRegistries.BLOCK
                            .containsKey(
                                    new ResourceLocation(id));
                }

                @Override
                public Optional<Block> block(String id) {
                    return Optional.ofNullable(
                            net.minecraft.core.registries
                                    .BuiltInRegistries.BLOCK
                                    .get(
                                            new ResourceLocation(
                                                    id)));
                }

                @Override
                public Set<Block> tag(String id) {
                    return Set.of();
                }

                @Override
                public String id(Block value) {
                    return net.minecraft.core.registries
                            .BuiltInRegistries.BLOCK
                            .getKey(value)
                            .toString();
                }
            };

    private static EmittedFaces emit(
            BakedModel model,
            BlockState pane,
            VerticalPaneLevel level,
            int y) {
        RecordingContext context =
                new RecordingContext();
        model.emitBlockQuads(
                level,
                pane,
                new BlockPos(0, y, 0),
                () -> RandomSource.create(0L),
                context);
        return new EmittedFaces(
                context.emitter.emitted);
    }

    private static TextureAtlasSprite spriteForMaterial(
            Material material) {
        return sprite(material.texture().toString());
    }

    private static TextureAtlasSprite sprite(
            String name) {
        NativeImage image =
                new NativeImage(16, 16, false);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setPixelRGBA(
                        x,
                        y,
                        0xFFFFFFFF);
            }
        }
        SpriteContents contents = new SpriteContents(
                new ResourceLocation(name),
                new FrameSize(16, 16),
                image,
                AnimationMetadataSection.EMPTY);
        return new TestSprite(
                TextureAtlas.LOCATION_BLOCKS,
                contents);
    }

    private record EmittedFaces(
            List<EmittedQuad> quads) {
        private Map<Direction, Integer> counts() {
            java.util.EnumMap<Direction, Integer> counts =
                    new java.util.EnumMap<>(
                            Direction.class);
            for (EmittedQuad quad : quads) {
                if (quad.sprite.contents()
                        .name()
                        .toString()
                        .equals(EDGE_TEXTURE)) {
                    counts.merge(
                            quad.face,
                            1,
                            Integer::sum);
                }
            }
            return counts;
        }
    }

    private record EmittedQuad(
            Direction face,
            TextureAtlasSprite sprite) {}

    /**
     * 中文：竖直三层 pane 世界桩：y=0/1/2 返回对应 pane，其余 AIR。
     *
     * <p>English: Vertical three-layer pane world stub: y=0/1/2 return the configured pane
     * states and everything else is AIR.
     */
    private static final class VerticalPaneLevel
            implements earth.terrarium.athena.api.client.utils
                    .AppearanceAndTintGetter {
        private final BlockState bottom;
        private final BlockState middle;
        private final BlockState top;

        private VerticalPaneLevel(
                BlockState bottom,
                BlockState middle,
                BlockState top) {
            this.bottom = bottom;
            this.middle = middle;
            this.top = top;
        }

        private BlockState at(BlockPos pos) {
            return switch (pos.getY()) {
                case 0 -> bottom;
                case 1 -> middle;
                case 2 -> top;
                default ->
                        Blocks.AIR.defaultBlockState();
            };
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return at(pos);
        }

        @Override
        public BlockState getAppearance(
                BlockState source,
                BlockPos pos,
                Direction face,
                BlockState otherState,
                BlockPos otherPos) {
            return at(pos);
        }

        @Override
        public BlockState getAppearance(
                BlockPos pos,
                Direction face) {
            return at(pos);
        }

        @Override
        public BlockState getAppearance(
                BlockPos pos,
                Direction face,
                BlockState source,
                BlockPos otherPos) {
            return at(pos);
        }

        @Override
        public Query query(
                BlockPos pos,
                Direction face,
                BlockState source,
                BlockPos otherPos) {
            BlockState appearance = at(pos);
            return new Query(
                    appearance,
                    appearance);
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
            return 3;
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

    /** 中文：原版 pane 布局委托：方向桶=窄 cap（edge），null 桶=条带大面（pane）。 / English: Vanilla-layout delegate: direction buckets hold narrow caps (edge), the null bucket holds the strip body faces (pane). */
    private static final class SourceDelegate
            implements BakedModel {
        private final TextureAtlasSprite pane;
        private final TextureAtlasSprite edge;

        private SourceDelegate(
                TextureAtlasSprite pane,
                TextureAtlasSprite edge) {
            this.pane = pane;
            this.edge = edge;
        }

        @Override
        public List<BakedQuad> getQuads(
                BlockState state,
                Direction direction,
                RandomSource random) {
            if (direction == Direction.NORTH) {
                return List.of(quad(
                        Direction.NORTH,
                        edge,
                        new float[][] {
                            {7.0F, 16.0F, 0.0F},
                            {9.0F, 16.0F, 0.0F},
                            {9.0F, 0.0F, 0.0F},
                            {7.0F, 0.0F, 0.0F}
                        }));
            }
            if (direction == Direction.SOUTH) {
                return List.of(quad(
                        Direction.SOUTH,
                        edge,
                        new float[][] {
                            {7.0F, 16.0F, 16.0F},
                            {9.0F, 16.0F, 16.0F},
                            {9.0F, 0.0F, 16.0F},
                            {7.0F, 0.0F, 16.0F}
                        }));
            }
            if (direction == Direction.UP) {
                return List.of(quad(
                        Direction.UP,
                        edge,
                        new float[][] {
                            {7.0F, 16.0F, 0.0F},
                            {9.0F, 16.0F, 0.0F},
                            {9.0F, 16.0F, 7.0F},
                            {7.0F, 16.0F, 7.0F}
                        }));
            }
            if (direction == Direction.DOWN) {
                return List.of(quad(
                        Direction.DOWN,
                        edge,
                        new float[][] {
                            {7.0F, 0.0F, 0.0F},
                            {9.0F, 0.0F, 0.0F},
                            {9.0F, 0.0F, 7.0F},
                            {7.0F, 0.0F, 7.0F}
                        }));
            }
            if (direction == null) {
                return List.of(
                        quad(
                                Direction.WEST,
                                pane,
                                new float[][] {
                                    {7.0F, 16.0F, 0.0F},
                                    {7.0F, 16.0F, 7.0F},
                                    {7.0F, 0.0F, 7.0F},
                                    {7.0F, 0.0F, 0.0F}
                                }),
                        quad(
                                Direction.EAST,
                                pane,
                                new float[][] {
                                    {9.0F, 16.0F, 0.0F},
                                    {9.0F, 16.0F, 7.0F},
                                    {9.0F, 0.0F, 7.0F},
                                    {9.0F, 0.0F, 0.0F}
                                }));
            }
            return List.of();
        }

        private static BakedQuad quad(
                Direction face,
                TextureAtlasSprite sprite,
                float[][] positions) {
            VertexFormat format =
                    DefaultVertexFormat.BLOCK;
            int stride = format.getVertexSize() / 4;
            int uvOffset = 4 /* UV0 int offset in DefaultVertexFormat.BLOCK */;
            int[] vertices = new int[stride * 4];
            for (int vertex = 0;
                    vertex < 4;
                    vertex++) {
                int base = vertex * stride;
                vertices[base] = Float.floatToRawIntBits(
                        positions[vertex][0]);
                vertices[base + 1] = Float.floatToRawIntBits(
                        positions[vertex][1]);
                vertices[base + 2] = Float.floatToRawIntBits(
                        positions[vertex][2]);
                vertices[base + 3] = 0xFFFFFFFF;
                vertices[base + uvOffset] =
                        Float.floatToRawIntBits(
                                sprite.getU0());
                vertices[base + uvOffset + 1] =
                        Float.floatToRawIntBits(
                                sprite.getV0());
            }
            return new BakedQuad(
                    vertices,
                    -1,
                    face,
                    sprite,
                    false);
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
            return pane;
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

    private static final class RecordingEmitter
            implements QuadEmitter {
        private final List<EmittedQuad> emitted =
                new ArrayList<>();
        private TextureAtlasSprite sprite;
        private Direction face;
        private RenderContext.QuadTransform transform;

        @Override
        public QuadEmitter emit() {
            emitted.add(new EmittedQuad(
                    face,
                    sprite));
            return this;
        }

        void applyTransform(
                RenderContext.QuadTransform active) {
            this.transform = active;
        }

        void clearTransform() {
            this.transform = null;
        }

        @Override
        public QuadEmitter square(
                Direction face,
                float left,
                float bottom,
                float right,
                float top,
                float depth) {
            this.face = face;
            return this;
        }

        @Override
        public QuadEmitter spriteBake(
                TextureAtlasSprite sprite,
                int bakeFlags) {
            this.sprite = sprite;
            return this;
        }

        @Override
        public QuadEmitter pos(
                int vertexIndex,
                float x,
                float y,
                float z) {
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
            return this;
        }

        @Override
        public QuadEmitter nominalFace(
                Direction face) {
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
            this.face = quad.getDirection();
            return this;
        }

        @Override
        public float x(int vertexIndex) {
            return 0.0F;
        }

        @Override
        public float y(int vertexIndex) {
            return 0.0F;
        }

        @Override
        public float z(int vertexIndex) {
            return 0.0F;
        }

        @Override
        public float posByIndex(
                int vertexIndex,
                int coordinateIndex) {
            return 0.0F;
        }

        @Override
        public org.joml.Vector3f copyPos(
                int vertexIndex,
                org.joml.Vector3f target) {
            return target;
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
            return null;
        }

        @Override
        public Direction lightFace() {
            return face;
        }

        @Override
        public Direction nominalFace() {
            return face;
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

    private static final class TestSprite
            extends TextureAtlasSprite {
        private TestSprite(
                ResourceLocation atlasLocation,
                SpriteContents contents) {
            super(
                    atlasLocation,
                    contents,
                    16,
                    16,
                    0,
                    0);
        }
    }
}
