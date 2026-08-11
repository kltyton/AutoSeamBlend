package com.kltyton.autoseamblend.forge.compat.athena.runtime.pane;

import com.kltyton.autoseamblend.forge.testing.ForgeTestBootstrap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.inference.InferenceFacts;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.StateSurface;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import com.kltyton.autoseamblend.texture.mask.TextureFrameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：共享抽取回归测试——Forge 工厂的 paneSources 改由 common
 * AthenaPaneSurfaceRoles 驱动后，既有典型输入（竖直面全为 cap）的 body/edge 输出必须
 * 逐值不变（body=条带精灵、edge=cap 精灵、方向按面积/枚举序稳定），同时异常输入
 * （竖直面混入更大 body 精灵面）必须把 edge 修回 cap 精灵。不改变已验收运行行为。
 *
 * <p>English: Shared-extraction regression test -- after the Forge factory's paneSources
 * is driven by the common AthenaPaneSurfaceRoles, existing typical inputs (vertical faces
 * all caps) must keep value-identical body/edge outputs (body=strip sprite, edge=cap sprite,
 * directions stable by area/order), while the anomalous input (a larger body-sprite vertical
 * face) must fix edge back to the cap sprite. Accepted runtime behavior is unchanged.
 */
class AthenaPaneSurfaceRolesRegressionTest {
    private static final String PANE_TEXTURE =
            "minecraft:block/green_stained_glass";
    private static final String EDGE_TEXTURE =
            "minecraft:block/green_stained_glass_pane_top";

    @BeforeAll
    static void bootstrapRegistries() {
        ForgeTestBootstrap.bootStrap();
    }

    @Test
    void typicalInputsKeepValueIdenticalOutputs() {
        TextureAtlasSprite pane = sprite(PANE_TEXTURE);
        TextureAtlasSprite edge = sprite(EDGE_TEXTURE);
        BlockState state = Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState()
                .setValue(IronBarsBlock.NORTH, true)
                .setValue(IronBarsBlock.EAST, true)
                .setValue(IronBarsBlock.SOUTH, true)
                .setValue(IronBarsBlock.WEST, true);

        // 中文：十字典型输入：四条 strip 面（pane）+ 上下 cap 面（edge），
        // 与已验收 Forge 表面一致（竖直面全为 cap）。
        // English: Typical cross input: four strip faces (pane) plus top/bottom caps (edge),
        // identical to the accepted Forge surfaces (vertical faces all caps).
        StateSurface surface = surface(
                state,
                Map.of(
                        Direction.NORTH,
                        List.of(face(
                                Direction.NORTH,
                                pane,
                                0.4375F)),
                        Direction.EAST,
                        List.of(face(
                                Direction.EAST,
                                pane,
                                0.4375F)),
                        Direction.SOUTH,
                        List.of(face(
                                Direction.SOUTH,
                                pane,
                                0.4375F)),
                        Direction.WEST,
                        List.of(face(
                                Direction.WEST,
                                pane,
                                0.4375F)),
                        Direction.DOWN,
                        List.of(face(
                                Direction.DOWN,
                                edge,
                                0.015625F)),
                        Direction.UP,
                        List.of(face(
                                Direction.UP,
                                edge,
                                0.015625F))));

        AthenaGeneratedPaneModelFactory.PaneSources sources =
                AthenaGeneratedPaneModelFactory
                        .paneSources(surface)
                        .orElseThrow();

        assertEquals(
                PANE_TEXTURE,
                sources.body().sprite()
                        .contents()
                        .name()
                        .toString(),
                "body sprite must stay the pane strip texture");
        assertEquals(
                Direction.NORTH,
                sources.body().direction(),
                "body direction must stay the stable largest-face pick");
        assertEquals(
                EDGE_TEXTURE,
                sources.edge().sprite()
                        .contents()
                        .name()
                        .toString(),
                "edge sprite must stay the cap texture");
        assertEquals(
                Direction.DOWN,
                sources.edge().direction(),
                "edge direction must stay the stable largest-face pick");
    }

    @Test
    void singleArmAndIsolatedTypicalInputsKeepValueIdenticalOutputs() {
        TextureAtlasSprite pane = sprite(PANE_TEXTURE);
        TextureAtlasSprite edge = sprite(EDGE_TEXTURE);
        BlockState singleArm = Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState()
                .setValue(IronBarsBlock.NORTH, true);
        StateSurface singleArmSurface = surface(
                singleArm,
                Map.of(
                        Direction.NORTH,
                        List.of(face(
                                Direction.NORTH,
                                edge,
                                0.125F)),
                        Direction.WEST,
                        List.of(face(
                                Direction.WEST,
                                pane,
                                0.4375F)),
                        Direction.EAST,
                        List.of(face(
                                Direction.EAST,
                                pane,
                                0.4375F)),
                        Direction.DOWN,
                        List.of(face(
                                Direction.DOWN,
                                edge,
                                0.015625F)),
                        Direction.UP,
                        List.of(face(
                                Direction.UP,
                                edge,
                                0.015625F))));

        AthenaGeneratedPaneModelFactory.PaneSources singleArmSources =
                AthenaGeneratedPaneModelFactory
                        .paneSources(singleArmSurface)
                        .orElseThrow();
        assertEquals(
                PANE_TEXTURE,
                singleArmSources.body().sprite()
                        .contents()
                        .name()
                        .toString(),
                "single-arm body must stay the pane strip texture");
        assertEquals(
                Direction.WEST,
                singleArmSources.body().direction(),
                "single-arm body must stay the stable largest-face pick");
        assertEquals(
                EDGE_TEXTURE,
                singleArmSources.edge().sprite()
                        .contents()
                        .name()
                        .toString(),
                "single-arm edge must stay the cap texture");

        BlockState isolated = Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState();
        StateSurface isolatedSurface = surface(
                isolated,
                Map.of(
                        Direction.NORTH,
                        List.of(face(
                                Direction.NORTH,
                                pane,
                                0.125F)),
                        Direction.EAST,
                        List.of(face(
                                Direction.EAST,
                                pane,
                                0.125F)),
                        Direction.SOUTH,
                        List.of(face(
                                Direction.SOUTH,
                                pane,
                                0.125F)),
                        Direction.WEST,
                        List.of(face(
                                Direction.WEST,
                                pane,
                                0.125F)),
                        Direction.DOWN,
                        List.of(face(
                                Direction.DOWN,
                                edge,
                                0.015625F)),
                        Direction.UP,
                        List.of(face(
                                Direction.UP,
                                edge,
                                0.015625F))));
        AthenaGeneratedPaneModelFactory.PaneSources isolatedSources =
                AthenaGeneratedPaneModelFactory
                        .paneSources(isolatedSurface)
                        .orElseThrow();
        assertEquals(
                PANE_TEXTURE,
                isolatedSources.body().sprite()
                        .contents()
                        .name()
                        .toString(),
                "isolated body must stay the pane strip texture");
        assertEquals(
                EDGE_TEXTURE,
                isolatedSources.edge().sprite()
                        .contents()
                        .name()
                        .toString(),
                "isolated edge must stay the cap texture");
    }

    @Test
    void anomalousBodySpriteVerticalFaceFixesEdgeToCap() {
        TextureAtlasSprite pane = sprite(PANE_TEXTURE);
        TextureAtlasSprite edge = sprite(EDGE_TEXTURE);
        BlockState state = Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState();
        StateSurface surface = surface(
                state,
                Map.of(
                        Direction.NORTH,
                        List.of(face(
                                Direction.NORTH,
                                pane,
                                1.0F)),
                        Direction.DOWN,
                        List.of(
                                face(
                                        Direction.DOWN,
                                        pane,
                                        0.0625F),
                                face(
                                        Direction.DOWN,
                                        edge,
                                        0.015625F)),
                        Direction.UP,
                        List.of(face(
                                Direction.UP,
                                edge,
                                0.015625F))));

        AthenaGeneratedPaneModelFactory.PaneSources sources =
                AthenaGeneratedPaneModelFactory
                        .paneSources(surface)
                        .orElseThrow();

        assertEquals(
                EDGE_TEXTURE,
                sources.edge().sprite()
                        .contents()
                        .name()
                        .toString(),
                "edge must prefer the cap sprite over a larger "
                        + "body-sprite vertical face");
        assertEquals(
                PANE_TEXTURE,
                sources.body().sprite()
                        .contents()
                        .name()
                        .toString(),
                "body must keep the pane strip sprite");
    }

    private static StateSurface surface(
            BlockState state,
            Map<Direction, List<FaceSurface>> faces) {
        return new StateSurface(state, faces);
    }

    private static FaceSurface face(
            Direction direction,
            TextureAtlasSprite sprite,
            float area) {
        return new FaceSurface(
                direction,
                sprite,
                -1,
                false,
                false,
                representativeQuad(
                        direction,
                        sprite,
                        area),
                InferenceFacts.unknown(),
                ConnectionMethod.NONE,
                OverlayCutoutProfile.thinUniform(),
                new TextureFrameProfile(
                        0.0F,
                        0.0F,
                        0.0F,
                        0.0F));
    }

    private static BakedQuad representativeQuad(
            Direction direction,
            TextureAtlasSprite sprite,
            float area) {
        VertexFormat format = DefaultVertexFormat.BLOCK;
        int stride = format.getVertexSize() / 4;
        int uvOffset = 4 /* UV0 int offset in DefaultVertexFormat.BLOCK */;
        int[] vertices = new int[stride * 4];
        float width = (float) Math.sqrt(area);
        float height = area / width;
        float[][] positions = switch (direction) {
            case NORTH -> new float[][] {
                {0.0F, height, 0.0F},
                {width, height, 0.0F},
                {width, 0.0F, 0.0F},
                {0.0F, 0.0F, 0.0F}
            };
            case DOWN -> new float[][] {
                {0.0F, 0.0F, 0.0F},
                {width, 0.0F, 0.0F},
                {width, 0.0F, height},
                {0.0F, 0.0F, height}
            };
            case UP -> new float[][] {
                {0.0F, 1.0F, 0.0F},
                {width, 1.0F, 0.0F},
                {width, 1.0F, height},
                {0.0F, 1.0F, height}
            };
            default -> new float[][] {
                {0.0F, height, 0.0F},
                {0.0F, height, width},
                {0.0F, 0.0F, width},
                {0.0F, 0.0F, 0.0F}
            };
        };
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
                direction,
                sprite,
                false);
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
