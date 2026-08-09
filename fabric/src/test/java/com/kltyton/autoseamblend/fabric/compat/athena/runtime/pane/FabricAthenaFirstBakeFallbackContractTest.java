package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.List;
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
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 契约——首次 bake 时 surfaces 尚未 stage（modelDecorationSurfaces 回退 bootstrap
 * 空代次），pane factory 必须从当前烘焙 pane 模型的 quads 推导 body/edge 并仍创建专用
 * pane 模型，绝不能因 SOURCES gate 空而回退通用 wrapper（通用 wrapper 的 per-block
 * 采样产生单块重复边框/染色 pane 墙空洞）。当前实现返回空，本测试应先失败。
 *
 * <p>English: RED contract -- during the first bake surfaces are not staged yet
 * (modelDecorationSurfaces falls back to the empty bootstrap generation), so the pane
 * factory must derive body/edge from the current baked pane model's quads and still create
 * the dedicated pane model instead of falling back through the SOURCES gate to the generic
 * wrapper (whose per-block sampling yields repeated single-block borders and stained-pane
 * wall holes). The current implementation returns empty, so this test fails first.
 */
class FabricAthenaFirstBakeFallbackContractTest {
    private static final long GENERATION = 1L;

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void firstBakeWithoutSurfacesStillCreatesDedicatedPaneModel() {
        BlockState pane = Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState();
        TextureAtlasSprite body = sprite(
                "minecraft:block/green_stained_glass");
        TextureAtlasSprite edge = sprite(
                "minecraft:block/glass_pane_top");
        MinecraftSurfaceCatalog.Snapshot emptySurfaces =
                MinecraftSurfaceCatalog.Snapshot.empty(
                        GENERATION);
        ReloadPublication.Generation generation =
                generation(emptySurfaces);
        EngineQuerySelection selection = selection(
                ConnectionMethod.AUTO,
                PreparedSurfaceMethods.Snapshot.empty(
                        GENERATION));
        BakedModel delegate = new PaneQuadDelegate(
                pane,
                body,
                edge);

        Optional<BakedModel> result =
                FabricAthenaGeneratedPaneModelFactory
                        .create(
                                this::spriteForMaterial,
                                generation,
                                emptySurfaces,
                                pane,
                                delegate,
                                Optional.of(selection));

        assertTrue(
                result.isPresent(),
                "first bake with empty surfaces must still create the "
                        + "dedicated pane model, not fall back to the generic wrapper");
        BakedModel unwrapped = result.orElseThrow() instanceof
                net.fabricmc.fabric.api.renderer.v1.model
                        .ForwardingBakedModel forwarding
                ? forwarding.getWrappedModel()
                : result.orElseThrow();
        assertInstanceOf(
                FabricPaneTopUvModel.class,
                unwrapped,
                "the first-bake pane must be the native pane model");
    }

    @Test
    void firstBakePaneSourcesDeriveBodyAndEdgeFromModel() {
        BlockState pane = Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState();
        TextureAtlasSprite body = sprite(
                "minecraft:block/green_stained_glass");
        TextureAtlasSprite edge = sprite(
                "minecraft:block/glass_pane_top");
        BakedModel delegate = new PaneQuadDelegate(
                pane,
                body,
                edge);

        FabricAthenaGeneratedPaneModelFactory
                .PaneSources sources =
                FabricAthenaGeneratedPaneModelFactory
                        .firstBakePaneSources(
                                pane,
                                delegate,
                                List.of())
                        .orElseThrow();

        assertEquals(
                Direction.NORTH,
                sources.body().direction(),
                "body must be the largest horizontal non-edge face");
        assertEquals(
                "minecraft:block/green_stained_glass",
                sources.body().sprite()
                        .contents()
                        .name()
                        .toString(),
                "body must bind the pane strip sprite");
        assertTrue(
                sources.edge().direction()
                        .getAxis()
                        .isVertical(),
                "edge must come from a vertical cap face");
        assertEquals(
                "minecraft:block/glass_pane_top",
                sources.edge().sprite()
                        .contents()
                        .name()
                        .toString(),
                "edge must bind the pane cap sprite");
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
        // 中文：填充不透明像素，否则 fullyTransparent 判定把 body/edge 全部过滤。
        // English: Fill opaque pixels, otherwise the fullyTransparent test filters out
        // every body/edge candidate.
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

    /** 中文：模拟首次 bake 的 pane 烘焙模型：水平面 body、竖直面 edge。 / English: Simulates the first-bake pane baked model: body on horizontal faces, edge on vertical faces. */
    private static final class PaneQuadDelegate
            implements BakedModel {
        private final BlockState state;
        private final TextureAtlasSprite body;
        private final TextureAtlasSprite edge;

        private PaneQuadDelegate(
                BlockState state,
                TextureAtlasSprite body,
                TextureAtlasSprite edge) {
            this.state = state;
            this.body = body;
            this.edge = edge;
        }

        @Override
        public List<BakedQuad> getQuads(
                BlockState state,
                Direction direction,
                RandomSource random) {
            if (direction == null) {
                return List.of();
            }
            if (direction.getAxis().isVertical()) {
                return List.of(quad(
                        direction,
                        edge,
                        new float[][] {
                            {7.0F, 16.0F, 7.0F},
                            {9.0F, 16.0F, 7.0F},
                            {9.0F, 16.0F, 9.0F},
                            {7.0F, 16.0F, 9.0F}
                        }));
            }
            return List.of(quad(
                    direction,
                    body,
                    strip(direction)));
        }

        private static float[][] strip(
                Direction direction) {
            return switch (direction) {
                case NORTH -> new float[][] {
                    {7.0F, 16.0F, 0.0F},
                    {9.0F, 16.0F, 0.0F},
                    {9.0F, 0.0F, 0.0F},
                    {7.0F, 0.0F, 0.0F}
                };
                case SOUTH -> new float[][] {
                    {7.0F, 16.0F, 16.0F},
                    {9.0F, 16.0F, 16.0F},
                    {9.0F, 0.0F, 16.0F},
                    {7.0F, 0.0F, 16.0F}
                };
                case WEST -> new float[][] {
                    {0.0F, 16.0F, 7.0F},
                    {0.0F, 16.0F, 9.0F},
                    {0.0F, 0.0F, 9.0F},
                    {0.0F, 0.0F, 7.0F}
                };
                case EAST -> new float[][] {
                    {16.0F, 16.0F, 7.0F},
                    {16.0F, 16.0F, 9.0F},
                    {16.0F, 0.0F, 9.0F},
                    {16.0F, 0.0F, 7.0F}
                };
                default -> new float[0][];
            };
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
            return body;
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
