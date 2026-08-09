package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeProvider;
import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeStateSampler;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import earth.terrarium.athena.api.client.fabric.WrappedGetter;
import earth.terrarium.athena.api.client.models.AthenaQuad;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
 * 中文：RED 回归契约——replacement 必须区分 fullFace 与 !fullFace：非整面 pane-like 源
 * quad（薄条）经 replacement 后坐标 bounds 必须与源一致，只把源精灵的局部 UV 重映射到
 * 目标精灵区域（移植已验收 NeoForge fullFace ? bakeNative : retexture(source, sprite)
 * 的 !fullFace 分支）；fullFace 保持现有 square/象限路径，草方块与完整玻璃不受影响。
 * 当前 Fabric 实现无条件 square() 成整面，本测试应先失败。
 *
 * <p>English: RED regression contract -- replacement must distinguish fullFace from
 * !fullFace: a non-full-face pane-like source quad (thin strip) must keep the source
 * coordinate bounds through replacement, remapping only the source sprite's local UVs
 * into the target sprite region (porting the accepted NeoForge
 * fullFace ? bakeNative : retexture(source, sprite) !fullFace branch); fullFace keeps the
 * existing square/quadrant path, so grass and full glass stay unchanged. The current
 * Fabric implementation unconditionally squares every quad into a full face, so this test
 * must fail first.
 */
class FabricAthenaNonFullFaceReplacementContractTest {
    private static final float PANE_THIN = 0.25F;

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void nonFullFaceReplacementKeepsSourceBoundsAndRemapsLocalUvs() {
        TextureAtlasSprite sourceSprite = sprite(
                "minecraft:block/glass",
                16,
                16,
                0,
                0);
        TextureAtlasSprite[] stateSprites = {
            sprite("autoseamblend:role0", 32, 32, 8, 0),
            sprite("autoseamblend:role1", 32, 32, 8, 0),
            sprite("autoseamblend:role2", 32, 32, 8, 0),
            sprite("autoseamblend:role3", 32, 32, 8, 0),
            sprite("autoseamblend:role4", 32, 32, 8, 0)
        };
        BakedQuad source = northStripQuad(sourceSprite);
        AirLevel level = new AirLevel();
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        ConnectionRuleSet<Block> rules =
                RuleRuntime.bootstrapSnapshot().rules();
        RecordingQuadEmitter emitter =
                new RecordingQuadEmitter();

        boolean emitted =
                FabricAthenaNativeQuadProcessor.emitReplacement(
                        Direction.NORTH,
                        sourceSprite,
                        stateSprites,
                        level,
                        BlockPos.ZERO,
                        pane,
                        rules,
                        source,
                        -1,
                        emitter,
                        OptionalInt.empty(),
                        ConnectionMethod.CTM,
                        false);

        assertTrue(
                emitted,
                "a non-fullFace pane-like quad must be replaced");
        assertFalse(
                emitter.emittedQuads.isEmpty(),
                "at least one replacement quad must be emitted");
        List<AthenaQuad> expectedQuads =
                AthenaNativeProvider.quads(
                        AthenaNativeStateSampler.sample(
                                new WrappedGetter(level),
                                pane,
                                BlockPos.ZERO,
                                Direction.NORTH,
                                rules,
                                Set.of()),
                        Direction.NORTH,
                        stateSprites);
        assertEquals(
                expectedQuads.size(),
                emitter.emittedQuads.size(),
                "one retextured quad per native AthenaQuad, matching the "
                        + "NeoForge !fullFace contract");
        for (int index = 0;
                index < emitter.emittedQuads.size();
                index++) {
            QuadData data =
                    emitter.emittedQuads.get(index);
            assertBounds(
                    data.positions,
                    "emitted quad must keep the source strip bounds");
            TextureAtlasSprite target =
                    stateSprites[expectedQuads
                            .get(index)
                            .sprite()];
            for (int vertex = 0;
                    vertex < 4;
                    vertex++) {
                assertEquals(
                        target.getU(sourceLocalU(
                                source,
                                vertex)),
                        data.uvs[vertex][0],
                        1.0e-4F,
                        "U must be remapped into the target sprite region");
                assertEquals(
                        target.getV(sourceLocalV(
                                source,
                                vertex)),
                        data.uvs[vertex][1],
                        1.0e-4F,
                        "V must be remapped into the target sprite region");
            }
        }
    }

    @Test
    void fullFaceReplacementKeepsQuadrantSquarePath() {
        TextureAtlasSprite sourceSprite = sprite(
                "minecraft:block/glass",
                16,
                16,
                0,
                0);
        TextureAtlasSprite[] stateSprites = {
            sprite("autoseamblend:role0", 32, 32, 8, 0),
            sprite("autoseamblend:role1", 32, 32, 8, 0),
            sprite("autoseamblend:role2", 32, 32, 8, 0),
            sprite("autoseamblend:role3", 32, 32, 8, 0),
            sprite("autoseamblend:role4", 32, 32, 8, 0)
        };
        BakedQuad source = northStripQuad(sourceSprite);
        AirLevel level = new AirLevel();
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        ConnectionRuleSet<Block> rules =
                RuleRuntime.bootstrapSnapshot().rules();
        RecordingQuadEmitter emitter =
                new RecordingQuadEmitter();

        boolean emitted =
                FabricAthenaNativeQuadProcessor.emitReplacement(
                        Direction.NORTH,
                        sourceSprite,
                        stateSprites,
                        level,
                        BlockPos.ZERO,
                        pane,
                        rules,
                        source,
                        -1,
                        emitter,
                        OptionalInt.empty(),
                        ConnectionMethod.CTM,
                        true);

        assertTrue(
                emitted,
                "a fullFace quad must be replaced through the quadrant path");
        assertFalse(
                emitter.emittedQuads.isEmpty(),
                "the fullFace quadrant path must emit quads");
        List<AthenaQuad> expectedQuads =
                AthenaNativeProvider.quads(
                        AthenaNativeStateSampler.sample(
                                new WrappedGetter(level),
                                pane,
                                BlockPos.ZERO,
                                Direction.NORTH,
                                rules,
                                Set.of()),
                        Direction.NORTH,
                        stateSprites);
        assertEquals(
                expectedQuads.size(),
                emitter.emittedQuads.size(),
                "the fullFace path keeps one square per native AthenaQuad");
        for (int index = 0;
                index < emitter.emittedQuads.size();
                index++) {
            QuadData data =
                    emitter.emittedQuads.get(index);
            AthenaQuad quad = expectedQuads.get(index);
            assertQuadrantBounds(
                    data.positions,
                    quad,
                    "the fullFace path must keep the square/quadrant geometry");
        }
    }

    private static void assertBounds(
            float[][] positions,
            String message) {
        float[] min = {
            Float.POSITIVE_INFINITY,
            Float.POSITIVE_INFINITY,
            Float.POSITIVE_INFINITY
        };
        float[] max = {
            Float.NEGATIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        };
        for (float[] position : positions) {
            for (int axis = 0; axis < 3; axis++) {
                min[axis] = Math.min(
                        min[axis],
                        position[axis]);
                max[axis] = Math.max(
                        max[axis],
                        position[axis]);
            }
        }
        assertEquals(
                7.0F,
                min[0],
                1.0e-4F,
                message + " (min x)");
        assertEquals(
                9.0F,
                max[0],
                1.0e-4F,
                message + " (max x)");
        assertEquals(
                0.0F,
                min[1],
                1.0e-4F,
                message + " (min y)");
        assertEquals(
                16.0F,
                max[1],
                1.0e-4F,
                message + " (max y)");
        assertEquals(
                0.0F,
                min[2],
                1.0e-4F,
                message + " (min z)");
        assertEquals(
                0.0F,
                max[2],
                1.0e-4F,
                message + " (max z)");
        assertTrue(
                (max[0] - min[0]) / 16.0F <= PANE_THIN,
                message + " (thin pane strip)");
    }

    private static void assertQuadrantBounds(
            float[][] positions,
            AthenaQuad quad,
            String message) {
        float[] min = {
            Float.POSITIVE_INFINITY,
            Float.POSITIVE_INFINITY,
            Float.POSITIVE_INFINITY
        };
        float[] max = {
            Float.NEGATIVE_INFINITY,
            Float.NEGATIVE_INFINITY,
            Float.NEGATIVE_INFINITY
        };
        for (float[] position : positions) {
            for (int axis = 0; axis < 3; axis++) {
                min[axis] = Math.min(
                        min[axis],
                        position[axis]);
                max[axis] = Math.max(
                        max[axis],
                        position[axis]);
            }
        }
        // 中文：与 FRAPI square(NORTH, left, bottom, right, top, depth=0) 的顶点映射一致：
        // x 从 1-right 到 1-left，y 从 bottom 到 top，z=0。
        // English: Matches FRAPI square(NORTH, left, bottom, right, top, depth=0):
        // x from 1-right to 1-left, y from bottom to top, z=0.
        assertEquals(
                Math.min(
                        1.0F - quad.right(),
                        1.0F - quad.left()),
                min[0],
                1.0e-4F,
                message + " (min x)");
        assertEquals(
                Math.max(
                        1.0F - quad.right(),
                        1.0F - quad.left()),
                max[0],
                1.0e-4F,
                message + " (max x)");
        assertEquals(
                quad.bottom(),
                min[1],
                1.0e-4F,
                message + " (min y)");
        assertEquals(
                quad.top(),
                max[1],
                1.0e-4F,
                message + " (max y)");
        assertEquals(
                0.0F,
                min[2],
                1.0e-4F,
                message + " (min z)");
        assertEquals(
                0.0F,
                max[2],
                1.0e-4F,
                message + " (max z)");
        assertFalse(
                max[0] - min[0] <= PANE_THIN
                        && max[1] - min[1] <= PANE_THIN,
                message + " (quadrant, not a thin strip)");
    }

    private static float sourceLocalU(
            BakedQuad quad,
            int vertex) {
        TextureAtlasSprite sprite = quad.getSprite();
        float u = Float.intBitsToFloat(
                quad.getVertices()[vertex * 8 + 4]);
        return (u - sprite.getU0())
                / (sprite.getU1() - sprite.getU0());
    }

    private static float sourceLocalV(
            BakedQuad quad,
            int vertex) {
        TextureAtlasSprite sprite = quad.getSprite();
        float v = Float.intBitsToFloat(
                quad.getVertices()[vertex * 8 + 5]);
        return (v - sprite.getV0())
                / (sprite.getV1() - sprite.getV0());
    }

    private static BakedQuad northStripQuad(
            TextureAtlasSprite sprite) {
        VertexFormat format = DefaultVertexFormat.BLOCK;
        int stride = format.getVertexSize() / 4;
        int uvOffset = format.getOffset(
                VertexFormatElement.UV0) / 4;
        int[] vertices = new int[stride * 4];
        float[][] positions = {
            {9.0F, 16.0F, 0.0F},
            {9.0F, 0.0F, 0.0F},
            {7.0F, 0.0F, 0.0F},
            {7.0F, 16.0F, 0.0F}
        };
        float[][] uvs = {
            {0.1F, 0.1F},
            {0.1F, 0.9F},
            {0.9F, 0.9F},
            {0.9F, 0.1F}
        };
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
                            uvs[vertex][0]);
            vertices[base + uvOffset + 1] =
                    Float.floatToRawIntBits(
                            uvs[vertex][1]);
        }
        return new BakedQuad(
                vertices,
                -1,
                Direction.NORTH,
                sprite,
                false);
    }

    private static TextureAtlasSprite sprite(
            String name,
            int width,
            int height,
            int x,
            int y) {
        NativeImage image =
                new NativeImage(width, height, false);
        SpriteContents contents = new SpriteContents(
                ResourceLocation.parse(name),
                new FrameSize(width, height),
                image,
                ResourceMetadata.EMPTY);
        return new TestSprite(
                TextureAtlas.LOCATION_BLOCKS,
                contents,
                width,
                height,
                x,
                y);
    }

    private static final class TestSprite
            extends TextureAtlasSprite {
        private TestSprite(
                ResourceLocation atlasLocation,
                SpriteContents contents,
                int width,
                int height,
                int x,
                int y) {
            super(
                    atlasLocation,
                    contents,
                    width,
                    height,
                    x,
                    y);
        }
    }

    /** 中文：全部邻居返回 AIR 的最小世界桩。 / English: Minimal world stub returning AIR for every position. */
    private static final class AirLevel
            implements BlockAndTintGetter {
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return Fluids.EMPTY.defaultFluidState();
        }

        @Override
        public int getHeight() {
            return 0;
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

    /** 中文：记录每个已发射 quad 的顶点/UV 的最小 RenderContext + QuadEmitter。 / English: Minimal RenderContext + QuadEmitter recording positions and UVs per emitted quad. */
    private static final class RecordingRenderContext
            implements RenderContext {
        private final RecordingQuadEmitter emitter =
                new RecordingQuadEmitter();

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

    private static final class RecordingQuadEmitter
            implements QuadEmitter {
        private final List<QuadData> emittedQuads =
                new ArrayList<>();
        private final float[][] positions = new float[4][3];
        private final float[][] uvs = new float[4][2];
        private int colorIndex = -1;
        private RenderMaterial material;
        private float[] squareArgs;

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
        public QuadEmitter uv(
                int vertexIndex,
                float u,
                float v) {
            uvs[vertexIndex][0] = u;
            uvs[vertexIndex][1] = v;
            return this;
        }

        @Override
        public QuadEmitter square(
                Direction nominalFace,
                float left,
                float bottom,
                float right,
                float top,
                float depth) {
            squareArgs = new float[] {
                left,
                bottom,
                right,
                top,
                depth
            };
            return QuadEmitter.super.square(
                    nominalFace,
                    left,
                    bottom,
                    right,
                    top,
                    depth);
        }

        @Override
        public QuadEmitter fromVanilla(
                int[] quadData,
                int startIndex) {
            int stride = 8;
            for (int vertex = 0;
                    vertex < 4;
                    vertex++) {
                int base = startIndex
                        + vertex * stride;
                positions[vertex][0] =
                        Float.intBitsToFloat(
                                quadData[base]);
                positions[vertex][1] =
                        Float.intBitsToFloat(
                                quadData[base + 1]);
                positions[vertex][2] =
                        Float.intBitsToFloat(
                                quadData[base + 2]);
                uvs[vertex][0] =
                        Float.intBitsToFloat(
                                quadData[base + 4]);
                uvs[vertex][1] =
                        Float.intBitsToFloat(
                                quadData[base + 5]);
            }
            return this;
        }

        @Override
        public QuadEmitter fromVanilla(
                BakedQuad quad,
                RenderMaterial material,
                Direction cullFace) {
            fromVanilla(quad.getVertices(), 0);
            this.material = material;
            this.colorIndex = quad.getTintIndex();
            return this;
        }

        @Override
        public QuadEmitter colorIndex(int colorIndex) {
            this.colorIndex = colorIndex;
            return this;
        }

        @Override
        public QuadEmitter material(
                RenderMaterial material) {
            this.material = material;
            return this;
        }

        @Override
        public QuadEmitter emit() {
            float[][] positionsCopy =
                    new float[4][3];
            float[][] uvsCopy = new float[4][2];
            for (int vertex = 0;
                    vertex < 4;
                    vertex++) {
                System.arraycopy(
                        positions[vertex],
                        0,
                        positionsCopy[vertex],
                        0,
                        3);
                System.arraycopy(
                        uvs[vertex],
                        0,
                        uvsCopy[vertex],
                        0,
                        2);
            }
            emittedQuads.add(new QuadData(
                    positionsCopy,
                    uvsCopy,
                    colorIndex,
                    material,
                    squareArgs));
            squareArgs = null;
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
            return uvs[vertexIndex][0];
        }

        @Override
        public float v(int vertexIndex) {
            return uvs[vertexIndex][1];
        }

        @Override
        public int color(int vertexIndex) {
            return 0xFFFFFFFF;
        }

        @Override
        public int lightmap(int vertexIndex) {
            return 0;
        }

        @Override
        public int colorIndex() {
            return colorIndex;
        }

        @Override
        public RenderMaterial material() {
            return material;
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
            return positions[vertexIndex][coordinateIndex];
        }

        @Override
        public Vector3f copyPos(
                int vertexIndex,
                Vector3f target) {
            return target.set(
                    positions[vertexIndex][0],
                    positions[vertexIndex][1],
                    positions[vertexIndex][2]);
        }

        @Override
        public Vector2f copyUv(
                int vertexIndex,
                Vector2f target) {
            return target.set(
                    uvs[vertexIndex][0],
                    uvs[vertexIndex][1]);
        }

        @Override
        public void toVanilla(
                int[] vertices,
                int vertexIndex) {
        }

        @Override
        public QuadEmitter color(
                int vertexIndex,
                int color) {
            return this;
        }

        @Override
        public QuadEmitter spriteBake(
                TextureAtlasSprite sprite,
                int bakeFlags) {
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
        public QuadEmitter tag(int tag) {
            return this;
        }

        @Override
        public QuadEmitter copyFrom(QuadView quad) {
            return this;
        }
    }

    private record QuadData(
            float[][] positions,
            float[][] uvs,
            int colorIndex,
            RenderMaterial material,
            float[] squareArgs) {}
}
