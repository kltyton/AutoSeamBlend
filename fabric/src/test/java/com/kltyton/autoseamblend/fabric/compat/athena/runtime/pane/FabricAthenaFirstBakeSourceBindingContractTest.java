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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 集成契约——原版 pane 的条带大面（pane 纹理，无 cullface）位于 null bucket，
 * 方向 bucket 只有窄 cap 面（edge 纹理）；1.21.1 BakedQuad 方向由几何 calculateFacing
 * 得出，null-bucket quad 也带非空方向。first-bake 回退必须同 MinecraftSurfaceCatalog 一样
 * 读取 6 个方向 bucket + null bucket，否则 body 被绑成 cap 纹理，生成瓦片全部来自顶盖
 * 纹理，主体面透明只剩竖柱。当前实现只读方向 bucket，本测试应先失败。
 *
 * <p>English: RED integration contract -- the vanilla pane's body faces (pane texture,
 * unculled) live in the null bucket while the direction buckets only hold the narrow culled
 * cap faces (edge texture); in 1.21.1 the BakedQuad direction comes from geometry
 * calculateFacing, so null-bucket quads still carry a non-null direction. The first-bake
 * fallback must read all six direction buckets plus the null bucket like
 * MinecraftSurfaceCatalog, otherwise body binds the cap texture, every generated tile comes
 * from the cap texture, and the body face turns transparent leaving only vertical columns.
 * The current implementation reads only the direction buckets, so this test fails first.
 */
class FabricAthenaFirstBakeSourceBindingContractTest {
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
    void firstBakePaneSourcesPrefersPaneStripFacesFromNullBucket() {
        BlockState pane = Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState()
                .setValue(IronBarsBlock.NORTH, true);
        TextureAtlasSprite paneSprite =
                sprite(PANE_TEXTURE);
        TextureAtlasSprite edgeSprite =
                sprite(EDGE_TEXTURE);

        FabricAthenaGeneratedPaneModelFactory
                .PaneSources sources =
                FabricAthenaGeneratedPaneModelFactory
                        .firstBakePaneSources(
                                pane,
                                new VanillaPaneDelegate(
                                        paneSprite,
                                        edgeSprite),
                                List.of())
                        .orElseThrow();

        assertEquals(
                PANE_TEXTURE,
                sources.body().sprite()
                        .contents()
                        .name()
                        .toString(),
                "body must bind the pane strip texture from the null bucket, "
                        + "not the cap texture from the direction buckets");
        assertEquals(
                EDGE_TEXTURE,
                sources.edge().sprite()
                        .contents()
                        .name()
                        .toString(),
                "edge must bind the cap texture");
    }

    @Test
    void firstBakeCreatedModelEmitsBodyDerivedSpritesForConnectedPanes() {
        BlockState pane = Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState()
                .setValue(IronBarsBlock.NORTH, true)
                .setValue(IronBarsBlock.EAST, true)
                .setValue(IronBarsBlock.SOUTH, true)
                .setValue(IronBarsBlock.WEST, true);
        TextureAtlasSprite paneSprite =
                sprite(PANE_TEXTURE);
        TextureAtlasSprite edgeSprite =
                sprite(EDGE_TEXTURE);
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
                                pane,
                                new VanillaPaneDelegate(
                                        paneSprite,
                                        edgeSprite),
                                Optional.of(selection));
        assertTrue(
                result.isPresent(),
                "first bake must create the dedicated pane model");

        RecordingRenderContext context =
                new RecordingRenderContext();
        result.orElseThrow().emitBlockQuads(
                new ConnectedPaneLevel(pane),
                pane,
                BlockPos.ZERO,
                () -> RandomSource.create(0L),
                context);

        assertFalse(
                context.emitter.emittedSprites.isEmpty(),
                "connected panes must emit quads");
        boolean bodyDerived = context.emitter
                .emittedSprites
                .stream()
                .anyMatch(name ->
                        name.contains(
                                "generated/athena/ctm/"
                                        + "minecraft/block/"
                                        + "green_stained_glass/"));
        assertTrue(
                bodyDerived,
                "connected pane emission must include body-derived generated "
                        + "tiles (from the pane strip texture), not only cap-derived "
                        + "tiles -- otherwise the body face is invisible and only "
                        + "vertical columns remain");
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
                ResourceLocation.parse(name),
                new FrameSize(16, 16),
                image,
                ResourceMetadata.EMPTY);
        return new TestSprite(
                TextureAtlas.LOCATION_BLOCKS,
                contents);
    }

    /**
     * 中文：镜像原版 pane 烘焙布局：方向 bucket 只含 cullface 的窄 cap（edge），
     * null bucket 含条带大面（pane）与上下盖；与 PaneCullingModel 重分桶后的真实输入一致。
     *
     * <p>English: Mirrors the vanilla pane bake layout: direction buckets hold only the
     * culled narrow caps (edge) while the null bucket holds the strip body faces (pane) and
     * the top caps, matching the real input after PaneCullingModel re-bucketing.
     */
    private static final class VanillaPaneDelegate
            implements BakedModel {
        private final TextureAtlasSprite pane;
        private final TextureAtlasSprite edge;

        private VanillaPaneDelegate(
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
            int uvOffset = format.getOffset(
                    VertexFormatElement.UV0) / 4;
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

    /** 中文：连接 pane 世界桩：y=0 平面返回 pane 状态，其余 AIR。 / English: Connected-pane world stub: the y=0 plane returns the pane state and everything else is AIR. */
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

    private static final class RecordingRenderContext
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
        }

        @Override
        public void popTransform() {
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
        private final List<String> emittedSprites =
                new ArrayList<>();
        private TextureAtlasSprite currentSprite;

        @Override
        public QuadEmitter spriteBake(
                TextureAtlasSprite sprite,
                int bakeFlags) {
            this.currentSprite = sprite;
            return this;
        }

        @Override
        public QuadEmitter emit() {
            if (currentSprite != null) {
                emittedSprites.add(
                        currentSprite.contents()
                                .name()
                                .toString());
                currentSprite = null;
            }
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
        public Vector3f copyNormal(
                int vertexIndex,
                Vector3f target) {
            return target;
        }

        @Override
        public Vector3f faceNormal() {
            return new Vector3f();
        }

        @Override
        public int tag() {
            return 0;
        }

        @Override
        public float posByIndex(
                int vertexIndex,
                int coordinateIndex) {
            return 0.0F;
        }

        @Override
        public Vector3f copyPos(
                int vertexIndex,
                Vector3f target) {
            return target;
        }

        @Override
        public Vector2f copyUv(
                int vertexIndex,
                Vector2f target) {
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
