package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.engine.EngineDescriptor;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import com.kltyton.autoseamblend.engine.routing.ModelOwnershipRuntime;
import com.kltyton.autoseamblend.engine.routing.NativeCaptureHealth;
import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.engine.routing.query.EngineRouteProvenance;
import com.kltyton.autoseamblend.engine.routing.query.EngineRouteSelection;
import com.kltyton.autoseamblend.inference.InferenceDecision;
import com.kltyton.autoseamblend.inference.InferenceFacts;
import com.kltyton.autoseamblend.reload.rule.ManagedRuleSnapshot;
import com.kltyton.autoseamblend.reload.rule.NativeRuleSnapshot;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.StateSurface;
import com.kltyton.autoseamblend.runtime.surface.PreparedSurfaceMethods;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.atlas.GeneratedSpriteSetCatalog;
import com.kltyton.autoseamblend.texture.atlas.ResolvedSpriteCatalog;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import com.kltyton.autoseamblend.texture.mask.TextureFrameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
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
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：Fabric 玻璃板工厂的 RED 契约。已验收 1.21.1 NeoForge
 * AthenaGeneratedPaneModelFactory 语义是：IronBars 门控、ATHENA+runsAutoBlend 路由、
 * pane 专用 body/edge 表面、AthenaPaneTilePlan.resolveRuntimeMethod（AUTO+NONE 降级
 * CTM）、七个 Role nativeIndex 材质、缺槽守卫、Fabric AthenaBakedModel 原生 pane 模型。
 * 当前 Fabric 侧完全缺少该路径，pane 候选被通用 FabricAthenaConnectedBlockStateModel
 * 包装，因此这些契约必须先红。
 *
 * <p>English: RED contracts for the Fabric pane factory. The accepted 1.21.1 NeoForge
 * AthenaGeneratedPaneModelFactory semantics are: IronBars gate, ATHENA+runsAutoBlend
 * routing, pane-specific body/edge surfaces, AthenaPaneTilePlan.resolveRuntimeMethod
 * (AUTO+NONE degrades to CTM), seven Role nativeIndex materials, the missing-slot guard,
 * and the Fabric AthenaBakedModel native pane model. The Fabric side currently lacks the
 * whole path and wraps pane candidates with the generic
 * FabricAthenaConnectedBlockStateModel, so these contracts must fail first.
 */
class FabricAthenaGeneratedPaneModelFactoryContractTest {
    private static final long GENERATION = 1L;

    /**
     * 中文：断言 factory 产物解包外层 PaneCullingModel（97d1478 wrapper 顺序）后是
     * FabricPaneTopUvModel，且不是 generic 连接模型。
     *
     * <p>English: Asserts the factory result unwraps the outer PaneCullingModel (the
     * 97d1478 wrapper order) into a FabricPaneTopUvModel and is never the generic
     * connected model.
     */
    private static void assertUnwrapsToPaneTopUv(
            BakedModel result,
            String message) {
        BakedModel unwrapped = result instanceof
                ForwardingBakedModel forwarding
                ? forwarding.getWrappedModel()
                : result;
        assertInstanceOf(
                FabricPaneTopUvModel.class,
                unwrapped,
                message);
    }
    private static final String BODY_SPRITE = "minecraft:block/glass_pane";
    private static final String EDGE_SPRITE = "minecraft:block/glass_pane_top";

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void paneCandidateGetsDedicatedPaneModelNotGenericWrapper() {
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        TextureAtlasSprite body = sprite(BODY_SPRITE);
        TextureAtlasSprite edge = sprite(EDGE_SPRITE);
        MinecraftSurfaceCatalog.Snapshot surfaces =
                paneSurfaces(pane, body, edge);
        ReloadPublication.Generation generation =
                generation(surfaces);
        EngineQuerySelection selection = selection(
                ConnectionMethod.CTM,
                PreparedSurfaceMethods.Snapshot.empty(
                        GENERATION));

        Optional<BakedModel> result =
                FabricAthenaGeneratedPaneModelFactory.create(
                        this::spriteForMaterial,
                        generation,
                        surfaces,
                        pane,
                        new DelegateModel(),
                        Optional.of(selection));

        assertTrue(
                result.isPresent(),
                "an ATHENA CTM pane candidate must produce the dedicated pane model");
        assertUnwrapsToPaneTopUv(
                result.orElseThrow(),
                "pane candidate must not be wrapped by the generic connected model");
    }

    @Test
    void autoWithNoneInferenceDegradesToCtm() {
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        TextureAtlasSprite body = sprite(BODY_SPRITE);
        TextureAtlasSprite edge = sprite(EDGE_SPRITE);
        MinecraftSurfaceCatalog.Snapshot surfaces =
                paneSurfaces(pane, body, edge);
        ReloadPublication.Generation generation =
                generation(surfaces);
        EngineQuerySelection selection = selection(
                ConnectionMethod.AUTO,
                preparedWithResolved(
                        pane,
                        ConnectionMethod.NONE));

        Optional<BakedModel> result =
                FabricAthenaGeneratedPaneModelFactory.create(
                        this::spriteForMaterial,
                        generation,
                        surfaces,
                        pane,
                        new DelegateModel(),
                        Optional.of(selection));

        assertTrue(
                result.isPresent(),
                "AUTO with NONE inference must degrade to CTM for panes "
                        + "(AthenaPaneTilePlan.resolveRuntimeMethod)");
        assertUnwrapsToPaneTopUv(
                result.orElseThrow(),
                "the AUTO/NONE pane must still enter the native pane model");
    }

    @Test
    void autoWithCtmInferenceStaysCtm() {
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        TextureAtlasSprite body = sprite(BODY_SPRITE);
        TextureAtlasSprite edge = sprite(EDGE_SPRITE);
        MinecraftSurfaceCatalog.Snapshot surfaces =
                paneSurfaces(pane, body, edge);
        ReloadPublication.Generation generation =
                generation(surfaces);
        EngineQuerySelection selection = selection(
                ConnectionMethod.AUTO,
                preparedWithResolved(
                        pane,
                        ConnectionMethod.CTM));

        Optional<BakedModel> result =
                FabricAthenaGeneratedPaneModelFactory.create(
                        this::spriteForMaterial,
                        generation,
                        surfaces,
                        pane,
                        new DelegateModel(),
                        Optional.of(selection));

        assertTrue(
                result.isPresent(),
                "AUTO with CTM inference must keep the native pane model");
    }

    @Test
    void explicitNoneAbortsPaneModel() {
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        TextureAtlasSprite body = sprite(BODY_SPRITE);
        TextureAtlasSprite edge = sprite(EDGE_SPRITE);
        MinecraftSurfaceCatalog.Snapshot surfaces =
                paneSurfaces(pane, body, edge);
        ReloadPublication.Generation generation =
                generation(surfaces);
        EngineQuerySelection selection = selection(
                ConnectionMethod.NONE,
                PreparedSurfaceMethods.Snapshot.empty(
                        GENERATION));

        Optional<BakedModel> result =
                FabricAthenaGeneratedPaneModelFactory.create(
                        this::spriteForMaterial,
                        generation,
                        surfaces,
                        pane,
                        new DelegateModel(),
                        Optional.of(selection));

        assertTrue(
                result.isEmpty(),
                "an explicit NONE method must never be rewritten into the pane model");
    }

    @Test
    void nonIronBarsStateAbortsPaneModel() {
        BlockState stone = Blocks.STONE.defaultBlockState();
        TextureAtlasSprite body = sprite(BODY_SPRITE);
        TextureAtlasSprite edge = sprite(EDGE_SPRITE);
        MinecraftSurfaceCatalog.Snapshot surfaces =
                paneSurfaces(stone, body, edge);
        ReloadPublication.Generation generation =
                generation(surfaces);
        EngineQuerySelection selection = selection(
                ConnectionMethod.CTM,
                PreparedSurfaceMethods.Snapshot.empty(
                        GENERATION));

        Optional<BakedModel> result =
                FabricAthenaGeneratedPaneModelFactory.create(
                        this::spriteForMaterial,
                        generation,
                        surfaces,
                        stone,
                        new DelegateModel(),
                        Optional.of(selection));

        assertTrue(
                result.isEmpty(),
                "non-IronBars states must never enter the pane factory");
    }

    @Test
    void missingRoleSpriteAbortsPaneModel() {
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        TextureAtlasSprite body = sprite(BODY_SPRITE);
        TextureAtlasSprite edge = sprite(EDGE_SPRITE);
        MinecraftSurfaceCatalog.Snapshot surfaces =
                paneSurfaces(pane, body, edge);
        ReloadPublication.Generation generation =
                generation(surfaces);
        EngineQuerySelection selection = selection(
                ConnectionMethod.CTM,
                PreparedSurfaceMethods.Snapshot.empty(
                        GENERATION));

        Optional<BakedModel> result =
                FabricAthenaGeneratedPaneModelFactory.create(
                        material -> missingSprite(material),
                        generation,
                        surfaces,
                        pane,
                        new DelegateModel(),
                        Optional.of(selection));

        assertTrue(
                result.isEmpty(),
                "any missing generated role sprite must abort the pane model");
    }

    @Test
    void paneSourcesSelectLargestNonTransparentEdgeAndBody() {
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        TextureAtlasSprite body = sprite(BODY_SPRITE);
        TextureAtlasSprite edge = sprite(EDGE_SPRITE);
        StateSurface stateSurface = new StateSurface(
                pane,
                Map.of(
                        Direction.UP,
                        List.of(face(
                                Direction.UP,
                                edge,
                                fullFaceQuad(
                                        Direction.UP,
                                        edge))),
                        Direction.NORTH,
                        List.of(face(
                                Direction.NORTH,
                                body,
                                fullFaceQuad(
                                        Direction.NORTH,
                                        body))),
                        Direction.SOUTH,
                        List.of(face(
                                Direction.SOUTH,
                                body,
                                fullFaceQuad(
                                        Direction.SOUTH,
                                        body)))));

        FabricAthenaGeneratedPaneModelFactory.PaneSources
                sources = FabricAthenaGeneratedPaneModelFactory
                        .paneSources(stateSurface)
                        .orElseThrow();

        assertEquals(
                Direction.UP,
                sources.edge().direction(),
                "edge must be the largest non-transparent vertical face");
        assertEquals(
                Direction.NORTH,
                sources.body().direction(),
                "body must be the largest non-edge horizontal face");
    }

    @Test
    void paneSourcesSkipFullyTransparentFaces() {
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        TextureAtlasSprite body = sprite(BODY_SPRITE);
        TextureAtlasSprite edge = sprite(EDGE_SPRITE);
        FaceSurface transparentUp = new FaceSurface(
                Direction.UP,
                edge,
                -1,
                true,
                true,
                fullFaceQuad(Direction.UP, edge),
                InferenceFacts.unknown(),
                ConnectionMethod.CTM,
                OverlayCutoutProfile.thinUniform(),
                new TextureFrameProfile(0.0F, 0.0F, 0.0F, 0.0F));
        FaceSurface smallDown = new FaceSurface(
                Direction.DOWN,
                edge,
                -1,
                true,
                false,
                smallQuad(Direction.DOWN, edge),
                InferenceFacts.unknown(),
                ConnectionMethod.CTM,
                OverlayCutoutProfile.thinUniform(),
                new TextureFrameProfile(0.0F, 0.0F, 0.0F, 0.0F));
        StateSurface stateSurface = new StateSurface(
                pane,
                Map.of(
                        Direction.UP,
                        List.of(transparentUp),
                        Direction.DOWN,
                        List.of(smallDown),
                        Direction.NORTH,
                        List.of(face(
                                Direction.NORTH,
                                body,
                                fullFaceQuad(
                                        Direction.NORTH,
                                        body)))));

        FabricAthenaGeneratedPaneModelFactory.PaneSources
                sources = FabricAthenaGeneratedPaneModelFactory
                        .paneSources(stateSurface)
                        .orElseThrow();

        assertEquals(
                Direction.DOWN,
                sources.edge().direction(),
                "fully transparent vertical faces must be skipped");
        assertEquals(
                Direction.NORTH,
                sources.body().direction(),
                "body selection must ignore the transparent vertical face");
    }

    private static MinecraftSurfaceCatalog.Snapshot paneSurfaces(
            BlockState state,
            TextureAtlasSprite body,
            TextureAtlasSprite edge) {
        Map<BlockState, StateSurface> states = Map.of(
                state,
                new StateSurface(
                        state,
                        Map.of(
                                Direction.UP,
                                List.of(face(
                                        Direction.UP,
                                        edge,
                                        fullFaceQuad(
                                                Direction.UP,
                                                edge))),
                                Direction.NORTH,
                                List.of(face(
                                        Direction.NORTH,
                                        body,
                                        fullFaceQuad(
                                                Direction.NORTH,
                                                body))),
                                Direction.SOUTH,
                                List.of(face(
                                        Direction.SOUTH,
                                        body,
                                        fullFaceQuad(
                                                Direction.SOUTH,
                                                body))))));
        return new MinecraftSurfaceCatalog.Snapshot(
                GENERATION,
                states,
                List.of());
    }

    private static FaceSurface face(
            Direction direction,
            TextureAtlasSprite sprite,
            BakedQuad representative) {
        return new FaceSurface(
                direction,
                sprite,
                -1,
                true,
                false,
                representative,
                InferenceFacts.unknown(),
                ConnectionMethod.CTM,
                OverlayCutoutProfile.thinUniform(),
                new TextureFrameProfile(0.0F, 0.0F, 0.0F, 0.0F));
    }

    private static PreparedSurfaceMethods.Snapshot
            preparedWithResolved(
                    BlockState state,
                    ConnectionMethod resolved) {
        InferenceDecision decision =
                new InferenceDecision(
                        ConnectionMethod.AUTO,
                        Optional.of(resolved),
                        false,
                        InferenceDecision.Confidence.CERTAIN,
                        List.of(),
                        List.of());
        PreparedSurfaceMethods.PreparedMethod method =
                new PreparedSurfaceMethods.PreparedMethod(
                        InferenceFacts.unknown(),
                        decision);
        PreparedSurfaceMethods.Key key =
                new PreparedSurfaceMethods.Key(
                        state,
                        Direction.NORTH,
                        new ResourceLocation(BODY_SPRITE));
        return new PreparedSurfaceMethods.Snapshot(
                GENERATION,
                "test-reload",
                Map.of(key, method));
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

    private TextureAtlasSprite spriteForMaterial(
            Material material) {
        return sprite(
                material.texture().toString());
    }

    private static TextureAtlasSprite missingSprite(
            Material material) {
        return sprite(
                "minecraft:missingno");
    }

    private static TextureAtlasSprite sprite(
            String name) {
        NativeImage image =
                new NativeImage(16, 16, false);
        SpriteContents contents = new SpriteContents(
                new ResourceLocation(name),
                new FrameSize(16, 16),
                image,
                AnimationMetadataSection.EMPTY);
        return new TestSprite(
                TextureAtlas.LOCATION_BLOCKS,
                contents);
    }

    private static BakedQuad fullFaceQuad(
            Direction face,
            TextureAtlasSprite sprite) {
        float[][] positions = switch (face) {
            case UP -> new float[][] {
                {0.0F, 16.0F, 0.0F},
                {16.0F, 16.0F, 0.0F},
                {16.0F, 16.0F, 16.0F},
                {0.0F, 16.0F, 16.0F}
            };
            case DOWN -> new float[][] {
                {0.0F, 0.0F, 0.0F},
                {16.0F, 0.0F, 0.0F},
                {16.0F, 0.0F, 16.0F},
                {0.0F, 0.0F, 16.0F}
            };
            case NORTH -> new float[][] {
                {0.0F, 16.0F, 0.0F},
                {16.0F, 16.0F, 0.0F},
                {16.0F, 0.0F, 0.0F},
                {0.0F, 0.0F, 0.0F}
            };
            case SOUTH -> new float[][] {
                {0.0F, 16.0F, 16.0F},
                {16.0F, 16.0F, 16.0F},
                {16.0F, 0.0F, 16.0F},
                {0.0F, 0.0F, 16.0F}
            };
            case WEST -> new float[][] {
                {0.0F, 16.0F, 0.0F},
                {0.0F, 16.0F, 16.0F},
                {0.0F, 0.0F, 16.0F},
                {0.0F, 0.0F, 0.0F}
            };
            case EAST -> new float[][] {
                {16.0F, 16.0F, 0.0F},
                {16.0F, 16.0F, 16.0F},
                {16.0F, 0.0F, 16.0F},
                {16.0F, 0.0F, 0.0F}
            };
        };
        return bakedQuad(face, sprite, positions);
    }

    private static BakedQuad smallQuad(
            Direction face,
            TextureAtlasSprite sprite) {
        return bakedQuad(
                face,
                sprite,
                new float[][] {
                    {0.0F, 8.0F, 0.0F},
                    {8.0F, 8.0F, 0.0F},
                    {8.0F, 8.0F, 8.0F},
                    {0.0F, 8.0F, 8.0F}
                });
    }

    private static BakedQuad bakedQuad(
            Direction face,
            TextureAtlasSprite sprite,
            float[][] positions) {
        VertexFormat format = DefaultVertexFormat.BLOCK;
        int stride = format.getVertexSize() / 4;
        int uvOffset = 4 /* UV0 int offset in DefaultVertexFormat.BLOCK */;
        int[] vertices = new int[stride * 4];
        for (int vertex = 0; vertex < 4; vertex++) {
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

    private static final class TestSprite
            extends TextureAtlasSprite {
        private TestSprite(
                ResourceLocation atlasLocation,
                SpriteContents contents) {
            super(
                    atlasLocation,
                    contents,
                    2048,
                    2048,
                    0,
                    0);
        }
    }

    /** 中文：最小 BakedModel 委托；pane 工厂只检查它是否为 AthenaBakedModel。 / English: Minimal BakedModel delegate; the pane factory only checks whether it is an AthenaBakedModel. */
    private static final class DelegateModel
            implements BakedModel {
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
}
