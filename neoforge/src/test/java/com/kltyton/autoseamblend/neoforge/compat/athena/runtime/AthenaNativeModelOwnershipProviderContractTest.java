package com.kltyton.autoseamblend.neoforge.compat.athena.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.NativeImage;
import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeOwnershipPolicy;
import com.kltyton.autoseamblend.engine.query.NativeQueryObservation;
import earth.terrarium.athena.api.client.models.AthenaBlockModel;
import earth.terrarium.athena.api.client.models.AthenaQuad;
import earth.terrarium.athena.api.client.utils.AppearanceAndTintGetter;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：AthenaNativeModelOwnershipProvider 的 NeoForge 绑定合同测试。纯裁决逻辑已迁到
 * common AthenaNativeOwnershipPolicy（common 侧另有三项合同测试）；本文件只保留依赖
 * WrappedGetter/AthenaBlockModel/textures 槽提取的 nativeSprites 链，并调用 common policy
 * 完成裁决，不链接真实 AthenaBakedModel 实例。
 *
 * <p>English: NeoForge-bound contract tests for AthenaNativeModelOwnershipProvider. The pure
 * adjudication moved to the common AthenaNativeOwnershipPolicy (three contract tests live on
 * the common side); this file keeps only the nativeSprites chain that depends on
 * WrappedGetter/AthenaBlockModel/textures slot extraction and completes the verdict through
 * the common policy, linking no real AthenaBakedModel instance.
 */
class AthenaNativeModelOwnershipProviderContractTest {
    private static final BlockPos POS = BlockPos.ZERO;
    private static final Direction FACE = Direction.NORTH;

    @BeforeAll
    static void bootstrapRegistries() {
        // 中文：独立 JVM 测试需要游戏版本与注册表引导，避免静态初始化抛
        // ExceptionInInitializerError；与 neoforge runtime/render 测试构造方式一致。
        // English: Standalone JVM tests need a game version and registry bootstrap to avoid
        // ExceptionInInitializerError; same construction pattern as the neoforge
        // runtime/render tests.
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        LoadingModList.of(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of());
        Bootstrap.bootStrap();
    }

    @Test
    void nativeSpritesMapsQuadSpriteIndicesThroughTexturesAndDropsNulls() {
        TextureAtlasSprite first = sprite("minecraft:first");
        TextureAtlasSprite second = sprite("minecraft:second");
        Int2ObjectMap<TextureAtlasSprite> textures =
                new Int2ObjectOpenHashMap<>();
        textures.put(1, first);
        textures.put(2, null);

        List<TextureAtlasSprite> candidates =
                AthenaNativeModelOwnershipProvider.nativeSprites(
                        modelWithQuads(List.of(
                                AthenaQuad.withSprite(1),
                                AthenaQuad.withSprite(2),
                                AthenaQuad.withSprite(3))),
                        textures,
                        LEVEL,
                        POS,
                        state(),
                        FACE);

        assertEquals(
                List.of(first),
                candidates,
                "quad sprite indices must map through textures with nulls dropped");
    }

    @Test
    void fullNativeOwnershipChainPrefersUnknownOverExactEmpty() {
        // 中文：完整链：原生发射映射候选 → 面/精灵命中 → identity 缺失 → unknown 而非
        // exact(empty)，也不抛异常。
        // English: Full chain: native emission maps candidates, face/sprite matches, identity
        // is absent, so the observation is an explicit unknown, never exact(empty) or a throw.
        TextureAtlasSprite rendered = sprite("minecraft:rendered");
        Int2ObjectMap<TextureAtlasSprite> textures =
                new Int2ObjectOpenHashMap<>();
        textures.put(0, rendered);
        List<TextureAtlasSprite> candidates =
                AthenaNativeModelOwnershipProvider.nativeSprites(
                        modelWithQuads(List.of(
                                AthenaQuad.withSprite(0))),
                        textures,
                        LEVEL,
                        POS,
                        state(),
                        FACE);
        boolean owns =
                AthenaNativeOwnershipPolicy.ownsByCandidateSprites(
                        candidates,
                        rendered);
        NativeQueryObservation observation =
                AthenaNativeOwnershipPolicy.resolveObservation(
                        owns,
                        Optional.empty());
        assertTrue(owns);
        assertTrue(observation.acceptedDocuments().isEmpty());
        assertEquals(
                Optional.of(
                        "ATHENA_ACCEPTED_MODEL_DOCUMENT_IDENTITY_UNAVAILABLE"),
                observation.unknownDiagnostic());
        assertThrows(
                IllegalArgumentException.class,
                () -> NativeQueryObservation.exact(
                        List.of()));
    }

    /** 中文：给定 quad 列表的 Athena 模型替身；忽略 getter。 / English: Athena model stub returning the given quads; ignores the getter. */
    private static AthenaBlockModel modelWithQuads(
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
                    Function<
                                    net.minecraft.client.resources
                                            .model.Material,
                                    TextureAtlasSprite>
                            spriteGetter) {
                return new Int2ObjectOpenHashMap<>();
            }
        };
    }

    private static final BlockAndTintGetter LEVEL =
            new BlockAndTintGetter() {
                @Override
                public BlockState getBlockState(BlockPos pos) {
                    return Blocks.AIR.defaultBlockState();
                }

                @Override
                public BlockEntity getBlockEntity(BlockPos pos) {
                    return null;
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
                public int getBlockTint(
                        BlockPos pos,
                        ColorResolver resolver) {
                    return -1;
                }

                @Override
                public LevelLightEngine getLightEngine() {
                    return null;
                }

                @Override
                public float getShade(
                        Direction direction,
                        boolean shade) {
                    return 1.0F;
                }
            };

    /** 中文：延迟到 Bootstrap 后求值，避免静态字段触发 Blocks 早期类加载。 / English: Evaluated lazily after Bootstrap to avoid an early Blocks class-load via a static field. */
    private static BlockState state() {
        return Blocks.STONE.defaultBlockState();
    }

    /** 中文：位于假定 2048x2048 Atlas 原点的 16x16 真实（非 missing）测试精灵。 / English: 16x16 real (non-missing) test sprite at the assumed 2048x2048 atlas origin. */
    private static TextureAtlasSprite sprite(String name) {
        NativeImage image = new NativeImage(16, 16, false);
        SpriteContents contents = new SpriteContents(
                ResourceLocation.parse(name),
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
