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
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 契约——全 false/孤立状态 first-bake 推导的 StateSurface 没有 cap 竖直面，
 * edge 退化绑定 body 精灵。必须按 0d5bce0 已验收的跨状态回退语义：仅当当前 state 的 edge
 * 与 body 同精灵时，从同一 Block 的其他连接状态（sibling）借用稳定 cap FaceSurface，
 * body 保持当前 state；无 sibling 时明确安全回退（present、edge 保持退化、不崩溃）。
 * 纯数据驱动，不依赖方块 ID/精灵名白名单。当前工厂 seam 不接受 siblings，本测试应先失败。
 *
 * <p>English: RED contract -- the all-false/isolated state's first-bake derived StateSurface
 * has no cap vertical face, so edge degenerates to the body sprite. The accepted 0d5bce0
 * cross-state fallback semantics must apply: only when the current state's edge shares the
 * body sprite, borrow a stable cap FaceSurface from another connection state (sibling) of the
 * same Block, keeping body on the current state; with no sibling the result safely falls back
 * (present, edge stays degenerate, never crashes). Purely data-driven with no block-id or
 * sprite whitelists. The current factory seam accepts no siblings, so this fails first.
 */
class AthenaPaneSurfaceRolesSiblingFallbackContractTest {
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
    void currentAllFalseBorrowsCapFromSingleArmSibling() {
        BlockState allFalse = Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState();
        BlockState singleArm = Blocks.GREEN_STAINED_GLASS_PANE
                .defaultBlockState()
                .setValue(IronBarsBlock.NORTH, true);
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

        FabricAthenaGeneratedPaneModelFactory
                .PaneSources sources =
                FabricAthenaGeneratedPaneModelFactory
                        .firstBakePaneSources(
                                allFalse,
                                new AllFalseDelegate(
                                        pane,
                                        edge),
                                List.of(sibling))
                        .orElseThrow();

        assertEquals(
                EDGE_TEXTURE,
                sources.edge().sprite()
                        .contents()
                        .name()
                        .toString(),
                "edge must borrow the sibling's stable cap sprite");
        assertEquals(
                PANE_TEXTURE,
                sources.body().sprite()
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
        TextureAtlasSprite edge = sprite(EDGE_TEXTURE);

        FabricAthenaGeneratedPaneModelFactory
                .PaneSources sources =
                FabricAthenaGeneratedPaneModelFactory
                        .firstBakePaneSources(
                                allFalse,
                                new AllFalseDelegate(
                                        pane,
                                        edge),
                                List.of())
                        .orElseThrow();

        assertEquals(
                PANE_TEXTURE,
                sources.edge().sprite()
                        .contents()
                        .name()
                        .toString(),
                "without a sibling the edge must safely fall back to the "
                        + "degenerate current face, never crash or become empty");
        assertTrue(
                sources.edge().sprite()
                        .contents()
                        .name()
                        .equals(
                                sources.body().sprite()
                                        .contents()
                                        .name()),
                "the safe fallback keeps the degenerate edge==body relation");
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
        int uvOffset = format.getOffset(
                VertexFormatElement.UV0) / 4;
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
                ResourceLocation.parse(name),
                new FrameSize(16, 16),
                image,
                ResourceMetadata.EMPTY);
        return new TestSprite(
                TextureAtlas.LOCATION_BLOCKS,
                contents);
    }

    /** 中文：全 false 委托：水平面=body 大面，竖直面全为 body 精灵（无 cap），复现观测退化。 / English: All-false delegate: horizontal body face, vertical faces all body sprite (no cap), reproducing the observed degeneracy. */
    private static final class AllFalseDelegate
            implements net.minecraft.client.resources.model.BakedModel {
        private final TextureAtlasSprite pane;
        private final TextureAtlasSprite edge;

        private AllFalseDelegate(
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
                        pane,
                        new float[][] {
                            {0.0F, 1.0F, 0.0F},
                            {1.0F, 1.0F, 0.0F},
                            {1.0F, 0.0F, 0.0F},
                            {0.0F, 0.0F, 0.0F}
                        }));
            }
            if (direction == Direction.DOWN) {
                return List.of(quad(
                        Direction.DOWN,
                        pane,
                        new float[][] {
                            {0.0F, 0.0F, 0.0F},
                            {0.25F, 0.0F, 0.0F},
                            {0.25F, 0.0F, 0.25F},
                            {0.0F, 0.0F, 0.25F}
                        }));
            }
            if (direction == Direction.UP) {
                return List.of(quad(
                        Direction.UP,
                        pane,
                        new float[][] {
                            {0.0F, 1.0F, 0.0F},
                            {0.25F, 1.0F, 0.0F},
                            {0.25F, 1.0F, 0.25F},
                            {0.0F, 1.0F, 0.25F}
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
        public net.minecraft.client.renderer.block.model.ItemTransforms
                getTransforms() {
            return net.minecraft.client.renderer.block.model
                    .ItemTransforms.NO_TRANSFORMS;
        }

        @Override
        public net.minecraft.client.renderer.block.model.ItemOverrides
                getOverrides() {
            return net.minecraft.client.renderer.block.model
                    .ItemOverrides.EMPTY;
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
