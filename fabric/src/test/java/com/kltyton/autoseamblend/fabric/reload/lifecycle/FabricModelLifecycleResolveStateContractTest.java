package com.kltyton.autoseamblend.fabric.reload.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.function.Function;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.DetectedVersion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：AfterBake 状态解析的 RED 合同：Fabric 物品模型位置约定为 {@code <item-id>#inventory}
 * （缺失模型为 {@code #missingno}），从不属于任何 BlockState；resolveState 必须返回 null，
 * 否则物品烘焙会被当作方块状态进入 Athena 世界 pane/通用包装（玻璃板物品图标被
 * PaneConnectedBlockModel 替换而空白/坏）。普通与染色玻璃板同一语义，生产代码不依赖 ID。
 *
 * English: RED contract for AfterBake state resolution: Fabric item model locations use
 * {@code <item-id>#inventory} (and {@code #missingno} for the missing model), which never
 * belong to any BlockState; resolveState must return null or item bakes get wrapped as block
 * states by Athena's world pane/generic path (pane item icons replaced by the world
 * PaneConnectedBlockModel become blank/broken). Plain and stained panes share one semantics
 * and production code depends on no specific ID.
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
                                        ResourceLocation.parse(
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
                                        ResourceLocation.parse(
                                                "minecraft:red_stained_glass_pane"),
                                        "inventory"))),
                "stained pane item bakes must use the same skip semantics");
        assertNull(
                FabricModelLifecycle.resolveState(
                        new StubContext(
                                new ModelResourceLocation(
                                        ResourceLocation.parse(
                                                "minecraft:glass"),
                                        "inventory"))),
                "full glass item bakes must also stay out of block-state wrapping");
    }

    @Test
    void realBlockStateBakeStillResolves() {
        BlockState resolved =
                FabricModelLifecycle.resolveState(
                        new StubContext(
                                new ModelResourceLocation(
                                        ResourceLocation.parse(
                                                "minecraft:grass_block"),
                                        "snowy=false")));
        assertNotNull(
                resolved,
                "real blockstate variants must still resolve");
        assertEquals(
                Blocks.GRASS_BLOCK,
                resolved.getBlock());
        assertEquals(
                false,
                resolved.getValue(
                        net.minecraft.world.level.block
                                .GrassBlock.SNOWY));
    }

    /** 中文：仅提供 topLevelId 的最小 AfterBake.Context 桩。 / English: Minimal AfterBake.Context stub providing only topLevelId. */
    private static final class StubContext
            implements ModelModifier.AfterBake.Context {
        private final ModelResourceLocation topLevelId;

        private StubContext(
                ModelResourceLocation topLevelId) {
            this.topLevelId = topLevelId;
        }

        @Override
        public ResourceLocation resourceId() {
            return null;
        }

        @Override
        public ModelResourceLocation topLevelId() {
            return topLevelId;
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
