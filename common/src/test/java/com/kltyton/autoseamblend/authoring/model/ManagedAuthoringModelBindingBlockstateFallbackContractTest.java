package com.kltyton.autoseamblend.authoring.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 中文：模型绑定回退合同（1.20.1 兄弟测试移植）。当启发式 {@code namespace:block/<block>}
 * 模型缺失时，绑定必须经 blockstate 的 variants/multipart 模型引用回退，选中一个源纹理键
 * 非空的实体模型 ID。26.1.2 的 MinecraftModelTextureBindings/ModelTextureBindings 只在模型
 * 文档缺失时终止父链并返回空键，未实现该回退，因此两个断言当前为 RED。
 *
 * <p>English: Model binding fallback contract (ported from the 1.20.1 sibling test). When the
 * heuristic {@code namespace:block/<block>} model is missing, binding must fall back through
 * the blockstate variants/multipart model references and select a concrete model id whose source
 * texture keys are nonempty. The 26.1.2 MinecraftModelTextureBindings/ModelTextureBindings
 * terminate the parent chain and return no keys when the heuristic model document is missing,
 * so both assertions are currently RED.
 */
class ManagedAuthoringModelBindingBlockstateFallbackContractTest {
    private static final String HEURISTIC_MODEL =
            "minecraft:block/stone";
    private static final String SOURCE_TEXTURE =
            "minecraft:block/stone";

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.setVersion(
                DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();
    }

    @Test
    void missingHeuristicModelFallsBackThroughVariantReference() {
        ManagedAuthoringRule rule = rule(
                "{\"variants\":{\"\":{\"model\":\"minecraft:block/cube_all\"}}}");

        assertEquals("minecraft:block/cube_all", rule.originalModelId());
        assertFalse(
                rule.sourceTextureKeys().isEmpty(),
                "missing heuristic model " + HEURISTIC_MODEL
                        + " must fall back through the blockstate variants reference to a "
                        + "concrete model with nonempty source texture keys; actual keys: "
                        + rule.sourceTextureKeys());
    }

    @Test
    void missingHeuristicModelFallsBackThroughMultipartReference() {
        ManagedAuthoringRule rule = rule(
                "{\"multipart\":[{\"apply\":{\"model\":\"minecraft:block/cube_all\"}}]}");

        assertEquals("minecraft:block/cube_all", rule.originalModelId());
        assertFalse(
                rule.sourceTextureKeys().isEmpty(),
                "missing heuristic model " + HEURISTIC_MODEL
                        + " must fall back through the blockstate multipart reference to a "
                        + "concrete model with nonempty source texture keys; actual keys: "
                        + rule.sourceTextureKeys());
    }

    private static ManagedAuthoringRule rule(String blockstate) {
        Map<Identifier, Resource> resources = Map.of(
                Identifier.fromNamespaceAndPath(
                        "minecraft",
                        "blockstates/stone.json"),
                resource(blockstate),
                Identifier.fromNamespaceAndPath(
                        "minecraft",
                        "models/block/cube_all.json"),
                resource(
                        "{\"textures\":{\"all\":\""
                                + SOURCE_TEXTURE
                                + "\"}}"));
        ManagedAuthoringDraft draft = new ManagedAuthoringDraft(
                "minecraft:stone",
                SOURCE_TEXTURE,
                HEURISTIC_MODEL,
                ConnectionMethod.CTM,
                ConnectionMethod.CTM,
                false,
                false);
        return ManagedAuthoringProjectDrafts.createRule(
                draft,
                new MapResourceManager(resources));
    }

    private static Resource resource(String json) {
        byte[] bytes = json.getBytes(
                StandardCharsets.UTF_8);
        return new Resource(
                null,
                () -> new ByteArrayInputStream(bytes),
                ResourceMetadata.EMPTY_SUPPLIER);
    }

    /** 中文：只服务模型/blockstate 读取的最小资源管理器替身。 / English: Minimal resource manager double serving only model and blockstate reads. */
    private static final class MapResourceManager
            implements ResourceManager {
        private final Map<Identifier, Resource> resources;

        private MapResourceManager(
                Map<Identifier, Resource> resources) {
            this.resources = Map.copyOf(resources);
        }

        @Override
        public Optional<Resource> getResource(
                Identifier location) {
            return Optional.ofNullable(
                    resources.get(location));
        }

        @Override
        public List<Resource> getResourceStack(
                Identifier location) {
            return Optional.ofNullable(
                            resources.get(location))
                    .map(List::of)
                    .orElseGet(List::of);
        }

        @Override
        public Map<Identifier, Resource> listResources(
                String namespace,
                Predicate<Identifier> predicate) {
            return Map.of();
        }

        @Override
        public Map<Identifier, List<Resource>>
                listResourceStacks(
                        String namespace,
                        Predicate<Identifier> predicate) {
            return Map.of();
        }

        @Override
        public Set<String> getNamespaces() {
            return Set.of("minecraft");
        }

        @Override
        public Stream<PackResources> listPacks() {
            return Stream.of();
        }
    }
}
