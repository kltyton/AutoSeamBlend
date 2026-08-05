package com.kltyton.autoseamblend.neoforge.compat.athena.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.neoforge.compat.athena.mixin.AthenaBakedModelAccessor;
import com.kltyton.autoseamblend.mixin.athena.AthenaResourceLoaderAccessor;
import com.kltyton.autoseamblend.engine.query.AcceptedNativeDocument;
import com.kltyton.autoseamblend.engine.query.NativeDocumentIdentity;
import com.kltyton.autoseamblend.engine.query.NativeQueryObservation;
import com.kltyton.autoseamblend.engine.routing.NativeQueryOwnershipProvider;
import com.kltyton.autoseamblend.engine.routing.NativeModelOwnershipProvider;
import earth.terrarium.athena.api.client.models.AthenaBlockModel;
import earth.terrarium.athena.api.client.models.FactoryManager;
import earth.terrarium.athena.api.client.neoforge.AthenaBakedModel;
import earth.terrarium.athena.api.client.neoforge.WrappedGetter;
import earth.terrarium.athena.api.client.utils.AthenaUnbakedModelLoader;
import earth.terrarium.athena.impl.loading.AthenaResourceLoader;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：捕获 Athena 已接受模型并查询当前面；只在原生 getData 结果能与直接 data 或
 * blockstateData 项按身份对应时发布精确文档身份。
 *
 * English:
 * Captures Athena's accepted model and queries the current face. An exact document identity is
 * published only when native getData returns the same direct data or blockstateData object.
 */
public enum AthenaNativeModelOwnershipProvider
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
        AthenaBakedModelAccessor accessor =
                (AthenaBakedModelAccessor)
                        Objects.requireNonNull(
                                model,
                                "model");
        capturing.put(
                state,
                new QueryProbe(
                        accessor.autoseamblend$model(),
                        accessor.autoseamblend$materials(),
                        acceptedIdentity(state)));
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
        if (sprite.contents()
                .name()
                .equals(MissingTextureAtlasSprite.getLocation())) {
            return NativeQueryObservation.noMatch();
        }
        Map<BlockState, QueryProbe> probes =
                generations.get(generation);
        if (probes == null) {
            return NativeQueryObservation.unknown(
                    "ATHENA_MODEL_CAPTURE_GENERATION_UNAVAILABLE");
        }
        QueryProbe probe = probes.get(state);
        if (probe == null
                || !probe.owns(
                        level,
                        pos,
                        state,
                        quad,
                        sprite)) {
            return NativeQueryObservation.noMatch();
        }
        return probe.identity()
                .map(identity -> NativeQueryObservation.exact(
                        java.util.List.of(
                                AcceptedNativeDocument
                                        .identityOnly(identity))))
                .orElseGet(() -> NativeQueryObservation.unknown(
                        "ATHENA_ACCEPTED_MODEL_DOCUMENT_IDENTITY_UNAVAILABLE"));
    }

    private static Optional<NativeDocumentIdentity>
            acceptedIdentity(BlockState state) {
        Identifier targetId = state.getBlock()
                .builtInRegistryHolder()
                .key()
                .identifier();
        AthenaResourceLoaderAccessor resources =
                (AthenaResourceLoaderAccessor)
                        AthenaResourceLoader.INSTANCE;
        JsonElement directData =
                resources.autoseamblend$data().get(targetId);
        JsonObject directBlockstate =
                resources.autoseamblend$blockstateData()
                        .get(targetId);
        for (AthenaUnbakedModelLoader loader :
                FactoryManager.loaders()) {
            JsonObject accepted = AthenaResourceLoader.getData(
                    loader.id(),
                    targetId);
            if (accepted == null) {
                continue;
            }
            if (accepted == directData) {
                return Optional.of(
                        NativeDocumentIdentity.resourceOnly(
                                targetId.getNamespace()
                                        + ":athena/"
                                        + targetId.getPath()
                                        + ".json"));
            }
            if (accepted == directBlockstate) {
                return Optional.of(
                        NativeDocumentIdentity.resourceOnly(
                                targetId.getNamespace()
                                        + ":blockstates/"
                                        + targetId.getPath()
                                        + ".json"));
            }
        }
        return Optional.empty();
    }

    private record QueryProbe(
            AthenaBlockModel model,
            Int2ObjectMap<Material.Baked> materials,
            Optional<NativeDocumentIdentity> identity) {
        private QueryProbe {
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(materials, "materials");
            Objects.requireNonNull(identity, "identity");
        }

        private boolean owns(
                BlockAndTintGetter level,
                BlockPos pos,
                BlockState state,
                BakedQuad rendered,
                TextureAtlasSprite sprite) {
            return model.getQuads(
                            new WrappedGetter(level),
                            state,
                            pos,
                            rendered.direction())
                    .stream()
                    .map(quad -> materials.get(
                            quad.sprite()))
                    .filter(Objects::nonNull)
                    .map(Material.Baked::sprite)
                    .filter(candidate -> !candidate
                            .contents()
                            .name()
                            .equals(MissingTextureAtlasSprite
                                    .getLocation()))
                    .anyMatch(candidate -> candidate
                            .contents()
                            .name()
                            .equals(sprite.contents()
                                    .name()));
        }
    }
}
