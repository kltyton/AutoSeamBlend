package com.kltyton.autoseamblend.forge.compat.fusion.runtime;

import com.kltyton.autoseamblend.forge.testing.ForgeTestBootstrap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：Fusion 包装器必须保留 Forge delegate 的 ModelData 与 RenderType 合同。
 *
 * <p>English: The Fusion wrapper must preserve the Forge delegate's ModelData and
 * RenderType contracts.
 */
class FusionConnectedBlockStateModelDelegateContractTest {
    private static final ModelProperty<String> DELEGATE_PROPERTY =
            new ModelProperty<>();

    @BeforeAll
    static void bootstrapRegistries() {
        // 中文：独立 JVM 测试需先注入游戏版本、空模组加载表并引导原版注册表。
        // English: Standalone JVM tests require a game version, an empty mod list,
        // and vanilla registry bootstrapping.
        ForgeTestBootstrap.bootStrap();
    }

    @Test
    void forwardsFiveArgQuadsToDelegateWhenQueryContextMissing() {
        // 中文：缺少本包装器上下文时必须把同一 ModelData/RenderType 交给 delegate
        // 的五参数入口，不能回落到会丢失两者的三参数入口。
        // English: Without this wrapper's context, the same ModelData/RenderType must
        // reach the delegate's five-argument entry instead of the lossy three-argument one.
        RecordingDelegate delegate = new RecordingDelegate();
        BlockState state = Blocks.STONE.defaultBlockState();
        FusionConnectedBlockStateModel wrapper =
                new FusionConnectedBlockStateModel(delegate, state);
        ModelData modelData = ModelData.builder()
                .with(new ModelProperty<String>(), "unrelated")
                .build();
        RenderType renderType = RenderType.cutout();

        wrapper.getQuads(
                state,
                Direction.NORTH,
                RandomSource.create(0),
                modelData,
                renderType);

        assertTrue(delegate.fiveArgCalled);
        assertSame(modelData, delegate.lastModelData);
        assertSame(renderType, delegate.lastRenderType);
        assertFalse(delegate.threeArgCalled);
    }

    @Test
    void getModelDataDelegatesAndPreservesDelegateProperty() {
        // 中文：必须先调用 delegate.getModelData，再派生并保留 delegate 写入的属性。
        // English: delegate.getModelData must run first and its property must survive
        // derivation by this wrapper.
        RecordingDelegate delegate = new RecordingDelegate();
        BlockState state = Blocks.STONE.defaultBlockState();
        FusionConnectedBlockStateModel wrapper =
                new FusionConnectedBlockStateModel(delegate, state);

        ModelData result = wrapper.getModelData(
                minimalLevel(),
                BlockPos.ZERO,
                state,
                ModelData.EMPTY);

        assertTrue(delegate.modelDataCalled);
        assertEquals("kept", result.get(DELEGATE_PROPERTY));
    }

    /** 中文：仅记录调用的模型替身。 / English: Recording model test double. */
    private static final class RecordingDelegate implements BakedModel {
        private boolean threeArgCalled;
        private boolean fiveArgCalled;
        private boolean modelDataCalled;
        private ModelData lastModelData;
        private RenderType lastRenderType;

        @Override
        public List<BakedQuad> getQuads(
                BlockState state,
                Direction direction,
                RandomSource random) {
            threeArgCalled = true;
            return List.of();
        }

        @Override
        public List<BakedQuad> getQuads(
                BlockState state,
                Direction direction,
                RandomSource random,
                ModelData modelData,
                RenderType renderType) {
            fiveArgCalled = true;
            lastModelData = modelData;
            lastRenderType = renderType;
            return List.of();
        }

        @Override
        public ModelData getModelData(
                BlockAndTintGetter level,
                BlockPos pos,
                BlockState state,
                ModelData existing) {
            modelDataCalled = true;
            return existing.derive()
                    .with(DELEGATE_PROPERTY, "kept")
                    .build();
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
            return TestSprite.INSTANCE;
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }

    /** 中文：不依赖 Atlas/GL 的 16x16 测试精灵。 / English: 16x16 test sprite without Atlas/GL access. */
    private static final class TestSprite extends TextureAtlasSprite {
        private static final TestSprite INSTANCE = new TestSprite();

        private TestSprite() {
            super(
                    TextureAtlas.LOCATION_BLOCKS,
                    MissingTextureAtlasSprite.create(),
                    16,
                    16,
                    0,
                    0);
        }
    }

    /** 中文：getModelData 使用的最小世界替身。 / English: Minimal world test double for getModelData. */
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
