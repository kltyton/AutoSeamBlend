package com.kltyton.autoseamblend.fabric.compat.athena.runtime.pane;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
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
 * 中文：RED 契约——first-bake body/edge 选择必须覆盖全部连接属性组合且稳定：body 恒为
 * 条带（pane）纹理、edge 恒为顶盖（pane_top）纹理，绝不把 body 精灵当 edge。已观测真实
 * 运行（diag2 批）孤立全 false 状态 `edge=down,<色>_stained_glass`（body 精灵）且
 * `body=north,...,area=1.0`——edge 选择按“最大竖直面”把 body 精灵的竖直面当 edge。
 * 本测试用合成 StateSurface 编码各状态组合（含该异常组合），纯数据驱动、无方块白名单。
 *
 * <p>English: RED contract -- first-bake body/edge selection must be stable across all
 * connection property combinations: body is always the strip (pane) texture and edge is
 * always the cap (pane_top) texture, never binding the body sprite as edge. The diag2 run
 * observed the isolated all-false state with `edge=down,<color>_stained_glass` (the body
 * sprite) and `body=north,...,area=1.0` -- edge selection by "largest vertical face" picked
 * a body-sprite vertical face as edge. This test encodes every state combination (including
 * that anomaly) with synthetic StateSurfaces, purely data-driven and whitelist-free.
 */
class FabricPaneSourcesEdgeBindingContractTest {
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
    void edgeSelectionPrefersCapSpriteOverLargerBodySpriteVerticalFace() {
        // 中文：编码真实 diag2 批孤立状态观测组合：竖直面同时存在 body 精灵（更大）与
        // cap 精灵（更小），edge 必须选 cap 精灵，绝不选 body 精灵。
        // English: Encodes the observed diag2 isolated-state composition: the vertical axis
        // holds both a larger body-sprite face and a smaller cap-sprite face; edge must pick
        // the cap sprite, never the body sprite.
        BlockState pane = Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState();
        TextureAtlasSprite paneSprite =
                sprite(PANE_TEXTURE);
        TextureAtlasSprite edgeSprite =
                sprite(EDGE_TEXTURE);
        StateSurface surface = new StateSurface(
                pane,
                Map.of(
                        Direction.NORTH,
                        List.of(face(
                                Direction.NORTH,
                                paneSprite,
                                1.0F)),
                        Direction.DOWN,
                        List.of(face(
                                        Direction.DOWN,
                                        paneSprite,
                                        0.0625F),
                                face(
                                        Direction.DOWN,
                                        edgeSprite,
                                        0.015625F)),
                        Direction.UP,
                        List.of(face(
                                Direction.UP,
                                edgeSprite,
                                0.015625F))));

        FabricAthenaGeneratedPaneModelFactory
                .PaneSources sources =
                FabricAthenaGeneratedPaneModelFactory
                        .paneSources(surface)
                        .orElseThrow();

        assertEquals(
                EDGE_TEXTURE,
                sources.edge().sprite()
                        .contents()
                        .name()
                        .toString(),
                "edge must bind the cap sprite even when a larger "
                        + "body-sprite vertical face exists");
        assertEquals(
                PANE_TEXTURE,
                sources.body().sprite()
                        .contents()
                        .name()
                        .toString(),
                "body must keep the pane strip sprite");
    }

    @Test
    void edgeAndBodyStayStableAcrossConnectionStateTypes() {
        // 中文：全 false / 单臂 / 直线 / 转角 / 十字，body=edge 组合必须稳定。
        // English: all-false / single-arm / straight / corner / cross must keep a stable
        // body=edge sprite pair.
        BlockState pane = Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState();
        TextureAtlasSprite paneSprite =
                sprite(PANE_TEXTURE);
        TextureAtlasSprite edgeSprite =
                sprite(EDGE_TEXTURE);
        for (boolean north : new boolean[] {
                false, true}) {
            for (boolean east : new boolean[] {
                    false, true}) {
                for (boolean south : new boolean[] {
                        false, true}) {
                    for (boolean west : new boolean[] {
                            false, true}) {
                        assertStable(
                                pane,
                                paneSprite,
                                edgeSprite,
                                north,
                                east,
                                south,
                                west);
                    }
                }
            }
        }
    }

    private static void assertStable(
            BlockState pane,
            TextureAtlasSprite paneSprite,
            TextureAtlasSprite edgeSprite,
            boolean north,
            boolean east,
            boolean south,
            boolean west) {
        BlockState state = pane
                .setValue(IronBarsBlock.NORTH, north)
                .setValue(IronBarsBlock.EAST, east)
                .setValue(IronBarsBlock.SOUTH, south)
                .setValue(IronBarsBlock.WEST, west);
        java.util.EnumMap<Direction, List<FaceSurface>> faces =
                new java.util.EnumMap<>(
                        Direction.class);
        // 中文：按原版 part 组合镜像：north/south 臂=edge cap + west/east pane 大面，
        // east/west 臂=edge cap + north/south pane 大面（side y90 旋转）。
        // English: Mirrors the vanilla part composition: north/south arms add an edge cap
        // plus west/east pane faces; east/west arms add an edge cap plus north/south pane
        // faces (side rotated by 90).
        if (north) {
            faces.computeIfAbsent(
                            Direction.NORTH,
                            ignored ->
                                    new java.util.ArrayList<>())
                    .add(face(
                            Direction.NORTH,
                            edgeSprite,
                            0.125F));
            faces.computeIfAbsent(
                            Direction.WEST,
                            ignored ->
                                    new java.util.ArrayList<>())
                    .add(face(
                            Direction.WEST,
                            paneSprite,
                            0.4375F));
            faces.computeIfAbsent(
                            Direction.EAST,
                            ignored ->
                                    new java.util.ArrayList<>())
                    .add(face(
                            Direction.EAST,
                            paneSprite,
                            0.4375F));
        }
        if (south) {
            faces.computeIfAbsent(
                            Direction.SOUTH,
                            ignored ->
                                    new java.util.ArrayList<>())
                    .add(face(
                            Direction.SOUTH,
                            edgeSprite,
                            0.125F));
            faces.computeIfAbsent(
                            Direction.WEST,
                            ignored ->
                                    new java.util.ArrayList<>())
                    .add(face(
                            Direction.WEST,
                            paneSprite,
                            0.4375F));
            faces.computeIfAbsent(
                            Direction.EAST,
                            ignored ->
                                    new java.util.ArrayList<>())
                    .add(face(
                            Direction.EAST,
                            paneSprite,
                            0.4375F));
        }
        if (east) {
            faces.computeIfAbsent(
                            Direction.EAST,
                            ignored ->
                                    new java.util.ArrayList<>())
                    .add(face(
                            Direction.EAST,
                            edgeSprite,
                            0.125F));
            faces.computeIfAbsent(
                            Direction.NORTH,
                            ignored ->
                                    new java.util.ArrayList<>())
                    .add(face(
                            Direction.NORTH,
                            paneSprite,
                            0.4375F));
            faces.computeIfAbsent(
                            Direction.SOUTH,
                            ignored ->
                                    new java.util.ArrayList<>())
                    .add(face(
                            Direction.SOUTH,
                            paneSprite,
                            0.4375F));
        }
        if (west) {
            faces.computeIfAbsent(
                            Direction.WEST,
                            ignored ->
                                    new java.util.ArrayList<>())
                    .add(face(
                            Direction.WEST,
                            edgeSprite,
                            0.125F));
            faces.computeIfAbsent(
                            Direction.NORTH,
                            ignored ->
                                    new java.util.ArrayList<>())
                    .add(face(
                            Direction.NORTH,
                            paneSprite,
                            0.4375F));
            faces.computeIfAbsent(
                            Direction.SOUTH,
                            ignored ->
                                    new java.util.ArrayList<>())
                    .add(face(
                            Direction.SOUTH,
                            paneSprite,
                            0.4375F));
        }
        faces.put(
                Direction.DOWN,
                List.of(face(
                        Direction.DOWN,
                        edgeSprite,
                        0.015625F)));
        faces.put(
                Direction.UP,
                List.of(face(
                        Direction.UP,
                        edgeSprite,
                        0.015625F)));
        if (!north && !east && !south && !west) {
            faces.put(
                    Direction.NORTH,
                    List.of(face(
                            Direction.NORTH,
                            paneSprite,
                            0.125F)));
            faces.put(
                    Direction.EAST,
                    List.of(face(
                            Direction.EAST,
                            paneSprite,
                            0.125F)));
            faces.put(
                    Direction.SOUTH,
                    List.of(face(
                            Direction.SOUTH,
                            paneSprite,
                            0.125F)));
            faces.put(
                    Direction.WEST,
                    List.of(face(
                            Direction.WEST,
                            paneSprite,
                            0.125F)));
        }
        StateSurface surface = new StateSurface(
                state,
                faces);

        FabricAthenaGeneratedPaneModelFactory
                .PaneSources sources =
                FabricAthenaGeneratedPaneModelFactory
                        .paneSources(surface)
                        .orElseThrow();

        assertEquals(
                PANE_TEXTURE,
                sources.body().sprite()
                        .contents()
                        .name()
                        .toString(),
                "body must be the pane strip sprite for " + state);
        assertEquals(
                EDGE_TEXTURE,
                sources.edge().sprite()
                        .contents()
                        .name()
                        .toString(),
                "edge must be the cap sprite for " + state);
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
