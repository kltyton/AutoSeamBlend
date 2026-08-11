package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSetCatalog;
import com.kltyton.autoseamblend.texture.atlas.ResolvedSpriteCatalog;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import earth.terrarium.athena.api.client.utils.AppearanceAndTintGetter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
 * 中文：RED 集成契约——最终 pane 发射必须单一来源且无重复：模型 emitBlockQuads 不得
 * 经由 RenderContext.bakedModelConsumer 触发 delegate（原 vanilla pane 模型）的二次发射，
 * 每个连接状态的最终发射 quad 按（face,sprite,顶点键）必须唯一（无 side-bucket/随机重复）。
 * 用户三轮回测窄侧"长条/碎片"不变，需以此证明或证伪"delegate+replacement 双发射"。
 *
 * <p>English: RED integration contract -- final pane emission must be single-sourced and
 * duplicate-free: emitBlockQuads must never trigger a second delegate (vanilla pane model)
 * emission through RenderContext.bakedModelConsumer, and every connection state's final
 * emitted quad must be unique by (face, sprite, vertex key) with no side-bucket or random
 * duplicates. Three rounds of user re-tests show the narrow-side artifacts unchanged, so
 * this proves or refutes the delegate-plus-replacement dual-emission hypothesis.
 */
class FabricPaneSingleEmissionContractTest {
    private static final long GENERATION = 1L;
    private static final String PANE_TEXTURE =
            "minecraft:block/green_stained_glass";
    private static final String EDGE_TEXTURE =
            "minecraft:block/green_stained_glass_pane_top";

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void finalEmissionIsSingleSourcedAndDuplicateFreeAcrossConnectionStates() {
        TextureAtlasSprite paneSprite =
                sprite(PANE_TEXTURE);
        TextureAtlasSprite edgeSprite =
                sprite(EDGE_TEXTURE);
        for (BlockState state : states()) {
            MinecraftSurfaceCatalog.Snapshot emptySurfaces =
                    MinecraftSurfaceCatalog.Snapshot.empty(
                            GENERATION);
            ReloadPublication.Generation generation =
                    generation(emptySurfaces);
            EngineQuerySelection selection = selection(
                    ConnectionMethod.AUTO,
                    PreparedSurfaceMethods.Snapshot.empty(
                            GENERATION));
            Optional<BakedModel> result =
                    FabricAthenaGeneratedPaneModelFactory
                            .create(
                                    this::spriteForMaterial,
                                    generation,
                                    emptySurfaces,
                                    state,
                                    new SourceDelegate(
                                            paneSprite,
                                            edgeSprite),
                                    Optional.of(selection));
            assertTrue(
                    result.isPresent(),
                    "factory must produce the dedicated pane model for "
                            + state);

            RecordingContext context =
                    new RecordingContext();
            result.orElseThrow().emitBlockQuads(
                    new ConnectedPaneLevel(state),
                    state,
                    BlockPos.ZERO,
                    () -> RandomSource.create(0L),
                    context);

            assertFalse(
                    context.emitter.emitted.isEmpty(),
                    "the pane model must emit quads for " + state);
            assertEquals(
                    0,
                    context.consumerAccepts,
                    "bakedModelConsumer must never be invoked: the delegate "
                            + "(vanilla pane) model must not be re-emitted for "
                            + state);
            assertEquals(
                    context.emitter.emitted.size(),
                    new HashSet<>(context.emitter.emitted).size(),
                    "every emitted quad must be unique by "
                            + "(face,sprite,vertex key) for " + state);
        }
    }

    private static List<BlockState> states() {
        List<BlockState> states = new ArrayList<>();
        BlockState base = Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState();
        states.add(base);
        states.add(base.setValue(
                IronBarsBlock.NORTH, true));
        states.add(base.setValue(
                        IronBarsBlock.NORTH, true)
                .setValue(
                        IronBarsBlock.SOUTH, true));
        states.add(base.setValue(
                        IronBarsBlock.NORTH, true)
                .setValue(
                        IronBarsBlock.EAST, true));
        states.add(base.setValue(
                        IronBarsBlock.NORTH, true)
                .setValue(
                        IronBarsBlock.EAST, true)
                .setValue(
                        IronBarsBlock.SOUTH, true)
                .setValue(
                        IronBarsBlock.WEST, true));
        return states;
    }

    private TextureAtlasSprite spriteForMaterial(
            Material material) {
        return sprite(
                material.texture().toString());
    }

    private static EngineQuerySelection selection(
            ConnectionMethod method,
            PreparedSurfaceMethods.Snapshot prepared) {
        EngineDescriptor athena = new EngineDescriptor(
                "athena",
                EngineFamily.ATHENA,
                "athena",
                "athena",
                "4.0.6",
                "athena-fabric-4.0.6");
        EngineRouteProvenance provenance =
                EngineRouteProvenance.config(
                        SourceTier.CONFIG_COMPATIBILITY,
                        0);
        EngineRouteSelection route =
                new EngineRouteSelection(
                        athena,
                        provenance,
                        List.of(),
                        Optional.empty(),
                        method);
        return new EngineQuerySelection(
                route,
                Optional.empty(),
                prepared);
    }

    private static ReloadPublication.Generation generation(
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        return new ReloadPublication.Generation(
                GENERATION,
                NativeRuleSnapshot.empty(GENERATION),
                ManagedRuleSnapshot.empty(GENERATION),
                RuleRuntime.bootstrapSnapshot(),
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

    /** 中文：原版布局委托：方向桶=窄 cap（edge），null 桶=条带大面（pane）。 / English: Vanilla-layout delegate: direction buckets hold narrow caps (edge), the null bucket holds the strip body faces (pane). */
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
            VertexFormat format = DefaultVertexFormat.BLOCK;
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

    /** 中文：连接 pane 世界桩：y=0 平面返回 pane 状态，其余 AIR。 / English: Connected-pane world stub: y=0 returns the pane state and everything else is AIR. */
    private static final class ConnectedPaneLevel
            implements AppearanceAndTintGetter {
        private final BlockState pane;

        private ConnectedPaneLevel(BlockState pane) {
            this.pane = pane;
        }

        private BlockState at(BlockPos pos) {
            return pos.getY() == 0
                    ? pane
                    : Blocks.AIR.defaultBlockState();
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
        public BlockState getBlockState(BlockPos pos) {
            return at(pos);
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
        private int consumerAccepts;

        @Override
        public QuadEmitter getEmitter() {
            return emitter;
        }

        @Override
        public void pushTransform(
                QuadTransform transform) {
        }

        @Override
        public void popTransform() {
        }

        @Override
        public BakedModelConsumer bakedModelConsumer() {
            consumerAccepts++;
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
        private final List<String> emitted =
                new ArrayList<>();
        private Direction face;
        private TextureAtlasSprite sprite;
        private final float[][] positions = new float[4][3];

        @Override
        public QuadEmitter square(
                Direction nominalFace,
                float left,
                float bottom,
                float right,
                float top,
                float depth) {
            this.face = nominalFace;
            return QuadEmitter.super.square(
                    nominalFace,
                    left,
                    bottom,
                    right,
                    top,
                    depth);
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
        public QuadEmitter spriteBake(
                TextureAtlasSprite sprite,
                int bakeFlags) {
            this.sprite = sprite;
            return this;
        }

        @Override
        public QuadEmitter emit() {
            StringBuilder key = new StringBuilder();
            key.append(face).append('|')
                    .append(sprite.contents().name());
            for (int vertex = 0;
                    vertex < 4;
                    vertex++) {
                key.append('|')
                        .append(positions[vertex][0])
                        .append(',')
                        .append(positions[vertex][1])
                        .append(',')
                        .append(positions[vertex][2]);
            }
            emitted.add(key.toString());
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
                int[] quadData,
                int startIndex) {
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
        public float u(int vertexIndex) {
            return 0.0F;
        }

        @Override
        public float v(int vertexIndex) {
            return 0.0F;
        }

        @Override
        public int color(int vertexIndex) {
            return 0;
        }

        @Override
        public int lightmap(int vertexIndex) {
            return 0;
        }

        @Override
        public int colorIndex() {
            return -1;
        }

        @Override
        public RenderMaterial material() {
            return null;
        }

        @Override
        public Direction cullFace() {
            return null;
        }

        @Override
        public Direction lightFace() {
            return null;
        }

        @Override
        public Direction nominalFace() {
            return null;
        }

        @Override
        public boolean hasNormal(int vertexIndex) {
            return false;
        }

        @Override
        public float normalX(int vertexIndex) {
            return Float.NaN;
        }

        @Override
        public float normalY(int vertexIndex) {
            return Float.NaN;
        }

        @Override
        public float normalZ(int vertexIndex) {
            return Float.NaN;
        }

        @Override
        public org.joml.Vector3f copyNormal(
                int vertexIndex,
                org.joml.Vector3f target) {
            return target;
        }

        @Override
        public org.joml.Vector3f faceNormal() {
            return new org.joml.Vector3f();
        }

        @Override
        public int tag() {
            return 0;
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
            return target;
        }

        @Override
        public org.joml.Vector2f copyUv(
                int vertexIndex,
                org.joml.Vector2f target) {
            return target;
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
