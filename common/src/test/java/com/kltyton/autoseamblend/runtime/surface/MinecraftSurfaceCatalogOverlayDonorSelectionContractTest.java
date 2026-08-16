package com.kltyton.autoseamblend.runtime.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.inference.ConnectionAxis;
import com.kltyton.autoseamblend.inference.InferenceFacts;
import com.kltyton.autoseamblend.inference.InferenceFacts.FactState;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import com.kltyton.autoseamblend.texture.mask.TextureFrameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.EnumSet;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：通用 tinted-overlay 供体面选择的 RED 合同（非 grass 泛化示例）：当同一面存在
 * opaque 基底（tintIndex=-1）与 tinted overlay 层（tintIndex>=0）时，overlay 供体必须
 * 选择 tinted 层，而不是 opaque-first 的通用 preferredFace 选出的基底。单层与无 tint
 * 场景必须保持不变（NeoForge 既有行为不变的证明）。
 *
 * English: RED contract for the generalized tinted-overlay donor-face selection on a
 * non-grass example: when one face has an opaque base (tintIndex=-1) plus a tinted overlay
 * layer (tintIndex>=0), the overlay donor must select the tinted layer instead of the
 * opaque-first generic preferredFace result. Single-layer and untinted cases must stay
 * unchanged (evidence that existing NeoForge behavior is preserved).
 */
class MinecraftSurfaceCatalogOverlayDonorSelectionContractTest {
    private static final ResourceLocation TEST_BASE =
            ResourceLocation.parse(
                    "minecraft:block/some_base");
    private static final ResourceLocation TEST_OVERLAY =
            ResourceLocation.parse(
                    "minecraft:block/some_overlay");

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void tintedOverlayLayerWinsOverOpaqueBaseForOverlayDonor() {
        BlockState state = Blocks.STONE.defaultBlockState();
        MinecraftSurfaceCatalog.FaceSurface base = face(
                TEST_BASE,
                -1,
                true);
        MinecraftSurfaceCatalog.FaceSurface overlay = face(
                TEST_OVERLAY,
                0,
                false);
        MinecraftSurfaceCatalog.StateSurface surface =
                new MinecraftSurfaceCatalog.StateSurface(
                        state,
                        Map.of(
                                Direction.NORTH,
                                List.of(base, overlay)));

        // 中文：旧通用语义是 opaque-first，供体若复用会选中基底（本缺陷的根源）。
        // English: The legacy generic semantics are opaque-first; a donor reusing it selects
        // the base, which is the root of this defect.
        assertSame(
                base,
                surface.preferredFace(Direction.NORTH)
                        .orElseThrow(),
                "legacy preferredFace stays opaque-first");
        // 中文：overlay 供体专用选择必须选 tinted 层。
        // English: The overlay-donor-specific selection must pick the tinted layer.
        assertSame(
                overlay,
                surface.overlayDonorFace(Direction.NORTH)
                        .orElseThrow(),
                "overlay donor must prefer the tinted layer");
        assertEquals(
                0,
                surface.overlayDonorFace(Direction.NORTH)
                        .orElseThrow()
                        .tintIndex(),
                "the tinted layer carries the overlay tint index");
    }

    @Test
    void singleUntintedOpaqueFaceUnchanged() {
        BlockState state = Blocks.STONE.defaultBlockState();
        MinecraftSurfaceCatalog.FaceSurface only = face(
                ResourceLocation.parse(
                        "minecraft:block/plain"),
                -1,
                true);
        MinecraftSurfaceCatalog.StateSurface surface =
                new MinecraftSurfaceCatalog.StateSurface(
                        state,
                        Map.of(
                                Direction.NORTH,
                                List.of(only)));
        assertSame(
                only,
                surface.overlayDonorFace(Direction.NORTH)
                        .orElseThrow(),
                "single untinted face selection unchanged");
        assertSame(
                only,
                surface.preferredFace(Direction.NORTH)
                        .orElseThrow(),
                "single untinted face preferred selection unchanged");
    }

    @Test
    void singleTintedFaceKept() {
        BlockState state = Blocks.STONE.defaultBlockState();
        MinecraftSurfaceCatalog.FaceSurface tinted = face(
                ResourceLocation.parse(
                        "minecraft:block/tinted"),
                0,
                true);
        MinecraftSurfaceCatalog.StateSurface surface =
                new MinecraftSurfaceCatalog.StateSurface(
                        state,
                        Map.of(
                                Direction.NORTH,
                                List.of(tinted)));
        assertSame(
                tinted,
                surface.overlayDonorFace(Direction.NORTH)
                        .orElseThrow(),
                "a single tinted face is kept as the overlay donor");
    }

    @Test
    void tintedTranslucentOverlaySelectionPreservesColorAndAlpha() {
        // 中文：任意 tinted translucent overlay + opaque base 组合的泛化合同：选中层必须
        // 保留 tint 色来源（精灵 + tintIndex）与 alpha 事实，不得被选择过程改写。
        // English: Generalized contract for any tinted translucent overlay plus opaque base
        // pair: the selected layer must keep the tint color source (sprite + tintIndex) and
        // the alpha facts unchanged by the selection.
        BlockState state = Blocks.STONE.defaultBlockState();
        MinecraftSurfaceCatalog.FaceSurface base = face(
                TEST_BASE,
                -1,
                true);
        MinecraftSurfaceCatalog.FaceSurface overlay = face(
                TEST_OVERLAY,
                0,
                false);
        MinecraftSurfaceCatalog.StateSurface surface =
                new MinecraftSurfaceCatalog.StateSurface(
                        state,
                        Map.of(
                                Direction.NORTH,
                                List.of(base, overlay)));
        MinecraftSurfaceCatalog.FaceSurface selected =
                surface.overlayDonorFace(Direction.NORTH)
                        .orElseThrow();
        assertSame(
                overlay.sprite(),
                selected.sprite(),
                "selection keeps the tinted overlay sprite (color source)");
        assertEquals(
                0,
                selected.tintIndex(),
                "selection keeps the overlay tint index (color)");
        assertEquals(
                overlay.fullyTransparent(),
                selected.fullyTransparent(),
                "selection keeps the overlay alpha fact");
        assertEquals(
                overlay.facts().alphaOpaque(),
                selected.facts().alphaOpaque(),
                "selection keeps the overlay alpha-opaque fact");
    }

    @Test
    void completelyAbsentStateCannotBorrowSiblingSurface() {
        BlockState absentState = Blocks.GLASS_PANE.defaultBlockState();
        BlockState siblingState = absentState.setValue(BlockStateProperties.NORTH, true);
        MinecraftSurfaceCatalog.FaceSurface siblingFace = face(TEST_BASE, -1, true);
        MinecraftSurfaceCatalog.Snapshot snapshot = new MinecraftSurfaceCatalog.Snapshot(
                1L,
                Map.of(
                        siblingState,
                        new MinecraftSurfaceCatalog.StateSurface(
                                siblingState,
                                Map.of(Direction.NORTH, List.of(siblingFace)))),
                List.of());

        assertTrue(snapshot.face(
                        absentState,
                        Direction.NORTH,
                        Direction.NORTH,
                        siblingFace.sprite())
                .isEmpty());
    }

    private static MinecraftSurfaceCatalog.FaceSurface face(
            ResourceLocation spriteId,
            int tintIndex,
            boolean opaque) {
        TextureAtlasSprite sprite = sprite(spriteId);
        InferenceFacts facts = new InferenceFacts(
                FactState.TRUE,
                FactState.TRUE,
                FactState.TRUE,
                FactState.TRUE,
                FactState.TRUE,
                FactState.of(opaque),
                FactState.FALSE,
                FactState.FALSE,
                FactState.of(tintIndex >= 0),
                FactState.TRUE,
                FactState.FALSE,
                FactState.FALSE,
                FactState.FALSE,
                FactState.TRUE,
                EnumSet.of(
                        ConnectionAxis.HORIZONTAL,
                        ConnectionAxis.VERTICAL));
        return new MinecraftSurfaceCatalog.FaceSurface(
                Direction.NORTH,
                sprite,
                tintIndex,
                true,
                false,
                new BakedQuad(
                        new int[32],
                        tintIndex,
                        Direction.NORTH,
                        sprite,
                        true),
                facts,
                ConnectionMethod.CTM,
                OverlayCutoutProfile.thinUniform(),
                new TextureFrameProfile(
                        0.0F,
                        0.0F,
                        0.0F,
                        0.0F));
    }

    /** 中文：位于假定 2048x2048 Atlas 原点的 16x16 测试精灵。 / English: 16x16 test sprite at the assumed 2048x2048 atlas origin. */
    private static TextureAtlasSprite sprite(
            ResourceLocation name) {
        NativeImage image =
                new NativeImage(16, 16, false);
        SpriteContents contents = new SpriteContents(
                name,
                new FrameSize(16, 16),
                image,
                ResourceMetadata.EMPTY);
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
                    2048,
                    2048,
                    0,
                    0);
        }
    }
}
