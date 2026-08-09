package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import com.kltyton.autoseamblend.compat.athena.runtime.AthenaAcceptedDocumentIdentity;
import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeOwnershipPolicy;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.query.NativeQueryObservation;
import com.kltyton.autoseamblend.engine.routing.NativeModelOwnershipProvider;
import com.kltyton.autoseamblend.engine.routing.NativeQueryOwnershipProvider;
import com.kltyton.autoseamblend.fabric.compat.athena.mixin.AthenaBakedModelAccessor;
import earth.terrarium.athena.api.client.fabric.AthenaBakedModel;
import earth.terrarium.athena.api.client.fabric.WrappedGetter;
import earth.terrarium.athena.api.client.models.AthenaBlockModel;
import earth.terrarium.athena.api.client.models.FactoryManager;
import earth.terrarium.athena.api.client.utils.AthenaUnbakedModelLoader;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：捕获 Athena 已接受模型并查询当前面。Loader 侧只保留 AthenaBakedModel、material、
 * FactoryManager loaderIds 与 blockId 快照，以及 Fabric/Mixin API 映射（WrappedGetter、
 * AthenaBakedModelAccessor、BakedQuad→精灵槽提取）；缺失精灵判定、候选同名 owns、
 * 观察结果裁决与精确文档身份解析全部委托 common AthenaNativeOwnershipPolicy /
 * AthenaAcceptedDocumentIdentity。绝不构造 exact(empty)：exact 只由 common 在身份可解析时
 * 发布，身份不可解析时返回明确 unknown。
 *
 * <p>English: Captures Athena's accepted model and queries the current face. The Loader side
 * keeps only the AthenaBakedModel, material, FactoryManager loaderIds and blockId snapshots,
 * plus the Fabric/Mixin API mapping (WrappedGetter, AthenaBakedModelAccessor, BakedQuad to
 * sprite-slot extraction); missing-sprite detection, same-name candidate ownership, the
 * observation verdict, and exact document-identity resolution all delegate to the common
 * AthenaNativeOwnershipPolicy / AthenaAcceptedDocumentIdentity. exact(empty) is never
 * constructed: exact is published by common only when the identity resolves, and an
 * unresolvable identity yields an explicit unknown.
 */
public enum FabricAthenaNativeModelOwnershipProvider
        implements NativeModelOwnershipProvider,
                NativeQueryOwnershipProvider {
    INSTANCE;

    private final ConcurrentMap<Long, Map<BlockState, QueryProbe>>
            generations = new ConcurrentHashMap<>();
    private Map<BlockState, QueryProbe> capturing =
            new LinkedHashMap<>();
    private long capturingGeneration = -1;

    @Override
    public String engineId() {
        return "athena";
    }

    @Override
    public EngineFamily family() {
        return EngineFamily.ATHENA;
    }

    @Override
    public boolean owns(BlockStateModel model) {
        return model instanceof AthenaBakedModel;
    }

    @Override
    public synchronized void beginCapture(
            long generation) {
        if (generation <= 0) {
            throw new IllegalArgumentException(
                    "generation must be positive");
        }
        generations.keySet().removeIf(value ->
                value < generation - 1);
        capturing = new LinkedHashMap<>();
        capturingGeneration = generation;
    }

    @Override
    public synchronized void capture(
            BlockState state,
            BlockStateModel model) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(model, "model");
        if (!(model instanceof AthenaBakedModel)) {
            return;
        }
        AthenaBakedModelAccessor accessor =
                (AthenaBakedModelAccessor) (Object) model;
        capturing.put(
                state,
                new QueryProbe(
                        accessor.autoseamblend$getModel(),
                        accessor.autoseamblend$getMaterials(),
                        state.getBlock()
                                .builtInRegistryHolder()
                                .key()
                                .identifier(),
                        loaderIds()));
    }

    /**
     * 中文：FactoryManager loaderIds 快照：在捕获时固定 4.7.3 loader 表键空间顺序，
     * 供 common AthenaAcceptedDocumentIdentity 按 loaderIds 顺序求值。
     *
     * <p>English: FactoryManager loaderIds snapshot: locks the 4.7.3 loader table key-space
     * order at capture time for common AthenaAcceptedDocumentIdentity evaluation.
     */
    private static List<Identifier> loaderIds() {
        return FactoryManager.loaders().stream()
                .map(AthenaUnbakedModelLoader::id)
                .toList();
    }

    @Override
    public synchronized void endCapture() {
        if (capturingGeneration < 0) {
            throw new IllegalStateException(
                    "Athena ownership capture has not begun");
        }
        generations.put(
                capturingGeneration,
                Map.copyOf(capturing));
        capturing = new LinkedHashMap<>();
        capturingGeneration = -1;
    }

    @Override
    public synchronized void abortCapture(
            long generation) {
        generations.remove(generation);
        if (capturingGeneration == generation) {
            capturing = new LinkedHashMap<>();
            capturingGeneration = -1;
        }
    }

    @Override
    public NativeQueryObservation observe(
            long generation,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            BakedQuad quad,
            TextureAtlasSprite sprite) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(quad, "quad");
        Objects.requireNonNull(sprite, "sprite");
        if (AthenaNativeOwnershipPolicy.missingSprite(
                sprite)) {
            return NativeQueryObservation.noMatch();
        }
        Map<BlockState, QueryProbe> probes =
                generations.get(generation);
        if (probes == null) {
            return NativeQueryObservation.unknown(
                    "ATHENA_MODEL_CAPTURE_GENERATION_UNAVAILABLE");
        }
        QueryProbe probe = probes.get(state);
        boolean owns = probe != null
                && probe.owns(
                        level,
                        pos,
                        state,
                        quad,
                        sprite);
        return AthenaNativeOwnershipPolicy
                .resolveObservation(
                        owns,
                        owns
                                ? AthenaAcceptedDocumentIdentity
                                        .resolve(
                                                probe.blockId(),
                                                probe.loaderIds())
                                : Optional.empty());
    }

    private record QueryProbe(
            AthenaBlockModel model,
            Int2ObjectMap<Material.Baked> materials,
            Identifier blockId,
            List<Identifier> loaderIds) {
        private QueryProbe {
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(materials, "materials");
            Objects.requireNonNull(blockId, "blockId");
            Objects.requireNonNull(loaderIds, "loaderIds");
        }

        private boolean owns(
                BlockAndTintGetter level,
                BlockPos pos,
                BlockState state,
                BakedQuad rendered,
                TextureAtlasSprite sprite) {
            List<TextureAtlasSprite> candidates =
                    model.getQuads(
                                    new WrappedGetter(level),
                                    state,
                                    pos,
                                    rendered.direction())
                            .stream()
                            .map(quad -> materials.get(
                                    quad.sprite()))
                            .filter(Objects::nonNull)
                            .map(Material.Baked::sprite)
                            .toList();
            return AthenaNativeOwnershipPolicy
                    .ownsByCandidateSprites(
                            candidates,
                            sprite);
        }
    }
}
