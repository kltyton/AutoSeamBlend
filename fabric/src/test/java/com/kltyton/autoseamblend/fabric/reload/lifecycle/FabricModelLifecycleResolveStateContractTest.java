package com.kltyton.autoseamblend.fabric.reload.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Function;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：AfterBake 状态解析的 RED 合同：1.20.1 AfterBake.Context.id() 声明为
 * ResourceLocation，但方块状态烘焙时运行时是携带 variant 的 ModelResourceLocation
 * （Fabric API 0.92.11 javadoc："may be a ModelResourceLocation"）。resolveState 必须按
 * ModelResourceLocation.getVariant() 精确还原每个 BlockState，不得折叠成 defaultBlockState；
 * 物品烘焙约定为 {@code <item-id>#inventory}，缺失模型为 {@code #missingno}，均不属于任何
 * BlockState，必须返回 null；纯 ResourceLocation 非方块 id 同样不得误捕获。
 *
 * English: RED contract for AfterBake state resolution: 1.20.1 AfterBake.Context.id() is
 * declared as ResourceLocation, but blockstate bakes receive a ModelResourceLocation whose
 * variant carries the exact state string (Fabric API 0.92.11 javadoc: "may be a
 * ModelResourceLocation"). resolveState must restore each BlockState from
 * ModelResourceLocation.getVariant() instead of folding everything into
 * defaultBlockState; item bakes use {@code <item-id>#inventory} and the missing model uses
 * {@code #missingno}, which never belong to any BlockState and must resolve to null; plain
 * non-block ResourceLocation ids must also stay out of block-state capture.
 */
class FabricModelLifecycleResolveStateContractTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void paneItemBakeInventoryVariantDoesNotResolveToBlockState() {
        assertNull(
                FabricModelLifecycle.resolveState(
                        new StubContext(
                                new ModelResourceLocation(
                                        new ResourceLocation(
                                                "minecraft:glass_pane"),
                                        "inventory"))),
                "the glass_pane item bake (#inventory) must never resolve to a block state");
    }

    @Test
    void stainedPaneItemBakeSharesTheSameSemantics() {
        assertNull(
                FabricModelLifecycle.resolveState(
                        new StubContext(
                                new ModelResourceLocation(
                                        new ResourceLocation(
                                                "minecraft:red_stained_glass_pane"),
                                        "inventory"))),
                "stained pane item bakes must use the same skip semantics");
        assertNull(
                FabricModelLifecycle.resolveState(
                        new StubContext(
                                new ModelResourceLocation(
                                        new ResourceLocation(
                                                "minecraft:glass"),
                                        "inventory"))),
                "full glass item bakes must also stay out of block-state wrapping");
    }

    @Test
    void realBlockStateBakeResolvesExactVariantState() {
        BlockState resolved =
                FabricModelLifecycle.resolveState(
                        new StubContext(
                                new ModelResourceLocation(
                                        new ResourceLocation(
                                                "minecraft:grass_block"),
                                        "snowy=true")));
        assertNotNull(
                resolved,
                "real blockstate variants must still resolve");
        assertEquals(
                Blocks.GRASS_BLOCK,
                resolved.getBlock());
        assertTrue(
                resolved.getValue(
                        net.minecraft.world.level.block
                                .GrassBlock.SNOWY),
                "snowy=true must be preserved, not folded to the default state");
        assertNotEquals(
                Blocks.GRASS_BLOCK.defaultBlockState(),
                resolved,
                "a non-default variant must never collapse to the default state");
    }

    @Test
    void oakStairsVariantsResolveToTheirOwnStatesWithoutCollapse() {
        BlockState east =
                FabricModelLifecycle.resolveState(
                        new StubContext(
                                new ModelResourceLocation(
                                        new ResourceLocation(
                                                "minecraft:oak_stairs"),
                                        "facing=east,half=bottom,"
                                                + "shape=straight,"
                                                + "waterlogged=false")));
        BlockState west =
                FabricModelLifecycle.resolveState(
                        new StubContext(
                                new ModelResourceLocation(
                                        new ResourceLocation(
                                                "minecraft:oak_stairs"),
                                        "facing=west,half=top,"
                                                + "shape=outer_left,"
                                                + "waterlogged=true")));
        assertNotNull(
                east,
                "the east-facing stair state must resolve");
        assertNotNull(
                west,
                "the west-facing stair state must resolve");
        assertEquals(
                Blocks.OAK_STAIRS,
                east.getBlock());
        assertEquals(
                Direction.EAST,
                east.getValue(StairBlock.FACING));
        assertEquals(
                Half.BOTTOM,
                east.getValue(StairBlock.HALF));
        assertEquals(
                Direction.WEST,
                west.getValue(StairBlock.FACING));
        assertEquals(
                Half.TOP,
                west.getValue(StairBlock.HALF));
        assertEquals(
                StairsShape.OUTER_LEFT,
                west.getValue(StairBlock.SHAPE));
        assertTrue(
                west.getValue(StairBlock.WATERLOGGED));
        assertNotEquals(
                east,
                west,
                "distinct states must not collapse to one state");
    }

    @Test
    void emptyVariantResolvesToDefaultBlockState() {
        assertEquals(
                Blocks.OAK_STAIRS.defaultBlockState(),
                FabricModelLifecycle.resolveState(
                        new StubContext(
                                new ModelResourceLocation(
                                        new ResourceLocation(
                                                "minecraft:oak_stairs"),
                                        ""))),
                "an empty variant is the default state");
    }

    @Test
    void invalidVariantSafelySkipsToDefaultState() {
        assertEquals(
                Blocks.OAK_STAIRS.defaultBlockState(),
                FabricModelLifecycle.resolveState(
                        new StubContext(
                                new ModelResourceLocation(
                                        new ResourceLocation(
                                                "minecraft:oak_stairs"),
                                        "facing=up"))),
                "an invalid variant must be skipped safely without throwing");
    }

    @Test
    void plainNonBlockResourceLocationDoesNotResolve() {
        assertNull(
                FabricModelLifecycle.resolveState(
                        new StubContext(
                                new ResourceLocation(
                                        "minecraft:item/glass_pane"))),
                "plain non-block ids must stay out of block-state wrapping");
    }

    /** 中文：仅提供 id() 的最小 AfterBake.Context 桩。 / English: Minimal AfterBake.Context stub providing only id(). */
    private static final class StubContext
            implements ModelModifier.AfterBake.Context {
        private final ResourceLocation id;

        private StubContext(
                ResourceLocation id) {
            this.id = id;
        }

        @Override
        public ResourceLocation id() {
            return id;
        }

        @Override
        public UnbakedModel sourceModel() {
            return null;
        }

        @Override
        public Function<Material, TextureAtlasSprite>
                textureGetter() {
            return null;
        }

        @Override
        public ModelState settings() {
            return null;
        }

        @Override
        public ModelBaker baker() {
            return null;
        }

        @Override
        public ModelBakery loader() {
            return null;
        }
    }
}
