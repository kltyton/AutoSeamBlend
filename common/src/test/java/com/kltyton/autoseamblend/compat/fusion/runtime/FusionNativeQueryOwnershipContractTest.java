package com.kltyton.autoseamblend.compat.fusion.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kltyton.autoseamblend.engine.query.AcceptedNativeDocument;
import com.kltyton.autoseamblend.engine.query.NativeDocumentIdentity;
import com.kltyton.autoseamblend.engine.query.NativeQueryObservation;
import com.mojang.blaze3d.platform.NativeImage;
import com.supermartijn642.fusion.api.texture.DefaultTextureTypes;
import com.supermartijn642.fusion.api.texture.TextureType;
import com.supermartijn642.fusion.api.texture.custom.SpriteInstance;
import com.supermartijn642.fusion.api.texture.custom.TextureInstance;
import com.supermartijn642.fusion.extensions.TextureAtlasSpriteExtension;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 合同——Fusion 原生 base/fixed 精确查询必须获得 tier-1 claim。旧实现
 * {@link FusionNativeQueryOwnership#observe} 对非 CONNECTING 精灵直接 noMatch，导致已接受
 * 文档 + probe 匹配的 BASE 精灵（FIXED 在 FusionMethodMapping 中映射为 fusion:base）永远
 * 无 exact 文档；修复后 BASE 精灵仅在 probe/catalog 确实匹配时走 exact 路径，未拥有/普通
 * 精灵仍 noMatch。测试隔离 ownership 实例状态，每个用例使用独立实例。
 *
 * <p>English: RED contract -- native Fusion base/fixed exact queries must earn a tier-1 claim.
 * The old FusionNativeQueryOwnership.observe returned noMatch for every non-CONNECTING sprite,
 * so a BASE sprite (FIXED maps to fusion:base in FusionMethodMapping) with a matching probe and
 * accepted document never produced an exact observation; after the fix a BASE sprite only takes
 * the exact path when the probe and catalog really match, while unowned and ordinary sprites
 * still return noMatch. Each case uses its own ownership instance.
 */
class FusionNativeQueryOwnershipContractTest {
    private static final long GENERATION = 5L;
    private static final BlockAndTintGetter LEVEL = minimalLevel();
    private static final BlockPos POS = BlockPos.ZERO;

    @BeforeAll
    static void bootstrapRegistries() {
        // 中文：独立 JVM 测试需要游戏版本与注册表引导，否则 Blocks 静态初始化抛异常。
        // English: Standalone JVM tests need a game version and registry bootstrap or Blocks
        // static initialization throws.
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void claimsExactOwnershipForBaseSpriteWithAcceptedDocumentAndProbe() {
        BlockState state = Blocks.GLASS.defaultBlockState();
        NativeDocumentIdentity identity =
                NativeDocumentIdentity.resourceOnly(
                        "minecraft:fusion/base_test.json");
        FusionNativeQueryOwnership ownership =
                new FusionNativeQueryOwnership();
        ownership.beginModelCapture(
                GENERATION,
                new FusionAcceptedModifierDocumentCatalog.Snapshot(
                        GENERATION,
                        Map.of(state, List.of(identity))));
        ownership.captureModel(
                state,
                (level, pos, capturedState, face, sprite) -> true);
        ownership.endModelCapture();

        NativeQueryObservation observed =
                ownership.observe(
                        GENERATION,
                        LEVEL,
                        POS,
                        state,
                        Direction.UP,
                        sprite(DefaultTextureTypes.BASE));

        // 中文：BASE 是 Fusion API 中表示 FIXED 的对应类型（FusionMethodMapping 的 FIXED
        // 路由为 fusion:base）；probe 与已接受文档都匹配时必须是 exact，旧实现返回 noMatch。
        // English: BASE is the Fusion API type standing for FIXED (FusionMethodMapping routes
        // FIXED to fusion:base); with a matching probe and accepted document the observation
        // must be exact, while the old implementation returned noMatch.
        assertEquals(
                1,
                observed.acceptedDocuments().size(),
                "a matched base/fixed sprite must claim its accepted document");
        assertEquals(
                List.of(AcceptedNativeDocument.identityOnly(identity)),
                observed.acceptedDocuments());
        assertEquals(
                Optional.empty(),
                observed.unknownDiagnostic());
    }

    @Test
    void keepsNoMatchForUnownedBaseSprite() {
        BlockState owned = Blocks.GLASS.defaultBlockState();
        BlockState unowned = Blocks.STONE.defaultBlockState();
        FusionNativeQueryOwnership ownership =
                new FusionNativeQueryOwnership();
        ownership.beginModelCapture(
                GENERATION,
                new FusionAcceptedModifierDocumentCatalog.Snapshot(
                        GENERATION,
                        Map.of(owned, List.of(
                                NativeDocumentIdentity.resourceOnly(
                                        "minecraft:fusion/base_test.json")))));
        ownership.captureModel(
                owned,
                (level, pos, capturedState, face, sprite) -> true);
        ownership.endModelCapture();

        NativeQueryObservation observed =
                ownership.observe(
                        GENERATION,
                        LEVEL,
                        POS,
                        unowned,
                        Direction.UP,
                        sprite(DefaultTextureTypes.BASE));

        // 中文：无 probe/catalog 归属的 BASE 精灵必须保持 noMatch，不能变成 unknown 阻断
        // AutoBlend 对未覆盖精确查询的补全。
        // English: A BASE sprite without probe/catalog ownership must stay noMatch so an
        // uncovered exact query never turns into a conservative unknown blocker.
        assertTrue(
                observed.acceptedDocuments().isEmpty());
        assertEquals(
                Optional.empty(),
                observed.unknownDiagnostic());
    }

    @Test
    void keepsNoMatchForOrdinaryVanillaSprite() {
        BlockState state = Blocks.GLASS.defaultBlockState();
        FusionNativeQueryOwnership ownership =
                new FusionNativeQueryOwnership();
        ownership.beginModelCapture(
                GENERATION,
                new FusionAcceptedModifierDocumentCatalog.Snapshot(
                        GENERATION,
                        Map.of(state, List.of(
                                NativeDocumentIdentity.resourceOnly(
                                        "minecraft:fusion/base_test.json")))));
        ownership.captureModel(
                state,
                (level, pos, capturedState, face, sprite) -> true);
        ownership.endModelCapture();

        NativeQueryObservation observed =
                ownership.observe(
                        GENERATION,
                        LEVEL,
                        POS,
                        state,
                        Direction.UP,
                        sprite(DefaultTextureTypes.VANILLA));

        // 中文：未拥有的普通（VANILLA）精灵必须仍 noMatch，所有权修复不得扩大模型级语义。
        // English: An ordinary (VANILLA) sprite must still return noMatch; the ownership fix
        // must not broaden model-level semantics.
        assertTrue(
                observed.acceptedDocuments().isEmpty());
        assertEquals(
                Optional.empty(),
                observed.unknownDiagnostic());
    }

    /** 中文：最小 BlockAndTintGetter 替身；只转发引用，不读取世界。 / English: Minimal BlockAndTintGetter double; only forwards references and never reads the world. */
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

    /** 中文：构造带指定 Fusion 纹理类型的 Atlas 精灵；VANILLA 表示无 Fusion 实例。 / English: Builds an atlas sprite carrying the requested Fusion texture type; VANILLA means no Fusion instance. */
    private static TextureAtlasSprite sprite(
            TextureType<?, ?> type) {
        NativeImage image =
                new NativeImage(16, 16, false);
        SpriteContents contents = new SpriteContents(
                new ResourceLocation(
                        "minecraft:test_sprite"),
                new FrameSize(16, 16),
                image,
                AnimationMetadataSection.EMPTY);
        return new FusionSprite(
                TextureAtlas.LOCATION_BLOCKS,
                contents,
                type);
    }

    /**
     * 中文：实现 Fusion 的 TextureAtlasSpriteExtension，使 SpriteHelper.getTextureType 能
     * 在本测试替身上读到目标纹理类型。
     *
     * English: Implements Fusion's TextureAtlasSpriteExtension so SpriteHelper.getTextureType
     * reads the requested texture type from this test double.
     */
    private static final class FusionSprite
            extends TextureAtlasSprite
            implements TextureAtlasSpriteExtension {
        private final TextureType<?, ?> type;

        private FusionSprite(
                ResourceLocation atlasLocation,
                SpriteContents contents,
                TextureType<?, ?> type) {
            super(
                    atlasLocation,
                    contents,
                    2048,
                    2048,
                    0,
                    0);
            this.type = Objects.requireNonNull(
                    type,
                    "type");
        }

        @Override
        public void setFusionSpriteInstance(
                SpriteInstance instance) {
            // 中文：测试替身只读，不需要回写路径。
            // English: The test double is read-only and needs no write-back path.
        }

        @Override
        public SpriteInstance getFusionSpriteInstance() {
            if (type == DefaultTextureTypes.VANILLA) {
                return null;
            }
            return new StubSpriteInstance(this, type);
        }
    }

    /** 中文：最小 SpriteInstance 替身，只提供纹理类型读取所需的方法。 / English: Minimal SpriteInstance double exposing only the methods needed for texture-type reads. */
    private static final class StubSpriteInstance
            implements SpriteInstance {
        private final TextureAtlasSprite sprite;
        private final TextureType<?, ?> type;

        private StubSpriteInstance(
                TextureAtlasSprite sprite,
                TextureType<?, ?> type) {
            this.sprite = Objects.requireNonNull(
                    sprite,
                    "sprite");
            this.type = Objects.requireNonNull(
                    type,
                    "type");
        }

        @Override
        public TextureInstance<?> getTexture() {
            return new StubTextureInstance(this, type);
        }

        @Override
        public TextureAtlasSprite getSprite() {
            return sprite;
        }

        @Override
        public ResourceLocation getIdentifier() {
            return sprite.contents().name();
        }

        @Override
        public float getU0() {
            return 0.0F;
        }

        @Override
        public float getU1() {
            return 1.0F;
        }

        @Override
        public float getV0() {
            return 0.0F;
        }

        @Override
        public float getV1() {
            return 1.0F;
        }
    }

    /** 中文：最小 TextureInstance 替身，只返回目标纹理类型。 / English: Minimal TextureInstance double returning only the target texture type. */
    private static final class StubTextureInstance
            implements TextureInstance<Void> {
        private final SpriteInstance defaultSprite;
        private final TextureType<?, Void> type;

        @SuppressWarnings("unchecked")
        private StubTextureInstance(
                SpriteInstance defaultSprite,
                TextureType<?, ?> type) {
            this.defaultSprite = Objects.requireNonNull(
                    defaultSprite,
                    "defaultSprite");
            this.type = (TextureType<?, Void>)
                    Objects.requireNonNull(
                            type,
                            "type");
        }

        @Override
        public TextureType<?, Void> getTextureType() {
            return type;
        }

        @Override
        public ResourceLocation getIdentifier() {
            return defaultSprite.getIdentifier();
        }

        @Override
        public List<SpriteInstance> getSprites() {
            return List.of(defaultSprite);
        }

        @Override
        public SpriteInstance getDefaultSprite() {
            return defaultSprite;
        }

        @Override
        public Void getCustomData() {
            return null;
        }
    }

}
