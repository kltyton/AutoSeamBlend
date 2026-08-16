package com.kltyton.autoseamblend.authoring.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 合同——namespace:block/&lt;blockpath&gt; 启发式对 glass_pane 等
 * multipart/变体方块会缺失；解析器必须通过 blockstate variants 或 multipart 引用
 * 选中一个具体模型，并且该模型的纹理映射能解析出源纹理，结果须包含所选具体模型
 * ID 与非空源纹理键。
 *
 * <p>English: RED contract -- the namespace:block/&lt;blockpath&gt; heuristic misses
 * multipart/variant blocks such as glass panes; the resolver must follow blockstate
 * variants or multipart references to a concrete model whose texture mapping resolves
 * the source texture, returning the selected concrete model id and non-empty source
 * texture keys.
 */
class BlockstatePreferredModelResolutionTest {
    private static final String GLASS_PANE_BLOCKSTATE = """
            {
              "multipart": [
                {
                  "when": {"north": "true"},
                  "apply": {"model": "minecraft:block/glass_pane_post"}
                },
                {
                  "when": {"east": "true"},
                  "apply": {"model": "minecraft:block/glass_pane_side"}
                }
              ]
            }
            """;
    private static final String GLASS_PANE_SIDE_MODEL = """
            {
              "textures": {
                "pane": "minecraft:block/glass",
                "edge": "minecraft:block/glass_pane_top"
              },
              "elements": []
            }
            """;
    private static final String STAIRS_BLOCKSTATE = """
            {
              "variants": {
                "facing=east,half=bottom,shape=straight": {
                  "model": "minecraft:block/oak_stairs"
                },
                "facing=north,half=bottom,shape=straight": {
                  "model": "minecraft:block/oak_stairs",
                  "y": 270
                }
              }
            }
            """;
    private static final String OAK_STAIRS_MODEL = """
            {
              "textures": {
                "bottom": "minecraft:block/oak_planks",
                "top": "minecraft:block/oak_planks",
                "side": "minecraft:block/oak_planks"
              },
              "elements": []
            }
            """;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void multipartPaneResolvesConcreteModelWithSourceTextureKeys() {
        ResourceManager resources = new MapResourceManager(Map.of(
                location("blockstates/glass_pane.json"),
                resource(GLASS_PANE_BLOCKSTATE),
                location("models/block/glass_pane_side.json"),
                resource(GLASS_PANE_SIDE_MODEL)));

        Optional<BlockstatePreferredModelResolution.Result> result =
                BlockstatePreferredModelResolution.resolve(
                        resources,
                        "minecraft:glass_pane",
                        "minecraft:block/glass");

        assertTrue(
                result.isPresent(),
                "multipart block with a missing direct block model must "
                        + "resolve through blockstate references");
        assertEquals(
                "minecraft:block/glass_pane_side",
                result.orElseThrow().selectedModelId(),
                "the selected model must be the concrete multipart model "
                        + "whose texture mapping resolves the source texture");
        assertFalse(
                result.orElseThrow().sourceTextureKeys().isEmpty(),
                "the resolved concrete model must expose the source texture "
                        + "keys resolved from its texture mapping");
    }

    @Test
    void variantStairsResolveConcreteModelWithSourceTextureKeys() {
        ResourceManager resources = new MapResourceManager(Map.of(
                location("blockstates/oak_stairs.json"),
                resource(STAIRS_BLOCKSTATE),
                location("models/block/oak_stairs.json"),
                resource(OAK_STAIRS_MODEL)));

        Optional<BlockstatePreferredModelResolution.Result> result =
                BlockstatePreferredModelResolution.resolve(
                        resources,
                        "minecraft:oak_stairs",
                        "minecraft:block/oak_planks");

        assertTrue(
                result.isPresent(),
                "variant block with a missing direct block model must "
                        + "resolve through blockstate variants");
        assertEquals(
                "minecraft:block/oak_stairs",
                result.orElseThrow().selectedModelId(),
                "the selected model must be the concrete variant model "
                        + "whose texture mapping resolves the source texture");
        assertFalse(
                result.orElseThrow().sourceTextureKeys().isEmpty(),
                "the resolved concrete model must expose the source texture "
                        + "keys resolved from its texture mapping");
    }

    @Test
    void managedRuleUsesResolvedConcreteModel() {
        ResourceManager resources = new MapResourceManager(Map.of(
                location("blockstates/glass_pane.json"),
                resource(GLASS_PANE_BLOCKSTATE),
                location("models/block/glass_pane_side.json"),
                resource(GLASS_PANE_SIDE_MODEL)));
        ManagedAuthoringDraft draft = new ManagedAuthoringDraft(
                "minecraft:glass_pane",
                "minecraft:block/glass",
                "minecraft:block/glass_pane",
                ConnectionMethod.CTM,
                ConnectionMethod.CTM,
                false,
                true);

        ManagedAuthoringRule rule =
                ManagedAuthoringProjectDrafts.createRule(draft, resources);

        assertEquals("minecraft:block/glass_pane_side", rule.originalModelId());
        assertFalse(rule.sourceTextureKeys().isEmpty());
    }

    private static ResourceLocation location(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "minecraft",
                path);
    }

    private static Resource resource(String content) {
        return new Resource(
                null,
                () -> new ByteArrayInputStream(
                        content.getBytes(
                                StandardCharsets.UTF_8)),
                () -> ResourceMetadata.EMPTY);
    }

    /** 中文：只服务测试注入资源的 ResourceManager 替身。 / English: ResourceManager double serving only injected resources. */
    private static final class MapResourceManager
            implements ResourceManager {
        private final Map<ResourceLocation, Resource> resources;

        private MapResourceManager(
                Map<ResourceLocation, Resource> resources) {
            this.resources = Map.copyOf(resources);
        }

        @Override
        public Optional<Resource> getResource(
                ResourceLocation location) {
            return Optional.ofNullable(
                    resources.get(location));
        }

        @Override
        public Set<String> getNamespaces() {
            return Set.of("minecraft");
        }

        @Override
        public List<Resource> getResourceStack(
                ResourceLocation location) {
            return Optional.ofNullable(
                            resources.get(location))
                    .map(List::of)
                    .orElseGet(List::of);
        }

        @Override
        public Map<ResourceLocation, Resource> listResources(
                String namespace,
                Predicate<ResourceLocation> predicate) {
            return Map.of();
        }

        @Override
        public Map<ResourceLocation, List<Resource>>
                listResourceStacks(
                        String namespace,
                        Predicate<ResourceLocation> predicate) {
            return Map.of();
        }

        @Override
        public Stream<PackResources> listPacks() {
            return Stream.of();
        }
    }
}
