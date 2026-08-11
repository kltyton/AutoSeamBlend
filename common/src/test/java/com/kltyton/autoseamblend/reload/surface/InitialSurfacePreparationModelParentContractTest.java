package com.kltyton.autoseamblend.reload.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：resolveModelParents 父链合同：1.20.1 原版 ModelBakery 对内置父模型
 * builtin/generated 与 builtin/entity 分别返回 ModelBakery.GENERATION_MARKER /
 * BLOCK_ENTITY_MARKER（public static final BlockModel，分别由
 * {"gui_light":"front"} / {"gui_light":"side"} 解析而来，均无元素）；解析父链后
 * 子模型的 getElements() 必须停在标记模型（空列表），而不是经
 * ModelBakery.MISSING_MODEL_LOCATION 回退到 missing 立方体（完整六面方块，
 * 会产生伪表面证据）。
 *
 * English: resolveModelParents parent-chain contract: vanilla 1.20.1 ModelBakery maps the
 * builtin parents builtin/generated and builtin/entity to ModelBakery.GENERATION_MARKER /
 * BLOCK_ENTITY_MARKER (public static final BlockModel, parsed from
 * {"gui_light":"front"} / {"gui_light":"side"}, both element-less). After parent resolution
 * a child's getElements() must stop at the marker (empty list) instead of falling back
 * through ModelBakery.MISSING_MODEL_LOCATION to the missing cube (a full six-face block
 * that would inject spurious surface evidence).
 */
class InitialSurfacePreparationModelParentContractTest {

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void builtinGeneratedParentResolvesToEmptyElementsNotMissingCube() {
        assertParentStopsAtMarker(
                "builtin/generated",
                "item/generated_child");
    }

    @Test
    void builtinEntityParentResolvesToEmptyElementsNotMissingCube() {
        assertParentStopsAtMarker(
                "builtin/entity",
                "item/entity_child");
    }

    private static void assertParentStopsAtMarker(
            String parent,
            String modelPath) {
        ResourceLocation id =
                new ResourceLocation("minecraft", modelPath);
        Map<ResourceLocation, BlockModel> models = Map.of(
                id,
                BlockModel.fromString(
                        "{\"parent\":\"" + parent
                                + "\",\"textures\":{\"layer0\":\"minecraft:item/apple\"}}"));

        InitialSurfacePreparation.resolveModelParents(models);

        assertEquals(
                List.of(),
                models.get(id).getElements(),
                "parent chain through " + parent + " must stop at the element-less marker, "
                        + "not degenerate into the missing cube via "
                        + ModelBakery.MISSING_MODEL_LOCATION);
    }
}
