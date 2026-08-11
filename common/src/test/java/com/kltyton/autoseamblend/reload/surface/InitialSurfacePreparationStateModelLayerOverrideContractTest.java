package com.kltyton.autoseamblend.reload.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：资源层覆盖语义合同：1.20.1 原版 ModelBakery 对同一 blockstate 位置的多层
 * LoadedJson（资源包顺序，低层在前、高层在后）按 per-layer state putAll/replace 处理：
 * 每层在全新 per-state 映射内编译 multipart/variants，再用 putAll 覆盖已累积的
 * per-state 分配；本域以空依赖列表编码 missing（不加入 builtin/missing），同一定义内
 * variant 键 overlap 会毒化该状态，后续 variant 键不得复活。variants 层未命中的状态
 * 保留低层；multipart 层不同：1.20.1 原版先为该 block 全部 possible states 建本层条目
 * （possibleStates.forEach(method_4738) 后 layerMap.putAll），selector 无命中的状态以
 * 空依赖覆盖低层，多个 selector 命中同一状态时贡献并集。
 *
 * English: Resource-layer override contract: vanilla 1.20.1 ModelBakery handles multi-layer
 * LoadedJson for one blockstate location (pack order, low first / high last) as per-layer
 * state putAll/replace. Each layer builds a fresh per-state assignment and then putAll covers
 * the accumulated per-state values; this domain encodes missing as an empty dependency list
 * (never builtin/missing), and a variant-key overlap inside the same definition poisons the
 * state so later keys must not revive it. States not hit by a variants layer retain the lower
 * layer; a multipart layer differs: vanilla 1.20.1 first creates an entry for EVERY possible
 * state of the block (possibleStates.forEach(method_4738) then layerMap.putAll), states no
 * selector matches are covered by an empty dependency list overriding the lower layer, and
 * multiple selectors matching one state contribute the union of their variants.
 */
class InitialSurfacePreparationStateModelLayerOverrideContractTest {

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
    void highLayerFullCoverageReplacesLowLayerModels() {
        // Stone has a single state: a high layer whose "" variant covers every state must
        // fully replace the low-layer model per state, not union with it.
        ResourceLocation rawStateKey =
                new ResourceLocation("minecraft", "blockstates/stone.json");
        Map<ResourceLocation, List<ModelBakery.LoadedJson>> blockStates =
                Map.of(rawStateKey, List.of(
                        loaded(rawStateKey, "low",
                                "{\"variants\":{\"\":{\"model\":\"minecraft:block/low\"}}}"),
                        loaded(rawStateKey, "high",
                                "{\"variants\":{\"\":{\"model\":\"minecraft:block/high\"}}}")));
        Map<ResourceLocation, BlockModel> blockModels = Map.of(
                modelId("low"),
                BlockModel.fromString(CUBE_MODEL_JSON),
                modelId("high"),
                BlockModel.fromString(CUBE_MODEL_JSON));

        List<InitialSurfacePreparation.StateModelEntry> entries =
                InitialSurfacePreparation.resolveStateModels(
                        blockModels,
                        blockStates);

        assertEquals(
                List.of(modelId("high")),
                singleEntry(entries, Blocks.STONE).models(),
                "high layer full coverage must replace the low layer per state");
    }

    @Test
    void highLayerPartialCoverageRetainsLowLayerForUnmatchedStates() {
        // A high layer with only "facing=north" must replace the low layer for north states
        // and leave every other oak stairs state on the low layer.
        ResourceLocation rawStateKey =
                new ResourceLocation("minecraft", "blockstates/oak_stairs.json");
        Map<ResourceLocation, List<ModelBakery.LoadedJson>> blockStates =
                Map.of(rawStateKey, List.of(
                        loaded(rawStateKey, "low",
                                "{\"variants\":{\"\":{\"model\":\"minecraft:block/low\"}}}"),
                        loaded(rawStateKey, "high",
                                "{\"variants\":{\"facing=north\":"
                                        + "{\"model\":\"minecraft:block/high\"}}}")));
        Map<ResourceLocation, BlockModel> blockModels = Map.of(
                modelId("low"),
                BlockModel.fromString(CUBE_MODEL_JSON),
                modelId("high"),
                BlockModel.fromString(CUBE_MODEL_JSON));

        List<InitialSurfacePreparation.StateModelEntry> entries =
                InitialSurfacePreparation.resolveStateModels(
                        blockModels,
                        blockStates);

        List<InitialSurfacePreparation.StateModelEntry> north = entriesFor(
                entries,
                Blocks.OAK_STAIRS,
                state -> state.getValue(
                        BlockStateProperties.HORIZONTAL_FACING) == Direction.NORTH);
        assertFalse(north.isEmpty(), "oak stairs must expose facing=north states");
        for (InitialSurfacePreparation.StateModelEntry entry : north) {
            assertEquals(
                    List.of(modelId("high")),
                    entry.models(),
                    "high layer partial variant must replace the low layer per matched state: "
                            + entry.state());
        }

        List<InitialSurfacePreparation.StateModelEntry> nonNorth = entriesFor(
                entries,
                Blocks.OAK_STAIRS,
                state -> state.getValue(
                        BlockStateProperties.HORIZONTAL_FACING) != Direction.NORTH);
        assertFalse(nonNorth.isEmpty(), "oak stairs must expose non-north states");
        for (InitialSurfacePreparation.StateModelEntry entry : nonNorth) {
            assertEquals(
                    List.of(modelId("low")),
                    entry.models(),
                    "states not hit by the high layer must retain the low layer: "
                            + entry.state());
        }
    }

    @Test
    void highLayerMultipartCoversEveryStateEmptyOverrideWhenUnmatched() {
        // A high-layer multipart definition first creates an entry for EVERY possible state
        // of the block: states whose selector matches replace the low layer with the selector
        // models; states no selector matches are covered by an empty dependency list (this
        // domain's missing encoding) and must NOT retain the low layer.
        ResourceLocation rawStateKey =
                new ResourceLocation("minecraft", "blockstates/oak_fence.json");
        Map<ResourceLocation, List<ModelBakery.LoadedJson>> blockStates =
                Map.of(rawStateKey, List.of(
                        loaded(rawStateKey, "low",
                                "{\"variants\":{\"\":{\"model\":\"minecraft:block/low\"}}}"),
                        loaded(rawStateKey, "high",
                                "{\"multipart\":["
                                        + "{\"when\":{\"north\":\"true\"},"
                                        + "\"apply\":{\"model\":\"minecraft:block/high\"}}]}")));
        Map<ResourceLocation, BlockModel> blockModels = Map.of(
                modelId("low"),
                BlockModel.fromString(CUBE_MODEL_JSON),
                modelId("high"),
                BlockModel.fromString(CUBE_MODEL_JSON));

        List<InitialSurfacePreparation.StateModelEntry> entries =
                InitialSurfacePreparation.resolveStateModels(
                        blockModels,
                        blockStates);

        List<InitialSurfacePreparation.StateModelEntry> north = entriesFor(
                entries,
                Blocks.OAK_FENCE,
                state -> state.getValue(BlockStateProperties.NORTH));
        assertFalse(north.isEmpty(), "oak fence must expose north=true states");
        for (InitialSurfacePreparation.StateModelEntry entry : north) {
            assertEquals(
                    List.of(modelId("high")),
                    entry.models(),
                    "high-layer multipart must replace the low layer for matched states: "
                            + entry.state());
        }

        List<InitialSurfacePreparation.StateModelEntry> noNorth = entriesFor(
                entries,
                Blocks.OAK_FENCE,
                state -> !state.getValue(BlockStateProperties.NORTH));
        assertFalse(noNorth.isEmpty(), "oak fence must expose north=false states");
        for (InitialSurfacePreparation.StateModelEntry entry : noNorth) {
            assertEquals(
                    List.of(),
                    entry.models(),
                    "states not matched by the high-layer multipart must be covered by an "
                            + "empty override (missing), not retain the low layer: "
                            + entry.state());
        }
    }

    @Test
    void highLayerMultipartSelectorsUnionForSameState() {
        // Multiple selectors matching the same state contribute the union of their variants
        // (vanilla 1.20.1 MultiPart collects variants from every matching selector); a state
        // matched only by the north selector must not include the east selector's model.
        ResourceLocation rawStateKey =
                new ResourceLocation("minecraft", "blockstates/oak_fence.json");
        Map<ResourceLocation, List<ModelBakery.LoadedJson>> blockStates =
                Map.of(rawStateKey, List.of(
                        loaded(rawStateKey, "low",
                                "{\"variants\":{\"\":{\"model\":\"minecraft:block/low\"}}}"),
                        loaded(rawStateKey, "high",
                                "{\"multipart\":["
                                        + "{\"when\":{\"north\":\"true\"},"
                                        + "\"apply\":{\"model\":\"minecraft:block/high_north\"}},"
                                        + "{\"when\":{\"east\":\"true\"},"
                                        + "\"apply\":{\"model\":\"minecraft:block/high_east\"}}]}")));
        Map<ResourceLocation, BlockModel> blockModels = Map.of(
                modelId("low"),
                BlockModel.fromString(CUBE_MODEL_JSON),
                modelId("high_north"),
                BlockModel.fromString(CUBE_MODEL_JSON),
                modelId("high_east"),
                BlockModel.fromString(CUBE_MODEL_JSON));

        List<InitialSurfacePreparation.StateModelEntry> entries =
                InitialSurfacePreparation.resolveStateModels(
                        blockModels,
                        blockStates);

        List<InitialSurfacePreparation.StateModelEntry> northAndEast = entriesFor(
                entries,
                Blocks.OAK_FENCE,
                state -> state.getValue(BlockStateProperties.NORTH)
                        && state.getValue(BlockStateProperties.EAST));
        assertFalse(
                northAndEast.isEmpty(),
                "oak fence must expose north=true,east=true states");
        for (InitialSurfacePreparation.StateModelEntry entry : northAndEast) {
            assertEquals(
                    List.of(
                            modelId("high_north"),
                            modelId("high_east")),
                    entry.models(),
                    "both matching selectors must contribute their variants as a union: "
                            + entry.state());
        }

        List<InitialSurfacePreparation.StateModelEntry> northOnly = entriesFor(
                entries,
                Blocks.OAK_FENCE,
                state -> state.getValue(BlockStateProperties.NORTH)
                        && !state.getValue(BlockStateProperties.EAST));
        assertFalse(
                northOnly.isEmpty(),
                "oak fence must expose north=true,east=false states");
        for (InitialSurfacePreparation.StateModelEntry entry : northOnly) {
            assertEquals(
                    List.of(modelId("high_north")),
                    entry.models(),
                    "a state matched only by the north selector must resolve only its model: "
                            + entry.state());
        }
    }

    @Test
    void variantOverlapPoisonsStateMissingAndLaterKeysCannotRevive() {
        // Within one high-layer variants definition, two keys matching the same state are an
        // overlap: the state becomes missing (empty dependency list, low layer not leaked) and
        // a later key matching the same state must not revive it.
        ResourceLocation rawStateKey =
                new ResourceLocation("minecraft", "blockstates/oak_stairs.json");
        Map<ResourceLocation, List<ModelBakery.LoadedJson>> blockStates =
                Map.of(rawStateKey, List.of(
                        loaded(rawStateKey, "low",
                                "{\"variants\":{\"\":{\"model\":\"minecraft:block/low\"}}}"),
                        loaded(rawStateKey, "high",
                                "{\"variants\":{"
                                        + "\"facing=north\":{\"model\":\"minecraft:block/a\"},"
                                        + "\"\":{\"model\":\"minecraft:block/b\"},"
                                        + "\"facing=north,half=bottom\":"
                                        + "{\"model\":\"minecraft:block/c\"}}}")));
        Map<ResourceLocation, BlockModel> blockModels = Map.of(
                modelId("low"),
                BlockModel.fromString(CUBE_MODEL_JSON),
                modelId("a"),
                BlockModel.fromString(CUBE_MODEL_JSON),
                modelId("b"),
                BlockModel.fromString(CUBE_MODEL_JSON),
                modelId("c"),
                BlockModel.fromString(CUBE_MODEL_JSON));

        List<InitialSurfacePreparation.StateModelEntry> entries =
                InitialSurfacePreparation.resolveStateModels(
                        blockModels,
                        blockStates);

        List<InitialSurfacePreparation.StateModelEntry> north = entriesFor(
                entries,
                Blocks.OAK_STAIRS,
                state -> state.getValue(
                        BlockStateProperties.HORIZONTAL_FACING) == Direction.NORTH);
        assertFalse(north.isEmpty(), "oak stairs must expose facing=north states");
        for (InitialSurfacePreparation.StateModelEntry entry : north) {
            assertEquals(
                    List.of(),
                    entry.models(),
                    "variant overlap must poison the state to missing and later keys must not "
                            + "revive it (low layer must not leak either): "
                            + entry.state());
        }

        List<InitialSurfacePreparation.StateModelEntry> nonNorth = entriesFor(
                entries,
                Blocks.OAK_STAIRS,
                state -> state.getValue(
                        BlockStateProperties.HORIZONTAL_FACING) != Direction.NORTH);
        assertFalse(nonNorth.isEmpty(), "oak stairs must expose non-north states");
        for (InitialSurfacePreparation.StateModelEntry entry : nonNorth) {
            assertEquals(
                    List.of(modelId("b")),
                    entry.models(),
                    "states matched only by the empty key must resolve the high layer: "
                            + entry.state());
        }
    }

    @Test
    void cleanHighLayerRevivesStatePoisonedOnlyInLowLayer() {
        // Poisoning is scoped to one definition: a low-layer overlap must not leak into a
        // clean high layer, whose per-layer putAll legitimately overrides the state.
        ResourceLocation rawStateKey =
                new ResourceLocation("minecraft", "blockstates/oak_stairs.json");
        Map<ResourceLocation, List<ModelBakery.LoadedJson>> blockStates =
                Map.of(rawStateKey, List.of(
                        loaded(rawStateKey, "low",
                                "{\"variants\":{"
                                        + "\"facing=north\":{\"model\":\"minecraft:block/a\"},"
                                        + "\"\":{\"model\":\"minecraft:block/b\"}}}"),
                        loaded(rawStateKey, "high",
                                "{\"variants\":{\"\":{\"model\":\"minecraft:block/high\"}}}")));
        Map<ResourceLocation, BlockModel> blockModels = Map.of(
                modelId("a"),
                BlockModel.fromString(CUBE_MODEL_JSON),
                modelId("b"),
                BlockModel.fromString(CUBE_MODEL_JSON),
                modelId("high"),
                BlockModel.fromString(CUBE_MODEL_JSON));

        List<InitialSurfacePreparation.StateModelEntry> entries =
                InitialSurfacePreparation.resolveStateModels(
                        blockModels,
                        blockStates);

        List<InitialSurfacePreparation.StateModelEntry> north = entriesFor(
                entries,
                Blocks.OAK_STAIRS,
                state -> state.getValue(
                        BlockStateProperties.HORIZONTAL_FACING) == Direction.NORTH);
        assertFalse(north.isEmpty(), "oak stairs must expose facing=north states");
        for (InitialSurfacePreparation.StateModelEntry entry : north) {
            assertEquals(
                    List.of(modelId("high")),
                    entry.models(),
                    "a clean high layer must replace the low layer even for states the low "
                            + "layer poisoned: "
                            + entry.state());
        }
    }

    private static ModelBakery.LoadedJson loaded(
            ResourceLocation rawStateKey,
            String source,
            String json) {
        JsonElement data = JsonParser.parseString(json);
        return new ModelBakery.LoadedJson(
                rawStateKey.toString() + " (" + source + ")",
                data);
    }

    private static ResourceLocation modelId(String path) {
        return new ResourceLocation("minecraft", "block/" + path);
    }

    private static InitialSurfacePreparation.StateModelEntry singleEntry(
            List<InitialSurfacePreparation.StateModelEntry> entries,
            Block block) {
        return entriesFor(entries, block, ignored -> true).stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "expected a resolution entry for " + block));
    }

    private static List<InitialSurfacePreparation.StateModelEntry> entriesFor(
            List<InitialSurfacePreparation.StateModelEntry> entries,
            Block block,
            Predicate<BlockState> predicate) {
        return entries.stream()
                .filter(entry -> entry.state().getBlock() == block)
                .filter(entry -> predicate.test(entry.state()))
                .toList();
    }
}
