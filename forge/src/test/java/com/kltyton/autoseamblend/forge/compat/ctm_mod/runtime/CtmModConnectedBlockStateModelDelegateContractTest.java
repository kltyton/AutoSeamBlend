package com.kltyton.autoseamblend.forge.compat.ctm_mod.runtime;

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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 测试——包装器必须把 5 参 getQuads 转发给 delegate 的 5 参，并在
 * getModelData 中先调用 delegate 再叠加自己的 ModelProperty。当前实现三处调用
 * delegate 3 参、getModelData 不先委托，本测试应失败。
 *
 * English: RED test. The wrapper must forward the 5-arg getQuads to the delegate's
 * 5-arg overload and must call the delegate's getModelData before deriving its own
 * property. The current implementation calls the delegate's 3-arg overload in three
 * places and never delegates in getModelData, so this test is expected to fail.
 */
class CtmModConnectedBlockStateModelDelegateContractTest {
    private static final ModelProperty<String> DELEGATE_PROPERTY =
            new ModelProperty<>();

    @BeforeAll
    static void bootstrapRegistries() {
        // 中文：独立 JVM 测试需要 FML LoadingModList 桩与注册表引导，否则
        // FeatureFlags/Blocks 静态初始化抛 ExceptionInInitializerError；空加载表
        // 足够引导原版注册表；Util.fetchChoiceType 还需游戏版本，先注入内建版本。
        // English: Standalone JVM tests need an FML LoadingModList stub and vanilla
        // registry bootstrapping or FeatureFlags/Blocks static initializers throw
        // ExceptionInInitializerError; an empty loading table suffices for vanilla
        // registries, and Util.fetchChoiceType needs a game version, so the built-in
        // version is injected first. Test-only initialization, never triggers a
        // resource reload.
        ForgeTestBootstrap.bootStrap();
    }

    @Test
    void forwardsFiveArgQuadsToDelegateWhenQueryContextMissing() {
        // 中文：modelData 不含 QUERY_CONTEXT 时，5 参入口必须把同一 ModelData/RenderType
        // 交给 delegate 的 5 参，且不得回落 3 参（3 参丢失 ModelData/RenderType）。
        // English: When modelData lacks QUERY_CONTEXT, the 5-arg entry must forward the
        // same ModelData/RenderType to the delegate's 5-arg overload and must not fall
        // back to the 3-arg overload (which drops ModelData/RenderType).
        RecordingDelegate delegate = new RecordingDelegate();
        BlockState state = Blocks.STONE.defaultBlockState();
        CtmModConnectedBlockStateModel wrapper =
                new CtmModConnectedBlockStateModel(delegate, state);
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

        assertTrue(
                delegate.fiveArgCalled,
                "delegate 5-arg getQuads must be invoked");
        assertSame(
                modelData,
                delegate.lastModelData,
                "delegate must receive the same ModelData");
        assertSame(
                renderType,
                delegate.lastRenderType,
                "delegate must receive the same RenderType");
        assertFalse(
                delegate.threeArgCalled,
                "delegate 3-arg getQuads must not be invoked");
    }

    @Test
    void getModelDataDelegatesAndPreservesDelegateProperty() {
        // 中文：getModelData 必须先调用 delegate.getModelData，再派生并保留 delegate
        // 写入的自定义 ModelProperty（26.1.2 语义下 CTM_CONTEXT 等由 delegate 链提供）。
        // English: getModelData must first call delegate.getModelData, then derive and
        // preserve the custom ModelProperty written by the delegate (in 26.1.2 semantics
        // CTM_CONTEXT-like data is supplied by the delegate chain).
        RecordingDelegate delegate = new RecordingDelegate();
        BlockState state = Blocks.STONE.defaultBlockState();
        CtmModConnectedBlockStateModel wrapper =
                new CtmModConnectedBlockStateModel(delegate, state);

        ModelData result = wrapper.getModelData(
                minimalLevel(),
                BlockPos.ZERO,
                state,
                ModelData.EMPTY);

        assertTrue(
                delegate.modelDataCalled,
                "delegate getModelData must be invoked");
        assertEquals(
                "kept",
                result.get(DELEGATE_PROPERTY),
                "delegate-written ModelProperty must be preserved");
    }

    /**
     * 中文：记录式 BakedModel 委托；仅记录调用，不触发任何重载/资源访问。
     * English: Recording BakedModel delegate; only records calls and never triggers
     * reloads or resource access.
     */
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

    /**
     * 中文：最小可用的 16x16 测试精灵；UV 归一化为 0..1，不依赖 Atlas 或 GL。
     * English: Minimal usable 16x16 test sprite with normalized 0..1 UVs; no Atlas
     * or GL dependency.
     */
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

    /**
     * 中文：最小 BlockAndTintGetter 测试替身；getModelData 只存储引用，不读取世界。
     * English: Minimal BlockAndTintGetter test double; getModelData only stores
     * references and never reads the world.
     */
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
