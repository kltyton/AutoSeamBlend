package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeProvider;
import earth.terrarium.athena.api.client.models.AthenaQuad;
import earth.terrarium.athena.api.client.utils.CtmState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.util.TriState;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Rotation;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：Athena overlay 在 Fabric loader 边界的 RED→GREEN 合同，严格移植已验收 NeoForge
 * 语义（AthenaNativeQuadProcessor.OVERLAY_OFFSET + AthenaRenderPassPolicy CUTOUT pass）：
 * (1) 发射前把 CUTOUT 材质设置到 QuadEmitter（BlendMode.CUTOUT，对应 Continuity Fabric
 * RenderUtil.findOverlayMaterial(BlendMode.CUTOUT, ...) 的既定做法）；
 * (2) 四个顶点沿 face.step() 向外偏移 1/2048，避免与 base 面共面 z-fighting；
 * (3) DonorTintResolver 返回的 ARGB（0xAARRGGBB）必须原样直通四个顶点；FRAPI
 * QuadEmitter.color 采用 ARGB，indigo 只在写入 vanilla 顶点缓冲时转换为 ABGR。
 * 测试通过窄 package-private seam（CUTOUT_MATERIAL_SOURCE + emitOverlayQuad）驱动真实发射
 * 路径，用记录式 QuadEmitter 观察 material/pos/color/emit 调用，而不是只断言常量。
 *
 * <p>English: RED→GREEN contract for Athena overlays on the Fabric loader boundary, porting
 * the accepted NeoForge semantics (AthenaNativeQuadProcessor.OVERLAY_OFFSET plus the
 * AthenaRenderPassPolicy CUTOUT pass): (1) a CUTOUT RenderMaterial (BlendMode.CUTOUT, the
 * same convention Continuity Fabric uses through RenderUtil.findOverlayMaterial) must be set
 * on the QuadEmitter before emission; (2) all four vertices must shift outward by 1/2048
 * along face.step() to avoid coplanar z-fighting with the base face; (3) the ARGB value
 * produced by DonorTintResolver (0xAARRGGBB) must pass through unchanged to all four vertices
 * because FRAPI QuadEmitter.color consumes ARGB and indigo converts to ABGR only at the
 * vanilla vertex boundary. The test drives the real emission seam (CUTOUT_MATERIAL_SOURCE +
 * emitOverlayQuad) and observes the QuadEmitter calls instead of asserting constants only.
 */
class FabricAthenaNativeQuadProcessorContractTest {
    private static final float OVERLAY_OFFSET = 1.0F / 2048.0F;
    private static final int ARGB = 0xFF112233;

    private final StubRenderMaterial cutoutMaterial =
            new StubRenderMaterial();
    private final RecordingQuadEmitter emitter =
            new RecordingQuadEmitter();

    @BeforeAll
    static void bootstrapRegistries() {
        // 中文：独立 JVM 测试需要游戏版本与注册表引导，否则 TextureAtlasSprite/
        // BuiltInRegistries 静态初始化抛 ExceptionInInitializerError。
        // English: Standalone JVM tests need a game version and registry bootstrap or
        // TextureAtlasSprite/BuiltInRegistries static initializers throw.
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @AfterEach
    void restoreMaterialSource() {
        // 中文：恢复生产默认的 FRAPI CUTOUT 材质来源，避免污染其他测试。
        // English: Restores the production FRAPI CUTOUT material source so other
        // tests never observe the injected seam.
        FabricAthenaNativeQuadProcessor.CUTOUT_MATERIAL_SOURCE.set(
                FabricAthenaNativeQuadProcessor
                        ::resolveCutoutMaterial);
    }

    @Test
    void overlayEmissionSetsCutoutMaterial() {
        FabricAthenaNativeQuadProcessor.CUTOUT_MATERIAL_SOURCE.set(
                () -> cutoutMaterial);
        emitOverlay();

        assertEquals(
                1,
                emitter.materials.size(),
                "exactly one material must be set on the emitter");
        assertSame(
                cutoutMaterial,
                emitter.materials.get(0),
                "the seam material must be applied to the emitter");
        assertEquals(
                BlendMode.CUTOUT,
                emitter.materials.get(0)
                        .blendMode(),
                "overlay quads must run in the CUTOUT layer");
        assertEquals(
                1,
                emitter.emits,
                "overlay quad must be emitted");
    }

    @Test
    void overlayVerticesShiftOutwardByOneOver2048() {
        FabricAthenaNativeQuadProcessor.CUTOUT_MATERIAL_SOURCE.set(
                () -> cutoutMaterial);
        emitOverlay();

        // 中文：square(4 基础顶点) + 偏移(4 顶点)，顺序配对比较位移。
        // English: square writes 4 base vertices, then the offset pass writes 4 more;
        // pairs are compared in call order.
        assertEquals(
                8,
                emitter.positionsX.size(),
                "square(4) plus offset(4)");
        for (int vertex = 0;
                vertex < 4;
                vertex++) {
            float dx = emitter.positionsX
                            .get(vertex + 4)
                    - emitter.positionsX.get(vertex);
            float dy = emitter.positionsY
                            .get(vertex + 4)
                    - emitter.positionsY.get(vertex);
            float dz = emitter.positionsZ
                            .get(vertex + 4)
                    - emitter.positionsZ.get(vertex);
            assertEquals(
                    0.0F,
                    dx,
                    1.0E-6F,
                    "UP overlay must not shift on x");
            assertEquals(
                    OVERLAY_OFFSET,
                    dy,
                    1.0E-6F,
                    "UP overlay must shift outward by 1/2048 on +y");
            assertEquals(
                    0.0F,
                    dz,
                    1.0E-6F,
                    "UP overlay must not shift on z");
        }
    }

    @Test
    void overlayTintPassesArgbThroughToFrapi() {
        FabricAthenaNativeQuadProcessor.CUTOUT_MATERIAL_SOURCE.set(
                () -> cutoutMaterial);
        emitOverlay();

        assertEquals(
                4,
                emitter.vertexColors.size(),
                "all four vertices must be colored");
        for (int vertex = 0;
                vertex < 4;
                vertex++) {
            assertEquals(
                    vertex,
                    emitter.colorVertexIndices.get(vertex),
                    "per-vertex color index order");
            assertEquals(
                    ARGB,
                    emitter.vertexColors.get(vertex),
                    "FRAPI QuadEmitter.color consumes ARGB; the resolver ARGB must pass through unchanged");
        }
        assertEquals(
                List.of(-1),
                emitter.colorIndices,
                "overlay must disable the color index");
    }

    /**
     * 中文：overlay 发射必须直接消费调用方传入的预计算 overlay CtmState，禁止丢弃后对
     * 接收方块重新普通采样；发射的精灵与象限序列必须等于 AthenaNativeProvider.quads(
     * 传入 overlayState, face, sprites) 的槽位组合。
     *
     * English: Overlay emission must consume the caller-supplied precomputed overlay
     * CtmState directly and must not discard it and re-sample the receiver with the plain
     * replacement predicate; the emitted sprite and quadrant sequence must equal
     * AthenaNativeProvider.quads(passed overlayState, face, sprites).
     */
    @Test
    void overlayReplacementEmitsPassedOverlayStateQuads() {
        FabricAthenaNativeQuadProcessor.CUTOUT_MATERIAL_SOURCE.set(
                () -> cutoutMaterial);
        CtmState overlayState = new CtmState(
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                false);
        assertFalse(
                overlayState.allTrue(),
                "the contract must exercise a non-allTrue overlay state");
        TextureAtlasSprite[] sprites = {
                sprite("minecraft:role0"),
                sprite("minecraft:role1"),
                sprite("minecraft:role2"),
                sprite("minecraft:role3"),
                sprite("minecraft:role4")};
        List<AthenaQuad> expected =
                AthenaNativeProvider.quads(
                        overlayState,
                        Direction.UP,
                        sprites);
        assertEquals(
                4,
                expected.size(),
                "a non-allTrue state must compose four quadrant quads");

        boolean emitted =
                FabricAthenaNativeQuadProcessor.emitOverlayReplacement(
                        Direction.UP,
                        overlayState,
                        sprites,
                        ARGB,
                        emitter);

        assertTrue(
                emitted,
                "overlay replacement must be emitted");
        assertEquals(
                expected.size(),
                emitter.emits,
                "one emitted quad per native AthenaQuad");
        List<TextureAtlasSprite> expectedSprites = expected
                .stream()
                .map(quad -> sprites[quad.sprite()])
                .toList();
        assertEquals(
                expectedSprites,
                emitter.bakedSprites,
                "emitted sprite sequence must follow the passed overlay state");
        List<SquareArgs> expectedBounds = expected
                .stream()
                .map(quad -> new SquareArgs(
                        quad.left(),
                        quad.bottom(),
                        quad.right(),
                        quad.top(),
                        quad.depth()))
                .toList();
        assertEquals(
                expectedBounds,
                emitter.squareBounds,
                "emitted quadrant bounds must follow the passed overlay state");
        assertTrue(
                emitter.materials.stream()
                        .allMatch(material -> material == cutoutMaterial),
                "every overlay quad must run in the CUTOUT layer");
        assertTrue(
                emitter.vertexColors.stream()
                        .allMatch(color -> color == ARGB),
                "every overlay vertex must receive the resolver ARGB unchanged");
        assertEquals(
                List.of(-1, -1, -1, -1),
                emitter.colorIndices,
                "overlay must disable the color index on every quad");
    }

    private void emitOverlay() {
        TextureAtlasSprite sprite = sprite(
                "minecraft:overlay");
        AthenaQuad quad = new AthenaQuad(
                1,
                0.0F,
                0.5F,
                1.0F,
                0.5F,
                Rotation.NONE,
                0.0F);
        FabricAthenaNativeQuadProcessor.emitOverlayQuad(
                emitter,
                Direction.UP,
                quad,
                sprite,
                ARGB);
    }

    /** 中文：位于假定 2048x2048 Atlas 原点的 16x16 测试精灵；不依赖 Atlas 或 GL。 / English: 16x16 test sprite at the assumed 2048x2048 atlas origin; no Atlas or GL dependency. */
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

    /** 中文：square(face,left,bottom,right,top,depth) 的只读参数记录。 / English: Read-only record of square(face,left,bottom,right,top,depth) arguments. */
    private record SquareArgs(
            float left,
            float bottom,
            float right,
            float top,
            float depth) {}

    /** 中文：记录式 QuadEmitter；观察 material/pos/color/emit 的真实调用序列。 / English: Recording QuadEmitter that observes the real material/pos/color/emit call sequence. */
    private static final class RecordingQuadEmitter
            implements QuadEmitter {
        private final List<RenderMaterial> materials =
                new ArrayList<>();
        private final List<Float> positionsX =
                new ArrayList<>();
        private final List<Float> positionsY =
                new ArrayList<>();
        private final List<Float> positionsZ =
                new ArrayList<>();
        private final List<Integer> vertexColors =
                new ArrayList<>();
        private final List<Integer> colorVertexIndices =
                new ArrayList<>();
        private final List<Integer> colorIndices =
                new ArrayList<>();
        private final List<TextureAtlasSprite> bakedSprites =
                new ArrayList<>();
        private final List<Integer> bakeFlags =
                new ArrayList<>();
        private final List<SquareArgs> squareBounds =
                new ArrayList<>();
        private int emits;

        @Override
        public QuadEmitter pos(
                int vertexIndex,
                float x,
                float y,
                float z) {
            positionsX.add(x);
            positionsY.add(y);
            positionsZ.add(z);
            return this;
        }

        @Override
        public QuadEmitter color(
                int vertexIndex,
                int color) {
            colorVertexIndices.add(vertexIndex);
            vertexColors.add(color);
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
        public QuadEmitter spriteBake(
                TextureAtlasSprite sprite,
                int bakeFlags) {
            bakedSprites.add(sprite);
            this.bakeFlags.add(bakeFlags);
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
            squareBounds.add(new SquareArgs(
                    left,
                    bottom,
                    right,
                    top,
                    depth));
            return QuadEmitter.super.square(
                    nominalFace,
                    left,
                    bottom,
                    right,
                    top,
                    depth);
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
        public QuadEmitter material(
                RenderMaterial material) {
            materials.add(material);
            return this;
        }

        @Override
        public QuadEmitter colorIndex(int colorIndex) {
            colorIndices.add(colorIndex);
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
        public QuadEmitter emit() {
            emits++;
            return this;
        }

        @Override
        public float x(int vertexIndex) {
            return positionsX.get(vertexIndex);
        }

        @Override
        public float y(int vertexIndex) {
            return positionsY.get(vertexIndex);
        }

        @Override
        public float z(int vertexIndex) {
            return positionsZ.get(vertexIndex);
        }

        @Override
        public float posByIndex(
                int vertexIndex,
                int coordinateIndex) {
            return new float[] {
                            x(vertexIndex),
                            y(vertexIndex),
                            z(vertexIndex)
                    }[coordinateIndex];
        }

        @Override
        public Vector3f copyPos(
                int vertexIndex,
                Vector3f target) {
            return target.set(
                    x(vertexIndex),
                    y(vertexIndex),
                    z(vertexIndex));
        }

        @Override
        public int color(int vertexIndex) {
            return vertexColors.get(vertexIndex);
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
        public Vector2f copyUv(
                int vertexIndex,
                Vector2f target) {
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
        public Vector3f copyNormal(
                int vertexIndex,
                Vector3f target) {
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
        public Vector3f faceNormal() {
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

    /** 中文：最小 CUTOUT RenderMaterial stub；仅测试 seam 注入用。 / English: Minimal CUTOUT RenderMaterial stub used only for seam injection in tests. */
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

    }
}
