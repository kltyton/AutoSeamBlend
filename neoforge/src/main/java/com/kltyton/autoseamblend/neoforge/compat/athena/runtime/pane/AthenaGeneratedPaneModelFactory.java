package com.kltyton.autoseamblend.neoforge.compat.athena.runtime.pane;

import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import com.kltyton.autoseamblend.compat.athena.authoring.AthenaPaneTilePlan;
import com.kltyton.autoseamblend.compat.athena.authoring.AthenaPhysicalTilePlan;
import com.kltyton.autoseamblend.compat.athena.generation.AthenaGeneratedSpritePlan;
import com.kltyton.autoseamblend.compat.athena.runtime.AthenaStateProjection;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.compat.athena.runtime.texture.AthenaGeneratedStateSprites;
import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import earth.terrarium.athena.api.client.models.AthenaQuad;
import earth.terrarium.athena.api.client.neoforge.AthenaBakedModel;
import earth.terrarium.athena.api.client.utils.AppearanceAndTintGetter;
import earth.terrarium.athena.impl.client.models.PaneConnectedBlockModel;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.quad.MutableQuad;

/**
 * 中文：把已规划的 CTM 状态精灵装入 Athena 原生玻璃板模型生命周期。
 *
 * English: Installs planned CTM state sprites into Athena's native pane-model lifecycle.
 */
public final class AthenaGeneratedPaneModelFactory {
    private AthenaGeneratedPaneModelFactory() {}

    public static Optional<BlockStateModel> create(
            Function<Identifier, TextureAtlasSprite> textureGetter,
            ReloadPublication.Generation generation,
            MinecraftSurfaceCatalog.Snapshot surfaces,
            BlockState state,
            BlockStateModel currentModel) {
        Objects.requireNonNull(textureGetter, "textureGetter");
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(surfaces, "surfaces");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(currentModel, "currentModel");
        if (!(state.getBlock() instanceof IronBarsBlock)) {
            return Optional.empty();
        }
        if (currentModel instanceof AthenaBakedModel) {
            return Optional.empty();
        }
        if (generation.generation() != surfaces.generation()) {
            return Optional.empty();
        }

        Optional<EngineQuerySelection> routed =
                EngineQueryRouter.select(state, generation);
        Optional<PaneSources> sources = Optional.ofNullable(
                        surfaces.states().get(state))
                .flatMap(AthenaGeneratedPaneModelFactory::paneSources);
        if (routed.isEmpty()
                || routed.orElseThrow().family() != EngineFamily.ATHENA
                || !routed.orElseThrow().runsAutoBlend()) {
            return Optional.empty();
        }
        if (sources.isEmpty()) {
            return Optional.empty();
        }

        EngineQuerySelection selection = routed.orElseThrow();
        PaneSources pane = sources.orElseThrow();
        ConnectionMethod method = selection.resolveMethod(
                state,
                pane.body().direction(),
                pane.body().sprite().contents().name());
        if (method != ConnectionMethod.CTM) {
            return Optional.empty();
        }

        boolean bodyTranslucent = translucent(pane.body().representativeQuad());
        boolean edgeTranslucent = translucent(pane.edge().representativeQuad());
        Identifier bodyId = pane.body().sprite().contents().name();
        Identifier edgeId = pane.edge().sprite().contents().name();
        Int2ObjectMap<Material> materials = new Int2ObjectArrayMap<>();
        materials.put(
                AthenaPaneTilePlan.Role.PARTICLE.nativeIndex(),
                new Material(bodyId, bodyTranslucent));
        materials.put(
                AthenaPaneTilePlan.Role.EMPTY.nativeIndex(),
                generatedMaterial(
                        bodyId,
                        AthenaPaneTilePlan.Role.EMPTY,
                        bodyTranslucent));
        materials.put(
                AthenaPaneTilePlan.Role.CENTER.nativeIndex(),
                generatedMaterial(
                        bodyId,
                        AthenaPaneTilePlan.Role.CENTER,
                        bodyTranslucent));
        materials.put(
                AthenaPaneTilePlan.Role.VERTICAL.nativeIndex(),
                generatedMaterial(
                        bodyId,
                        AthenaPaneTilePlan.Role.VERTICAL,
                        bodyTranslucent));
        materials.put(
                AthenaPaneTilePlan.Role.HORIZONTAL.nativeIndex(),
                generatedMaterial(
                        bodyId,
                        AthenaPaneTilePlan.Role.HORIZONTAL,
                        bodyTranslucent));
        materials.put(
                AthenaPaneTilePlan.Role.EDGE.nativeIndex(),
                new Material(edgeId, edgeTranslucent));
        // 中文：自动规则没有原生 side_edge 声明；窄边按 Athena 工厂合同回退到 particle。
        // English: Automatic rules have no native side_edge declaration; match Athena's particle fallback.
        materials.put(
                AthenaPaneTilePlan.Role.SIDE_EDGE.nativeIndex(),
                new Material(bodyId, bodyTranslucent));
        ArrayList<Integer> missingSlots = new ArrayList<>();
        for (AthenaPaneTilePlan.Role role : AthenaPaneTilePlan.Role.values()) {
            Material material = materials.get(role.nativeIndex());
            TextureAtlasSprite materialSprite = textureGetter.apply(
                    material.sprite());
            if (missing(materialSprite)) {
                missingSlots.add(role.nativeIndex());
            }
        }
        if (!missingSlots.isEmpty()) {
            return Optional.empty();
        }

        Function<Material, Material.Baked> baker = material ->
                new Material.Baked(
                        textureGetter.apply(material.sprite()),
                        material.forceTranslucent());
        ConnectionRuleSet<Block> rules = generation.selectors().rules();
        BlockStateModel nativeModel = new AthenaBakedModel(
                new RuleAwarePaneModel(materials, rules),
                baker);
        return Optional.of(new PaneTopUvModel(
                nativeModel,
                textureGetter.apply(edgeId)));
    }

    private static Optional<PaneSources> paneSources(
            MinecraftSurfaceCatalog.StateSurface stateSurface) {
        Optional<FaceSurface> edge = stateSurface.faces().entrySet().stream()
                .filter(entry -> entry.getKey().getAxis().isVertical())
                .flatMap(entry -> entry.getValue().stream())
                .filter(surface -> !surface.fullyTransparent())
                .max(Comparator.comparingDouble(surface ->
                        area(surface.representativeQuad())));
        if (edge.isEmpty()) {
            return Optional.empty();
        }
        Identifier edgeId = edge.orElseThrow().sprite().contents().name();
        Optional<FaceSurface> body = stateSurface.faces().entrySet().stream()
                .filter(entry -> entry.getKey().getAxis().isHorizontal())
                .flatMap(entry -> entry.getValue().stream())
                .filter(surface -> !surface.fullyTransparent())
                .max(Comparator
                        .comparing((FaceSurface surface) ->
                                !surface.sprite().contents().name().equals(edgeId))
                        .thenComparingDouble(surface ->
                                area(surface.representativeQuad())));
        return body.map(value -> new PaneSources(value, edge.orElseThrow()));
    }

    private static Material generatedMaterial(
            Identifier source,
            AthenaPaneTilePlan.Role role,
            boolean forceTranslucent) {
        int slot = AthenaPhysicalTilePlan.selectSlot(
                NeighborConnections.fromBits(
                        AthenaPaneTilePlan.generatedConnectionBits(role)));
        return new Material(
                AthenaGeneratedSpritePlan.generatedId(
                        source,
                        ConnectionMethod.CTM,
                        slot),
                forceTranslucent);
    }

    private static boolean translucent(BakedQuad quad) {
        return quad.materialInfo().layer() == ChunkSectionLayer.TRANSLUCENT;
    }

    private static boolean missing(TextureAtlasSprite sprite) {
        return sprite == null
                || sprite.contents().name().equals(
                        MissingTextureAtlasSprite.getLocation());
    }

    private static double area(BakedQuad quad) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < 4; vertex++) {
            minX = Math.min(minX, quad.position(vertex).x());
            minY = Math.min(minY, quad.position(vertex).y());
            minZ = Math.min(minZ, quad.position(vertex).z());
            maxX = Math.max(maxX, quad.position(vertex).x());
            maxY = Math.max(maxY, quad.position(vertex).y());
            maxZ = Math.max(maxZ, quad.position(vertex).z());
        }
        float x = maxX - minX;
        float y = maxY - minY;
        float z = maxZ - minZ;
        return Math.max(x * y, Math.max(x * z, y * z));
    }

    private record PaneSources(
            FaceSurface body,
            FaceSurface edge) {}

    /**
     * 中文：保留 Athena 的原生玻璃板几何，只解除东西向顶面几何范围与 UV 范围的错误绑定。
     *
     * <p>English: Retains Athena's native pane geometry while decoupling the east/west top-arm
     * geometry range from its incorrectly bound UV range.
     */
    private static final class PaneTopUvModel
            extends DelegateBlockStateModel {
        private static final float EPSILON = 1.0e-6F;

        private final TextureAtlasSprite edgeSprite;

        private PaneTopUvModel(
                BlockStateModel delegate,
                TextureAtlasSprite edgeSprite) {
            super(Objects.requireNonNull(delegate, "delegate"));
            this.edgeSprite = Objects.requireNonNull(
                    edgeSprite,
                    "edgeSprite");
        }

        @Override
        public void collectParts(
                BlockAndTintGetter level,
                BlockPos pos,
                BlockState state,
                RandomSource random,
                List<BlockStateModelPart> output) {
            ArrayList<BlockStateModelPart> nativeParts =
                    new ArrayList<>();
            super.collectParts(
                    level,
                    pos,
                    state,
                    random,
                    nativeParts);
            for (BlockStateModelPart part : nativeParts) {
                output.add(correct(part, edgeSprite));
            }
        }

        private static BlockStateModelPart correct(
                BlockStateModelPart part,
                TextureAtlasSprite edgeSprite) {
            LinkedHashMap<Direction, List<BakedQuad>> quads =
                    new LinkedHashMap<>();
            boolean changed = false;
            for (Direction cullFace : Direction.values()) {
                List<BakedQuad> source =
                        part.getQuads(cullFace);
                List<BakedQuad> result = correct(
                        source,
                        edgeSprite);
                quads.put(cullFace, result);
                changed |= result != source;
            }
            List<BakedQuad> source = part.getQuads(null);
            List<BakedQuad> result = correct(
                    source,
                    edgeSprite);
            quads.put(null, result);
            changed |= result != source;
            return changed
                    ? new CorrectedPart(part, quads)
                    : part;
        }

        private static List<BakedQuad> correct(
                List<BakedQuad> source,
                TextureAtlasSprite edgeSprite) {
            if (source.isEmpty()) {
                return source;
            }
            ArrayList<BakedQuad> output = null;
            for (int index = 0; index < source.size(); index++) {
                BakedQuad quad = source.get(index);
                BakedQuad replacement = correct(
                        quad,
                        edgeSprite);
                if (replacement == quad) {
                    if (output != null) {
                        output.add(quad);
                    }
                    continue;
                }
                if (output == null) {
                    output = new ArrayList<>(source.size());
                    output.addAll(source.subList(0, index));
                }
                output.add(replacement);
            }
            return output == null
                    ? source
                    : List.copyOf(output);
        }

        private static BakedQuad correct(
                BakedQuad quad,
                TextureAtlasSprite edgeSprite) {
            if (!quad.direction().getAxis().isVertical()
                    || !quad.materialInfo()
                            .sprite()
                            .contents()
                            .name()
                            .equals(edgeSprite.contents().name())) {
                return quad;
            }
            float minX = Float.POSITIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxZ = Float.NEGATIVE_INFINITY;
            for (int vertex = 0;
                    vertex < BakedQuad.VERTEX_COUNT;
                    vertex++) {
                minX = Math.min(
                        minX,
                        quad.position(vertex).x());
                minZ = Math.min(
                        minZ,
                        quad.position(vertex).z());
                maxX = Math.max(
                        maxX,
                        quad.position(vertex).x());
                maxZ = Math.max(
                        maxZ,
                        quad.position(vertex).z());
            }
            if (maxX - minX <= maxZ - minZ + EPSILON) {
                return quad;
            }
            float atlasWidth = edgeSprite.getU1()
                    - edgeSprite.getU0();
            float atlasHeight = edgeSprite.getV1()
                    - edgeSprite.getV0();
            if (Math.abs(atlasWidth) <= EPSILON
                    || Math.abs(atlasHeight) <= EPSILON) {
                return quad;
            }
            MutableQuad corrected =
                    new MutableQuad().setFrom(quad);
            for (int vertex = 0;
                    vertex < BakedQuad.VERTEX_COUNT;
                    vertex++) {
                float localU = (corrected.u(vertex)
                                - edgeSprite.getU0())
                        / atlasWidth;
                float localV = (corrected.v(vertex)
                                - edgeSprite.getV0())
                        / atlasHeight;
                corrected.setUv(
                        vertex,
                        edgeSprite.getU(localV),
                        edgeSprite.getV(localU));
            }
            return corrected.toBakedQuad();
        }
    }

    private record CorrectedPart(
            BlockStateModelPart delegate,
            Map<Direction, List<BakedQuad>> quads)
            implements BlockStateModelPart {
        private CorrectedPart {
            Objects.requireNonNull(delegate, "delegate");
            quads = Collections.unmodifiableMap(
                    new LinkedHashMap<>(
                            Objects.requireNonNull(
                                    quads,
                                    "quads")));
        }

        @Override
        public List<BakedQuad> getQuads(
                Direction direction) {
            return quads.getOrDefault(
                    direction,
                    List.of());
        }

        @Override
        public boolean useAmbientOcclusion() {
            return delegate.useAmbientOcclusion();
        }

        @Override
        public Material.Baked particleMaterial() {
            return delegate.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return delegate.materialFlags();
        }
    }

    private static final class RuleAwarePaneModel
            extends PaneConnectedBlockModel {
        private final ConnectionRuleSet<Block> rules;

        private RuleAwarePaneModel(
                Int2ObjectMap<Material> materials,
                ConnectionRuleSet<Block> rules) {
            // 中文：启用 Athena 4.7.3 原生的无缝单臂玻璃板分支；该分支仍使用原生 CtmState、形状判定和 Quad 生命周期。
            // English: Enable Athena 4.7.3's native seamless one-arm pane branch; it still uses native CtmState, shape checks, and quad lifecycle.
            super(materials, true);
            this.rules = Objects.requireNonNull(rules, "rules");
        }

        @Override
        public List<AthenaQuad> getQuads(
                AppearanceAndTintGetter getter,
                BlockState state,
                BlockPos pos,
            Direction face) {
            // 中文：Athena 原实现按 BlockState 引用剔除上下盖板；项目合同要求同一连接组的属性变体也连续。
            // English: Athena culls vertical caps by BlockState identity; the product contract also joins property variants in one connection group.
            if (face.getAxis().isVertical()
                    && AthenaStateProjection.connects(
                            rules,
                            state.getBlock(),
                            getter.getBlockState(pos.relative(face)).getBlock())) {
                return List.of();
            }
            return super.getQuads(
                    getter,
                    state,
                    pos,
                    face);
        }

        @Override
        protected boolean isConnected(
                BlockState neighbor,
                BlockState origin,
                Direction direction) {
            boolean nativeConnected = super.isConnected(
                    neighbor,
                    origin,
                    direction);
            if (nativeConnected) {
                return true;
            }
            boolean rulesConnected = AthenaStateProjection.connects(
                    rules,
                    origin.getBlock(),
                    neighbor.getBlock());
            if (!rulesConnected) {
                return false;
            }
            if (origin.getBlock() == neighbor.getBlock()) {
                return true;
            }
            // 中文：跨 ID 标签组仍复用 Athena 对邻居玻璃板形状的原生判定，不复制其方向算法。
            // English: Cross-id tag groups still reuse Athena's native neighbor-pane shape test without copying its direction algorithm.
            return super.isConnected(
                    neighbor,
                    neighbor,
                    direction);
        }
    }
}
