package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeOwnershipPolicy;
import com.kltyton.autoseamblend.engine.query.AcceptedNativeDocument;
import com.kltyton.autoseamblend.engine.query.NativeDocumentIdentity;
import com.kltyton.autoseamblend.engine.query.NativeQueryObservation;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import earth.terrarium.athena.api.client.models.AthenaBlockModel;
import earth.terrarium.athena.api.client.models.AthenaQuad;
import earth.terrarium.athena.api.client.utils.AppearanceAndTintGetter;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：Fabric Athena 所有权 RED 合同。锁定 26.1.2/NeoForge 已修复语义：missing 精灵
 * noMatch；predicate 前置；原生发射 slot 经 textures 表映射后按同名精灵判定；owns 且
 * 身份可解析→精确文档，身份不可解析→明确 unknown，绝不 exact(empty)（1.21.1 该构造
 * 抛 IllegalArgumentException）；identity 由 BuiltInRegistries 块 ID + Fabric
 * FactoryManagerImpl.LOADERS 键空间 + common resolver 接线。本测试锁定 Fabric 专属
 * 接线（native slot 映射、missing/predicate 编排、identity 来源）与 common 裁决的
 * 编排一致性；common 策略本身的裁决三态由 common 合同测试承担，不在此重复。
 *
 * <p>English: Fabric Athena ownership RED contract. Locks the 26.1.2/NeoForge-fixed
 * semantics: missing sprites yield noMatch; the predicate is checked first; native emitted
 * slots are mapped through the textures table and matched by same-name sprites; owned with
 * a resolvable identity yields an exact document, owned without one yields an explicit
 * unknown, and exact(empty) is never produced (that construction throws
 * IllegalArgumentException on 1.21.1); identity is wired from the BuiltInRegistries block
 * id plus the Fabric FactoryManagerImpl.LOADERS key space through the common resolver. This
 * test locks the Fabric-specific wiring (native slot mapping, missing/predicate
 * orchestration, identity sources) and the orchestration consistency with the common
 * policy; the policy's own adjudication tri-state is covered by the common contract tests
 * and is not duplicated here.
 */
class FabricAthenaNativeModelOwnershipProviderContractTest {

    @BeforeAll
    static void bootstrapRegistries() {
        // 中文：独立 JVM 测试需要游戏版本与注册表引导，否则 FeatureFlags/Blocks/
        // BuiltInRegistries 静态初始化抛 ExceptionInInitializerError；Fabric 测试类路径
        // 无 NeoForge LoadingModList，纯 vanilla 引导即可；仅测试初始化。
        // English: Standalone JVM tests need a game version and registry bootstrap or
        // FeatureFlags/Blocks/BuiltInRegistries static initializers throw
        // ExceptionInInitializerError; the Fabric test classpath has no NeoForge
        // LoadingModList, so plain vanilla bootstrapping suffices; test-only init.
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void predicateIsCheckedBeforeSpriteOwnershipThroughObserveProbe() {
        BlockState state = Blocks.STONE.defaultBlockState();
        TextureAtlasSprite rendered =
                sprite("minecraft:rendered");
        Int2ObjectMap<TextureAtlasSprite> textures =
                new Int2ObjectArrayMap<>();
        textures.put(0, rendered);
        NativeDocumentIdentity identity =
                NativeDocumentIdentity.resourceOnly(
                        "minecraft:blockstates/stone.json");
        // 中文：拒绝 predicate 即使候选同名也必须 noMatch（predicate 前置）。
        // English: A rejecting predicate must yield noMatch even when a candidate
        // shares the rendered sprite name (predicate checked first).
        NativeQueryObservation rejected =
                FabricAthenaNativeModelOwnershipProvider
                        .observeProbe(
                                new FabricAthenaNativeModelOwnershipProvider
                                        .QueryProbe(
                                                (left, right) -> false,
                                                fakeModel(0),
                                                textures,
                                                Optional.of(identity)),
                                minimalLevel(),
                                BlockPos.ZERO,
                                state,
                                quad(Direction.NORTH),
                                rendered);
        assertTrue(rejected.acceptedDocuments().isEmpty());
        assertTrue(rejected.unknownDiagnostic().isEmpty());
        // 中文：null predicate 视为接受，随后走 common 同名裁决并发布精确文档。
        // English: A null predicate accepts and the common same-name adjudication then
        // publishes the exact document.
        NativeQueryObservation owned =
                FabricAthenaNativeModelOwnershipProvider
                        .observeProbe(
                                new FabricAthenaNativeModelOwnershipProvider
                                        .QueryProbe(
                                                null,
                                                fakeModel(0),
                                                textures,
                                                Optional.of(identity)),
                                minimalLevel(),
                                BlockPos.ZERO,
                                state,
                                quad(Direction.NORTH),
                                rendered);
        assertEquals(
                List.of(
                        AcceptedNativeDocument.identityOnly(identity)),
                owned.acceptedDocuments());
        assertTrue(owned.unknownDiagnostic().isEmpty());
    }

    @Test
    void missingSpriteObservesNoMatch() {
        TextureAtlasSprite missing = new TestSprite(
                TextureAtlas.LOCATION_BLOCKS,
                MissingTextureAtlasSprite.create());
        NativeQueryObservation observed =
                FabricAthenaNativeModelOwnershipProvider.observeProbe(
                        probeWithTextures(
                                List.of(),
                                new Int2ObjectArrayMap<>()),
                        minimalLevel(),
                        BlockPos.ZERO,
                        Blocks.STONE.defaultBlockState(),
                        quad(Direction.NORTH),
                        missing);
        assertTrue(observed.acceptedDocuments().isEmpty());
        assertTrue(observed.unknownDiagnostic().isEmpty());
    }

    @Test
    void nativeSlotMappingYieldsCandidatesAndMatchesRenderedSprite() {
        TextureAtlasSprite rendered =
                sprite("minecraft:rendered");
        TextureAtlasSprite other =
                sprite("minecraft:other");
        Int2ObjectMap<TextureAtlasSprite> textures =
                new Int2ObjectArrayMap<>();
        textures.put(0, rendered);
        textures.put(1, null);
        textures.put(2, other);
        BlockState state = Blocks.STONE.defaultBlockState();

        List<TextureAtlasSprite> candidates =
                FabricAthenaNativeModelOwnershipProvider
                        .nativeSprites(
                                fakeModel(0, 1, 2),
                                textures,
                                minimalLevel(),
                                BlockPos.ZERO,
                                state,
                                Direction.NORTH);

        assertEquals(
                List.of(rendered, other),
                candidates,
                "null texture slots must be dropped");
        assertTrue(
                AthenaNativeOwnershipPolicy.ownsByCandidateSprites(
                        candidates,
                        rendered));
        assertFalse(
                AthenaNativeOwnershipPolicy.ownsByCandidateSprites(
                        candidates,
                        sprite("minecraft:unrelated")));

        NativeQueryObservation observed =
                FabricAthenaNativeModelOwnershipProvider.observeProbe(
                        new FabricAthenaNativeModelOwnershipProvider.QueryProbe(
                                null,
                                fakeModel(0, 1, 2),
                                textures,
                                Optional.of(
                                        NativeDocumentIdentity.resourceOnly(
                                                "minecraft:blockstates/stone.json"))),
                        minimalLevel(),
                        BlockPos.ZERO,
                        state,
                        quad(Direction.NORTH),
                        rendered);
        assertEquals(
                List.of(
                        AcceptedNativeDocument.identityOnly(
                                NativeDocumentIdentity.resourceOnly(
                                        "minecraft:blockstates/stone.json"))),
                observed.acceptedDocuments(),
                "same-named candidate must claim exact ownership");
    }

    @Test
    void nullFaceOrNullTexturesYieldNoCandidates() {
        Int2ObjectMap<TextureAtlasSprite> textures =
                new Int2ObjectArrayMap<>();
        textures.put(0, sprite("minecraft:rendered"));
        BlockState state = Blocks.STONE.defaultBlockState();
        assertTrue(
                FabricAthenaNativeModelOwnershipProvider
                        .nativeSprites(
                                fakeModel(0),
                                null,
                                minimalLevel(),
                                BlockPos.ZERO,
                                state,
                                Direction.NORTH)
                        .isEmpty());
        assertTrue(
                FabricAthenaNativeModelOwnershipProvider
                        .nativeSprites(
                                fakeModel(0),
                                textures,
                                minimalLevel(),
                                BlockPos.ZERO,
                                state,
                                null)
                        .isEmpty());
    }

    @Test
    void identityWiringUsesBlockIdAndFactoryManagerLoaderIds() {
        BlockState state = Blocks.STONE.defaultBlockState();
        assertEquals(
                new ResourceLocation("minecraft:stone"),
                FabricAthenaNativeModelOwnershipProvider.blockId(state),
                "block id must come from BuiltInRegistries.BLOCK.getKey");
        Optional<NativeDocumentIdentity> identity =
                FabricAthenaNativeModelOwnershipProvider
                        .resolveIdentity(state);
        // 中文：JUnit 下 AthenaResourceLoader 数据表为空，且 loaderIds 可能为空表，
        // 解析器必须确定性地返回 empty 且不抛异常；接线（块 ID→loaderIds→common
        // resolver）本身被本测试锁定。不断言 loaderIds 内容，避免环境耦合。
        // English: In JUnit the AthenaResourceLoader data table is empty and loaderIds may
        // be an empty table, so the resolver must deterministically return empty without
        // throwing; the wiring (block id -> loaderIds -> common resolver) itself is locked
        // by this test. The loaderIds contents are deliberately not asserted to avoid
        // environment coupling.
        assertTrue(identity.isEmpty());
    }

    private static FabricAthenaNativeModelOwnershipProvider.QueryProbe
            probeWithTextures(
                    List<AthenaQuad> quads,
                    Int2ObjectMap<TextureAtlasSprite> textures) {
        return new FabricAthenaNativeModelOwnershipProvider.QueryProbe(
                null,
                fakeModelFrom(quads),
                textures,
                Optional.empty());
    }

    private static AthenaBlockModel fakeModel(int... slots) {
        List<AthenaQuad> quads = java.util.Arrays.stream(slots)
                .mapToObj(AthenaQuad::withSprite)
                .toList();
        return fakeModelFrom(quads);
    }

    private static AthenaBlockModel fakeModelFrom(
            List<AthenaQuad> quads) {
        return new AthenaBlockModel() {
            @Override
            public List<AthenaQuad> getQuads(
                    AppearanceAndTintGetter getter,
                    BlockState state,
                    BlockPos pos,
                    Direction face) {
                return quads;
            }

            @Override
            public Int2ObjectMap<TextureAtlasSprite> getTextures(
                    Function<Material, TextureAtlasSprite> getter) {
                return new Int2ObjectArrayMap<>();
            }
        };
    }

    private static BakedQuad quad(Direction direction) {
        VertexFormat format = DefaultVertexFormat.BLOCK;
        int stride = format.getVertexSize() / 4;
        return new BakedQuad(
                new int[stride * 4],
                -1,
                direction,
                sprite("minecraft:rendered"),
                false);
    }

    private static TextureAtlasSprite sprite(String name) {
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

    /** 中文：位于假定 2048x2048 Atlas 原点的 16x16 测试精灵；不依赖 Atlas 或 GL。 / English: 16x16 test sprite at the assumed 2048x2048 atlas origin; no Atlas or GL dependency. */
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

    /** 中文：最小 BlockAndTintGetter 替身；nativeSprites 只转发引用，不读取世界。 / English: Minimal BlockAndTintGetter double; nativeSprites only forwards references and never reads the world. */
    private static BlockAndTintGetter minimalLevel() {
        return new BlockAndTintGetter() {
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
        };
    }
}
