package com.kltyton.autoseamblend.neoforge.compat.athena.runtime;

import com.kltyton.autoseamblend.compat.athena.runtime.AthenaAcceptedDocumentIdentity;
import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeOwnershipPolicy;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.query.NativeDocumentIdentity;
import com.kltyton.autoseamblend.engine.query.NativeQueryObservation;
import com.kltyton.autoseamblend.engine.routing.NativeModelOwnershipProvider;
import com.kltyton.autoseamblend.engine.routing.NativeQueryOwnershipProvider;
import com.kltyton.autoseamblend.neoforge.compat.athena.mixin.AthenaBakedModelAccessor;
import com.kltyton.autoseamblend.neoforge.compat.athena.mixin.ConnectedBlockModelAccessor;
import earth.terrarium.athena.api.client.models.AthenaQuad;
import earth.terrarium.athena.api.client.models.AthenaBlockModel;
import earth.terrarium.athena.api.client.models.neoforge.FactoryManagerImpl;
import earth.terrarium.athena.api.client.neoforge.AthenaBakedModel;
import earth.terrarium.athena.api.client.neoforge.WrappedGetter;
import earth.terrarium.athena.impl.client.models.ConnectedBlockModel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiPredicate;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：捕获 Athena 已接受模型并查询当前面。
 *
 * English: Captures Athena's accepted model and queries the current face.
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
    public boolean owns(BakedModel model) {
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
            BakedModel model) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(model, "model");
        if (!(model instanceof AthenaBakedModel athena)) {
            return;
        }
        AthenaBlockModel nativeModel =
                ((AthenaBakedModelAccessor) (Object) athena)
                        .autoseamblend$model();
        Int2ObjectMap<TextureAtlasSprite> textures =
                ((AthenaBakedModelAccessor) (Object) athena)
                        .autoseamblend$textures();
        BiPredicate<BlockState, BlockState> predicate =
                null;
        if (nativeModel
                instanceof ConnectedBlockModel connected) {
            predicate =
                    ((ConnectedBlockModelAccessor) (Object) connected)
                            .autoseamblend$getConnectTo();
        }
        // 中文：精确文档身份：block id（BuiltInRegistries.BLOCK 键）经 4.0.6 NeoForge
        // FactoryManagerImpl 注册的 loader id 集合解析；命中即 identity 非空，observe 时
        // 发布 exact 文档而非 unknown。
        // English: Exact document identity: the block id (BuiltInRegistries.BLOCK key) is
        // resolved through the loader ids registered by the 4.0.6 NeoForge FactoryManagerImpl;
        // a hit makes identity present so observe publishes an exact document, not unknown.
        Optional<NativeDocumentIdentity> identity = resolveIdentity(
                BuiltInRegistries.BLOCK.getKey(
                        state.getBlock()),
                new ArrayList<>(
                        FactoryManagerImpl.getTypes()));
        capturing.put(
                state,
                new QueryProbe(
                        predicate,
                        nativeModel,
                        textures,
                        identity));
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
        // 中文：26.1.2 语义：缺失纹理精灵绝不视为已接受所有权。
        // English: 26.1.2 semantics: a missing-texture sprite is never accepted as owned.
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
        if (probe == null) {
            return NativeQueryObservation.noMatch();
        }
        if (probe.predicate() != null
                && !probe.predicate().test(
                        state,
                        state)) {
            return NativeQueryObservation.noMatch();
        }
        // 中文：26.1.2 面/精灵所有权：原生模型按当前面发射的 quad 槽映射到纹理，
        // 任一候选与渲染精灵同名即命中；无法命中不发布任何所有权。
        // English: 26.1.2 face/sprite ownership: the native model's quads for the
        // current face map their sprite slots to textures; a same-named candidate is
        // required to claim ownership, otherwise nothing is published.
        List<TextureAtlasSprite> candidates = nativeSprites(
                probe.model(),
                probe.textures(),
                level,
                pos,
                state,
                quad.getDirection());
        return AthenaNativeOwnershipPolicy.resolveObservation(
                AthenaNativeOwnershipPolicy.ownsByCandidateSprites(
                        candidates,
                        sprite),
                probe.identity());
    }

    /**
     * 中文：原生发射候选纹理：model.getQuads 的每个 quad 槽位经 textures 表映射为
     * 实际精灵，null 槽位丢弃；与 26.1.2 materials 映射同构。
     *
     * <p>English: Native-emission candidate textures: each emitted quad's sprite slot is
     * resolved through the textures table, dropping null slots; isomorphic to the 26.1.2
     * materials mapping.
     */
    static List<TextureAtlasSprite> nativeSprites(
            AthenaBlockModel model,
            Int2ObjectMap<TextureAtlasSprite> textures,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            Direction face) {
        List<TextureAtlasSprite> candidates =
                new ArrayList<>();
        if (face == null || textures == null) {
            return candidates;
        }
        for (AthenaQuad quad : model.getQuads(
                new WrappedGetter(level),
                state,
                pos,
                face)) {
            TextureAtlasSprite texture =
                    textures.get(quad.sprite());
            if (texture != null) {
                candidates.add(texture);
            }
        }
        return candidates;
    }

    /**
     * 中文：生产路径的 identity 解析，委托 common {@link AthenaAcceptedDocumentIdentity}；
     * blockId 由调用方（capture）从 BuiltInRegistries.BLOCK 派生，loaderIds 来自 4.0.6
     * NeoForge FactoryManagerImpl。
     *
     * <p>English: Production identity resolution delegating to common
     * {@link AthenaAcceptedDocumentIdentity}; blockId is derived by the caller (capture)
     * from BuiltInRegistries.BLOCK and loaderIds come from the 4.0.6 NeoForge
     * FactoryManagerImpl.
     */
    static Optional<NativeDocumentIdentity> resolveIdentity(
            ResourceLocation blockId,
            List<ResourceLocation> loaderIds) {
        return AthenaAcceptedDocumentIdentity.resolve(
                blockId,
                loaderIds);
    }

    private record QueryProbe(
            BiPredicate<BlockState, BlockState> predicate,
            AthenaBlockModel model,
            Int2ObjectMap<TextureAtlasSprite> textures,
            Optional<NativeDocumentIdentity> identity) {
        private QueryProbe {
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(textures, "textures");
            Objects.requireNonNull(identity, "identity");
        }
    }
}
