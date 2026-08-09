package com.kltyton.autoseamblend.compat.athena.runtime.pane;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.compat.athena.runtime.pane.AthenaPaneSurfaceRoles.Roles;
import com.kltyton.autoseamblend.inference.InferenceFacts;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.StateSurface;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import com.kltyton.autoseamblend.texture.mask.TextureFrameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：common AthenaPaneSurfaceRoles 的 Loader 无关合同测试，由 1.21.1 08d4bc5 已验收
 * 的 NeoForge 回归测试与 Fabric sibling 回退契约测试的 fixture 移植并适配 26.1.2
 * BakedQuad/Identifier API。锁定语义：body=水平轴主面（最大面积条带面）；edge=竖直轴
 * 优先精灵不同于 body 候选的 cap 面，仅当全部竖直面共享 body 精灵时才稳定回退最大竖直
 * 面；body 再按非 edge 精灵过滤。典型输入（竖直面全为 cap）与既有 NeoForge 选择逐值
 * 一致；当前 state edge 退化等于 body 时从同一 Block 的 sibling 借用稳定 cap，无
 * sibling 时安全回退（present、edge 保持退化、不崩溃）；任一轴缺失返回 empty。
 *
 * <p>English: Loader-neutral contract tests for the common AthenaPaneSurfaceRoles, with
 * fixtures ported from the accepted 1.21.1 08d4bc5 NeoForge regression test and Fabric
 * sibling-fallback contract test, adapted to the 26.1.2 BakedQuad/Identifier APIs. Locks
 * the semantics: body is the largest horizontal strip face; edge prefers the vertical cap
 * face whose sprite differs from the body candidate, stably falling back to the largest
 * vertical face only when every vertical face shares the body sprite; body then keeps the
 * non-edge-sprite filter. Typical inputs (vertical faces all caps) stay value-identical to
 * the accepted NeoForge selection; when the current state's edge degenerates to body, a
 * stable cap is borrowed from a sibling of the same Block, and without a sibling the result
 * safely falls back (present, edge stays degenerate, never crashes); missing either axis
 * yields empty.
 */
class AthenaPaneSurfaceRolesContractTest {
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
    void typicalCrossInputsKeepValueIdenticalOutputs() {
        TextureAtlasSprite pane = sprite(PANE_TEXTURE);
        TextureAtlasSprite edge = sprite(EDGE_TEXTURE);
        BlockState state = Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState()
                .setValue(CrossCollisionBlock.NORTH, true)
                .setValue(CrossCollisionBlock.EAST, true)
                .setValue(CrossCollisionBlock.SOUTH, true)
                .setValue(CrossCollisionBlock.WEST, true);

        // 中文：十字典型输入：四条 strip 面（pane）+ 上下 cap 面（edge），
        // 与已验收 NeoForge 表面一致（竖直面全为 cap）。
        // English: Typical cross input: four strip faces (pane) plus top/bottom caps (edge),
        // identical to the accepted NeoForge surfaces (vertical faces all caps).
        StateSurface surface = new StateSurface(
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

        Roles roles = AthenaPaneSurfaceRoles.select(surface)
                .orElseThrow();

        assertEquals(
                PANE_TEXTURE,
                roles.body().sprite()
                        .contents()
                        .name()
                        .toString(),
                "body sprite must stay the pane strip texture");
        assertEquals(
                Direction.NORTH,
                roles.body().direction(),
                "body direction must stay the stable largest-face pick");
        assertEquals(
                EDGE_TEXTURE,
                roles.edge().sprite()
                        .contents()
                        .name()
                        .toString(),
                "edge sprite must stay the cap texture");
        assertEquals(
                Direction.DOWN,
                roles.edge().direction(),
                "edge direction must stay the stable largest-face pick");
    }

    @Test
    void singleArmAndIsolatedTypicalInputsKeepValueIdenticalOutputs() {
        TextureAtlasSprite pane = sprite(PANE_TEXTURE);
        TextureAtlasSprite edge = sprite(EDGE_TEXTURE);
        BlockState singleArm = Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState()
                .setValue(CrossCollisionBlock.NORTH, true);
        StateSurface singleArmSurface = new StateSurface(
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

        Roles singleArmRoles =
                AthenaPaneSurfaceRoles.select(
                                singleArmSurface)
                        .orElseThrow();
        assertEquals(
                PANE_TEXTURE,
                singleArmRoles.body().sprite()
                        .contents()
                        .name()
                        .toString(),
                "single-arm body must stay the pane strip texture");
        assertEquals(
                Direction.WEST,
                singleArmRoles.body().direction(),
                "single-arm body must stay the stable largest-face pick");
        assertEquals(
                EDGE_TEXTURE,
                singleArmRoles.edge().sprite()
                        .contents()
                        .name()
                        .toString(),
                "single-arm edge must stay the cap texture");

        BlockState isolated = Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState();
        StateSurface isolatedSurface = new StateSurface(
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
        Roles isolatedRoles =
                AthenaPaneSurfaceRoles.select(
                                isolatedSurface)
                        .orElseThrow();
        assertEquals(
                PANE_TEXTURE,
                isolatedRoles.body().sprite()
                        .contents()
                        .name()
                        .toString(),
                "isolated body must stay the pane strip texture");
        assertEquals(
                EDGE_TEXTURE,
                isolatedRoles.edge().sprite()
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
        StateSurface surface = new StateSurface(
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

        Roles roles = AthenaPaneSurfaceRoles.select(surface)
                .orElseThrow();

        assertEquals(
                EDGE_TEXTURE,
                roles.edge().sprite()
                        .contents()
                        .name()
                        .toString(),
                "edge must prefer the cap sprite over a larger "
                        + "body-sprite vertical face");
        assertEquals(
                PANE_TEXTURE,
                roles.body().sprite()
                        .contents()
                        .name()
                        .toString(),
                "body must keep the pane strip sprite");
    }

    @Test
    void currentAllFalseBorrowsCapFromSingleArmSibling() {
        BlockState allFalse = Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState();
        BlockState singleArm = Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState()
                .setValue(CrossCollisionBlock.NORTH, true);
        TextureAtlasSprite pane = sprite(PANE_TEXTURE);
        TextureAtlasSprite edge = sprite(EDGE_TEXTURE);
        // 中文：当前全 false 退化表面：竖直面全为 body 精灵（无 cap），edge 退化。
        // English: The current all-false degenerate surface: vertical faces are all the body
        // sprite (no cap), so edge degenerates.
        StateSurface current = new StateSurface(
                allFalse,
                Map.of(
                        Direction.NORTH,
                        List.of(face(
                                Direction.NORTH,
                                pane,
                                1.0F)),
                        Direction.DOWN,
                        List.of(face(
                                Direction.DOWN,
                                pane,
                                0.0625F)),
                        Direction.UP,
                        List.of(face(
                                Direction.UP,
                                pane,
                                0.0625F))));
        // 中文：sibling 单臂表面：含稳定 cap 竖直面（edge 精灵）。
        // English: The single-arm sibling surface: holds a stable cap vertical face.
        StateSurface sibling = new StateSurface(
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

        Roles roles = AthenaPaneSurfaceRoles
                .selectWithSiblingFallback(
                        current,
                        List.of(sibling))
                .orElseThrow();

        assertEquals(
                EDGE_TEXTURE,
                roles.edge().sprite()
                        .contents()
                        .name()
                        .toString(),
                "edge must borrow the sibling's stable cap sprite");
        assertEquals(
                PANE_TEXTURE,
                roles.body().sprite()
                        .contents()
                        .name()
                        .toString(),
                "body must stay the current state's pane sprite");
    }

    @Test
    void noSiblingKeepsSafeDegenerateFallback() {
        BlockState allFalse = Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState();
        TextureAtlasSprite pane = sprite(PANE_TEXTURE);
        StateSurface current = new StateSurface(
                allFalse,
                Map.of(
                        Direction.NORTH,
                        List.of(face(
                                Direction.NORTH,
                                pane,
                                1.0F)),
                        Direction.DOWN,
                        List.of(face(
                                Direction.DOWN,
                                pane,
                                0.0625F)),
                        Direction.UP,
                        List.of(face(
                                Direction.UP,
                                pane,
                                0.0625F))));

        Roles roles = AthenaPaneSurfaceRoles
                .selectWithSiblingFallback(
                        current,
                        List.of())
                .orElseThrow();

        assertEquals(
                PANE_TEXTURE,
                roles.edge().sprite()
                        .contents()
                        .name()
                        .toString(),
                "without a sibling the edge must safely fall back to the "
                        + "degenerate current face, never crash or become empty");
        assertTrue(
                roles.edge().sprite()
                        .contents()
                        .name()
                        .equals(
                                roles.body().sprite()
                                        .contents()
                                        .name()),
                "the safe fallback keeps the degenerate edge==body relation");
    }

    @Test
    void missingEitherAxisReturnsEmpty() {
        TextureAtlasSprite pane = sprite(PANE_TEXTURE);
        BlockState state = Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState();
        StateSurface horizontalOnly = new StateSurface(
                state,
                Map.of(
                        Direction.NORTH,
                        List.of(face(
                                Direction.NORTH,
                                pane,
                                1.0F))));

        Optional<Roles> roles =
                AthenaPaneSurfaceRoles.select(
                        horizontalOnly);

        assertTrue(
                roles.isEmpty(),
                "a pane surface without any vertical face must not select roles");
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
        return new BakedQuad(
                new Vector3f(
                        positions[0][0],
                        positions[0][1],
                        positions[0][2]),
                new Vector3f(
                        positions[1][0],
                        positions[1][1],
                        positions[1][2]),
                new Vector3f(
                        positions[2][0],
                        positions[2][1],
                        positions[2][2]),
                new Vector3f(
                        positions[3][0],
                        positions[3][1],
                        positions[3][2]),
                0L,
                0L,
                0L,
                0L,
                direction,
                new BakedQuad.MaterialInfo(
                        sprite,
                        ChunkSectionLayer.CUTOUT,
                        null,
                        -1,
                        false,
                        0));
    }

    private static TextureAtlasSprite sprite(
            String name) {
        NativeImage image =
                new NativeImage(16, 16, false);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setPixel(
                        x,
                        y,
                        0xFFFFFFFF);
            }
        }
        SpriteContents contents = new SpriteContents(
                Identifier.parse(name),
                new FrameSize(16, 16),
                image);
        return new TestSprite(
                TextureAtlas.LOCATION_BLOCKS,
                contents);
    }

    private static final class TestSprite
            extends TextureAtlasSprite {
        private TestSprite(
                Identifier atlasLocation,
                SpriteContents contents) {
            super(
                    atlasLocation,
                    contents,
                    2048,
                    2048,
                    0,
                    0,
                    0);
        }
    }
}
