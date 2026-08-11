package com.kltyton.autoseamblend.reload.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：E2 回归合同：1.20.1 ModelManager.loadBlockStates 返回原始 blockstate 键
 * （minecraft:blockstates/&lt;id&gt;.json），resolveStateModels 必须先经
 * ModelBakery.BLOCKSTATE_LISTER.fileToId 归一化，才能与 ID 键化的
 * BlockModelShaper.stateToModelLocation 匹配；否则任何状态都解析不到模型依赖，
 * 每个状态都会得到 BLOCKSTATE_ROOT_HAS_NO_MODEL_DEPENDENCIES。
 * 本测试不依赖 RuleRuntime selectors，也不依赖 atlas 缝合，专门区分
 * “原始键不匹配”与“空配置/atlas 饥饿”。
 *
 * English: E2 regression contract: 1.20.1 ModelManager.loadBlockStates returns raw
 * blockstate keys (minecraft:blockstates/&lt;id&gt;.json); resolveStateModels must
 * normalize them via ModelBakery.BLOCKSTATE_LISTER.fileToId before matching against
 * the ID-keyed BlockModelShaper.stateToModelLocation, otherwise every state stays
 * unresolved with BLOCKSTATE_ROOT_HAS_NO_MODEL_DEPENDENCIES. This test depends on
 * neither RuleRuntime selectors nor atlas stitching, so it discriminates raw-key
 * mismatch from empty-config/atlas starvation.
 */
class InitialSurfacePreparationStateModelKeyContractTest {

    private static final String CUBE_MODEL_JSON =
            "{\"elements\":[{\"from\":[0,0,0],\"to\":[16,16,16],\"faces\":{"
                    + "\"down\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"},"
                    + "\"up\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"},"
                    + "\"north\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"},"
                    + "\"south\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"},"
                    + "\"west\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"},"
                    + "\"east\":{\"uv\":[0,0,16,16],\"texture\":\"#all\"}}}]}";

    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void rawBlockStateKeyResolvesMatchingIdKeyedBlockModel() {
        ResourceLocation rawStateKey =
                new ResourceLocation("minecraft", "blockstates/stone.json");
        JsonElement definition = JsonParser.parseString(
                "{\"variants\":{\"\":{\"model\":\"minecraft:block/stone\"}}}");
        Map<ResourceLocation, List<ModelBakery.LoadedJson>> blockStates =
                Map.of(rawStateKey, List.of(
                        new ModelBakery.LoadedJson(
                                rawStateKey.toString(),
                                definition)));
        Map<ResourceLocation, BlockModel> blockModels = Map.of(
                new ResourceLocation("minecraft", "block/stone"),
                BlockModel.fromString(CUBE_MODEL_JSON));

        List<InitialSurfacePreparation.StateModelEntry> entries =
                InitialSurfacePreparation.resolveStateModels(
                        blockModels,
                        blockStates);

        InitialSurfacePreparation.StateModelEntry stone = entries.stream()
                .filter(entry -> entry.state().getBlock() == Blocks.STONE)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "stone state entry must be present in resolution output"));
        assertFalse(
                stone.models().isEmpty(),
                "raw blockstate key must normalize to the state model id: "
                        + rawStateKey
                        + " vs "
                        + BlockModelShaper.stateToModelLocation(
                                Blocks.STONE.defaultBlockState())
                        + " (resolved=" + stone.models() + ")");
        assertEquals(
                List.of(new ResourceLocation("minecraft", "block/stone")),
                stone.models(),
                "the ID-keyed block model referenced by the blockstate must resolve");
    }

    @Test
    void partialVariantKeyMatchesStatesWithUnlistedProperties() {
        // Vanilla 1.20.1 ModelBakery treats a variant key as a property-subset predicate:
        // "facing=north" must match every oak stairs state whose facing is north even when the
        // state additionally carries half/shape/waterlogged. A literal full-key table lookup
        // (BlockModelDefinition.getVariant) throws MissingVariantException for such keys and
        // would break the whole initial reload.
        ResourceLocation rawStateKey =
                new ResourceLocation("minecraft", "blockstates/oak_stairs.json");
        JsonElement definition = JsonParser.parseString(
                "{\"variants\":{\"facing=north\":{\"model\":\"minecraft:block/stone\"}}}");
        Map<ResourceLocation, List<ModelBakery.LoadedJson>> blockStates =
                Map.of(rawStateKey, List.of(
                        new ModelBakery.LoadedJson(
                                rawStateKey.toString(),
                                definition)));
        Map<ResourceLocation, BlockModel> blockModels = Map.of(
                new ResourceLocation("minecraft", "block/stone"),
                BlockModel.fromString(CUBE_MODEL_JSON));

        List<InitialSurfacePreparation.StateModelEntry> entries =
                InitialSurfacePreparation.resolveStateModels(
                        blockModels,
                        blockStates);

        List<InitialSurfacePreparation.StateModelEntry> northStates = entries.stream()
                .filter(entry -> entry.state().getBlock() == Blocks.OAK_STAIRS)
                .filter(entry -> entry.state().getValue(
                        BlockStateProperties.HORIZONTAL_FACING) == Direction.NORTH)
                .toList();
        assertFalse(
                northStates.isEmpty(),
                "oak stairs must expose facing=north states (with unlisted half/shape/waterlogged)");
        for (InitialSurfacePreparation.StateModelEntry entry : northStates) {
            assertEquals(
                    List.of(new ResourceLocation("minecraft", "block/stone")),
                    entry.models(),
                    "partial variant key facing=north must match states omitting waterlogged etc.: "
                            + entry.state());
        }

        List<InitialSurfacePreparation.StateModelEntry> eastStates = entries.stream()
                .filter(entry -> entry.state().getBlock() == Blocks.OAK_STAIRS)
                .filter(entry -> entry.state().getValue(
                        BlockStateProperties.HORIZONTAL_FACING) == Direction.EAST)
                .toList();
        assertFalse(
                eastStates.isEmpty(),
                "oak stairs must expose facing=east states");
        for (InitialSurfacePreparation.StateModelEntry entry : eastStates) {
            assertTrue(
                    entry.models().isEmpty(),
                    "non-north facing must not resolve the partial facing=north variant: "
                            + entry.state()
                            + " (resolved=" + entry.models() + ")");
        }
    }

    @Test
    void invalidMultipartSelectorIsSkippedAndValidSelectorStillResolves() {
        // AutoSeamBlend's defensive per-selector tolerance: a bad multipart selector
        // (unknown property/value) is skipped so the atlas-preparation future stays alive
        // and the valid selector still resolves per state. Vanilla 1.20.1 does not isolate
        // such selectors; the exception aborts the multipart bake and the whole blockstate
        // definition falls back to the missing model.
        ResourceLocation rawStateKey =
                new ResourceLocation("minecraft", "blockstates/oak_fence.json");
        JsonElement definition = JsonParser.parseString(
                "{\"multipart\":["
                        + "{\"when\":{\"unknown_property\":\"x\"},"
                        + "\"apply\":{\"model\":\"minecraft:block/stone\"}},"
                        + "{\"when\":{\"north\":\"true\"},"
                        + "\"apply\":{\"model\":\"minecraft:block/stone\"}}"
                        + "]}");
        Map<ResourceLocation, List<ModelBakery.LoadedJson>> blockStates =
                Map.of(rawStateKey, List.of(
                        new ModelBakery.LoadedJson(
                                rawStateKey.toString(),
                                definition)));
        Map<ResourceLocation, BlockModel> blockModels = Map.of(
                new ResourceLocation("minecraft", "block/stone"),
                BlockModel.fromString(CUBE_MODEL_JSON));

        List<InitialSurfacePreparation.StateModelEntry> entries =
                InitialSurfacePreparation.resolveStateModels(
                        blockModels,
                        blockStates);

        List<InitialSurfacePreparation.StateModelEntry> northStates = entries.stream()
                .filter(entry -> entry.state().getBlock() == Blocks.OAK_FENCE)
                .filter(entry -> entry.state().getValue(BlockStateProperties.NORTH))
                .toList();
        assertFalse(
                northStates.isEmpty(),
                "oak fence must expose north=true states");
        for (InitialSurfacePreparation.StateModelEntry entry : northStates) {
            assertEquals(
                    List.of(new ResourceLocation("minecraft", "block/stone")),
                    entry.models(),
                    "valid selector must still resolve for north=true states: "
                            + entry.state());
        }

        List<InitialSurfacePreparation.StateModelEntry> noNorthStates = entries.stream()
                .filter(entry -> entry.state().getBlock() == Blocks.OAK_FENCE)
                .filter(entry -> !entry.state().getValue(BlockStateProperties.NORTH))
                .toList();
        assertFalse(
                noNorthStates.isEmpty(),
                "oak fence must expose north=false states");
        for (InitialSurfacePreparation.StateModelEntry entry : noNorthStates) {
            assertTrue(
                    entry.models().isEmpty(),
                    "invalid selector must be skipped and valid selector must not match "
                            + "north=false states: "
                            + entry.state()
                            + " (resolved=" + entry.models() + ")");
        }
    }
}
