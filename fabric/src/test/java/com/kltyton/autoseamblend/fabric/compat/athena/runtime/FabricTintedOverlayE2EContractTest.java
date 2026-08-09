package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeProvider;
import com.kltyton.autoseamblend.engine.EngineDescriptor;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.ownership.SourceTier;
import com.kltyton.autoseamblend.engine.routing.query.EngineRouteProvenance;
import com.kltyton.autoseamblend.engine.routing.query.EngineRouteSelection;
import com.kltyton.autoseamblend.inference.ConnectionAxis;
import com.kltyton.autoseamblend.inference.InferenceFacts;
import com.kltyton.autoseamblend.inference.InferenceFacts.FactState;
import com.kltyton.autoseamblend.runtime.overlay.OverlayDonorResolver;
import com.kltyton.autoseamblend.runtime.overlay.PlanarOverlayNeighborhood;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.StateSurface;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.mask.OverlayCutoutProfile;
import com.kltyton.autoseamblend.texture.mask.TextureFrameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import earth.terrarium.athena.api.client.utils.CtmState;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.material.ShadeMode;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：非 grass、非 sprite 名的端到端合成合同：同一方向的 opaque base + tinted translucent
 * overlay 经 OverlayDonorResolver 供体选择与 FabricAthenaNativeQuadProcessor 发射，必须使用
 * overlay 精灵、保留一次 ARGB tint、colorIndex=-1、CUTOUT 材质，且 base 永不替代 overlay。
 * 生成精灵注册/真实资源快照不在此测试范围（若 GREEN，运行时剩余只能由真实快照/生成注册
 * 造成）。
 *
 * English: Non-grass, non-sprite-name end-to-end composition contract: a same-direction opaque
 * base plus tinted translucent overlay, through OverlayDonorResolver donor selection and
 * FabricAthenaNativeQuadProcessor emission, must use the overlay sprite, keep the ARGB tint
 * once, colorIndex=-1, CUTOUT material, and the base must never replace the overlay. Generated
 * sprite registration / real resource snapshots are out of scope (if GREEN, any remaining
 * runtime difference can only come from real snapshots or generated-sprite registration).
 */
class FabricTintedOverlayE2EContractTest {
    private static final long GENERATION = 1L;
    private static final int TINT = 0xFF77AB2F;
    private static final ResourceLocation BASE_SPRITE =
            ResourceLocation.parse(
                    "minecraft:block/some_base");
    private static final ResourceLocation OVERLAY_SPRITE =
            ResourceLocation.parse(
                    "minecraft:block/some_overlay");

    private final StubRenderMaterial cutoutMaterial =
            new StubRenderMaterial();
    private final RecordingQuadEmitter emitter =
            new RecordingQuadEmitter();

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @AfterEach
    void restoreMaterialSource() {
        FabricAthenaNativeQuadProcessor
                .CUTOUT_MATERIAL_SOURCE
                .set(FabricAthenaNativeQuadProcessor
                        ::resolveCutoutMaterial);
    }

    @Test
    void tintedOverlayE2EUsesOverlaySpriteSingleTintCutout() {
        BlockState receiver = Blocks.STONE.defaultBlockState();
        BlockState donor = Blocks.DIRT.defaultBlockState();
        TextureAtlasSprite stoneSprite =
                sprite(
                        ResourceLocation.parse(
                                "minecraft:block/stone"));
        TextureAtlasSprite baseSprite =
                sprite(BASE_SPRITE);
        TextureAtlasSprite overlaySprite =
                sprite(OVERLAY_SPRITE);

        MinecraftSurfaceCatalog.Snapshot snapshot =
                new MinecraftSurfaceCatalog.Snapshot(
                        GENERATION,
                        Map.of(
                                receiver,
                                new StateSurface(
                                        receiver,
                                        Map.of(
                                                Direction.NORTH,
                                                List.of(face(
                                                        stoneSprite,
                                                        -1,
                                                        true)))),
                                donor,
                                new StateSurface(
                                        donor,
                                        Map.of(
                                                Direction.NORTH,
                                                List.of(
                                                        face(
                                                                baseSprite,
                                                                -1,
                                                                true),
                                                        face(
                                                                overlaySprite,
                                                                0,
                                                                false))))),
                        List.of());
        OverlayDonorResolver resolver =
                new OverlayDonorResolver(
                        (family, state) -> state.getBlock()
                                        == donor.getBlock()
                                ? Optional.of(route())
                                : Optional.empty());

        List<OverlayDonorResolver.Donor> donors =
                resolver.resolveAll(
                        new DonorLevel(donor),
                        new BlockPos(0, 0, 0),
                        Direction.NORTH,
                        receiver,
                        RuleRuntime.bootstrapSnapshot()
                                .rules(),
                        snapshot,
                        EngineFamily.ATHENA,
                        PlanarOverlayNeighborhood
                                .planarDirections(
                                        Direction.NORTH));

        assertEquals(
                1,
                donors.size(),
                "the tinted overlay donor must be selected");
        OverlayDonorResolver.Donor selected =
                donors.getFirst();
        assertSame(
                overlaySprite,
                selected.surface().sprite(),
                "the donor surface must be the tinted overlay, never the base");
        assertNotSame(
                baseSprite,
                selected.surface().sprite(),
                "the opaque base must not replace the overlay");
        assertEquals(
                0,
                selected.surface().tintIndex(),
                "the selected overlay surface keeps its tint index");

        TextureAtlasSprite[] roleSprites = {
            overlaySprite,
            overlaySprite,
            overlaySprite,
            overlaySprite,
            overlaySprite
        };
        CtmState nativeState = new CtmState(
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false);
        FabricAthenaNativeQuadProcessor
                .CUTOUT_MATERIAL_SOURCE
                .set(() -> cutoutMaterial);
        boolean emitted =
                FabricAthenaNativeQuadProcessor
                        .emitOverlayReplacement(
                                Direction.NORTH,
                                nativeState,
                                roleSprites,
                                TINT,
                                emitter);

        assertTrue(
                emitted,
                "overlay replacement must be emitted");
        assertEquals(
                4,
                emitter.bakedSprites.size(),
                "a non-allTrue overlay state emits four quadrant quads");
        assertTrue(
                emitter.bakedSprites.stream()
                        .allMatch(sprite -> sprite
                                == overlaySprite),
                "every emitted quad must use the overlay sprite");
        assertTrue(
                emitter.bakedSprites.stream()
                        .noneMatch(sprite -> sprite
                                == baseSprite),
                "the opaque base must never appear in emission");
        assertTrue(
                emitter.vertexColors.stream()
                        .allMatch(color -> color == TINT),
                "the ARGB tint must be preserved once in the emitted color");
        assertTrue(
                emitter.colorIndices.stream()
                        .allMatch(index -> index == -1),
                "the overlay must disable the color index");
        assertTrue(
                emitter.materials.stream()
                        .allMatch(material -> material
                                == cutoutMaterial),
                "the overlay must run in the CUTOUT material layer");
    }

    private static EngineRouteSelection route() {
        EngineDescriptor athena = new EngineDescriptor(
                "athena",
                EngineFamily.ATHENA,
                "athena",
                "athena",
                "4.0.6",
                "athena-fabric-4.0.6");
        return new EngineRouteSelection(
                athena,
                EngineRouteProvenance.config(
                        SourceTier.CONFIG_COMPATIBILITY,
                        0),
                List.of(),
                Optional.empty(),
                ConnectionMethod.OVERLAY);
    }

    private static FaceSurface face(
            TextureAtlasSprite sprite,
            int tintIndex,
            boolean opaque) {
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
        return new FaceSurface(
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
            String name) {
        return sprite(ResourceLocation.parse(name));
    }

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

    /** 中文：所有位置返回同一供体方块的最小世界桩。 / English: Minimal world stub returning one donor state everywhere. */
    private static final class DonorLevel
            implements BlockAndTintGetter {
        private final BlockState donor;

        private DonorLevel(BlockState donor) {
            this.donor = donor;
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return donor;
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

    /** 中文：记录 spriteBake/color/colorIndex/material/emit 的最小 QuadEmitter。 / English: Minimal QuadEmitter recording spriteBake/color/colorIndex/material/emit. */
    private static final class RecordingQuadEmitter
            implements QuadEmitter {
        private final List<TextureAtlasSprite> bakedSprites =
                new java.util.ArrayList<>();
        private final List<Integer> vertexColors =
                new java.util.ArrayList<>();
        private final List<Integer> colorIndices =
                new java.util.ArrayList<>();
        private final List<RenderMaterial> materials =
                new java.util.ArrayList<>();
        private int emits;

        @Override
        public QuadEmitter spriteBake(
                TextureAtlasSprite sprite,
                int bakeFlags) {
            bakedSprites.add(sprite);
            return this;
        }

        @Override
        public QuadEmitter color(
                int vertexIndex,
                int color) {
            vertexColors.add(color);
            return this;
        }

        @Override
        public QuadEmitter colorIndex(int colorIndex) {
            colorIndices.add(colorIndex);
            return this;
        }

        @Override
        public QuadEmitter material(
                RenderMaterial material) {
            materials.add(material);
            return this;
        }

        @Override
        public QuadEmitter emit() {
            emits++;
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
        public QuadEmitter lightmap(
                int vertexIndex,
                int lightmap) {
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
        public QuadEmitter cullFace(
                Direction cullFace) {
            return this;
        }

        @Override
        public QuadEmitter nominalFace(
                Direction nominalFace) {
            return this;
        }

        @Override
        public QuadEmitter tag(int tag) {
            return this;
        }

        @Override
        public QuadEmitter copyFrom(
                net.fabricmc.fabric.api.renderer.v1.mesh.QuadView quad) {
            return this;
        }

        @Override
        public QuadEmitter fromVanilla(
                int[] vertices,
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
        public float posByIndex(
                int vertexIndex,
                int coordinateIndex) {
            return 0.0F;
        }

        @Override
        public org.joml.Vector3f copyPos(
                int vertexIndex,
                org.joml.Vector3f target) {
            return target.set(0.0F, 0.0F, 0.0F);
        }

        @Override
        public int color(int vertexIndex) {
            return vertexColors.isEmpty()
                    ? -1
                    : vertexColors.get(
                            vertexColors.size() - 1);
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
            return target.set(0.0F, 0.0F);
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
            return target.set(0.0F, 0.0F, 0.0F);
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
        public org.joml.Vector3f faceNormal() {
            return null;
        }

        @Override
        public RenderMaterial material() {
            return materials.isEmpty()
                    ? null
                    : materials.get(
                            materials.size() - 1);
        }

        @Override
        public int colorIndex() {
            return colorIndices.isEmpty()
                    ? -1
                    : colorIndices.get(
                            colorIndices.size() - 1);
        }

        @Override
        public int tag() {
            return 0;
        }

        @Override
        public void toVanilla(
                int[] vertices,
                int startIndex) {}
    }

    /** 中文：最小 CUTOUT/VANILLA RenderMaterial stub。 / English: Minimal CUTOUT/VANILLA RenderMaterial stub. */
    private static final class StubRenderMaterial
            implements RenderMaterial {
        @Override
        public BlendMode blendMode() {
            return BlendMode.CUTOUT;
        }

        @Override
        public boolean disableColorIndex() {
            return false;
        }

        @Override
        public boolean emissive() {
            return false;
        }

        @Override
        public boolean disableDiffuse() {
            return false;
        }

        @Override
        public TriState ambientOcclusion() {
            return TriState.DEFAULT;
        }

        @Override
        public TriState glint() {
            return TriState.DEFAULT;
        }

        @Override
        public ShadeMode shadeMode() {
            return ShadeMode.VANILLA;
        }
    }
}
