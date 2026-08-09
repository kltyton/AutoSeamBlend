package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import com.kltyton.autoseamblend.compat.athena.runtime.AthenaAcceptedDocumentIdentity;
import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeOwnershipPolicy;
import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.query.NativeDocumentIdentity;
import com.kltyton.autoseamblend.engine.query.NativeQueryObservation;
import com.kltyton.autoseamblend.engine.routing.NativeModelOwnershipProvider;
import com.kltyton.autoseamblend.engine.routing.NativeQueryOwnershipProvider;
import com.kltyton.autoseamblend.fabric.compat.athena.mixin.AthenaBakedModelAccessor;
import com.kltyton.autoseamblend.fabric.compat.athena.mixin.ConnectedBlockModelAccessor;
import earth.terrarium.athena.api.client.fabric.AthenaBakedModel;
import earth.terrarium.athena.api.client.fabric.WrappedGetter;
import earth.terrarium.athena.api.client.models.AthenaQuad;
import earth.terrarium.athena.api.client.models.AthenaBlockModel;
import earth.terrarium.athena.api.client.models.fabric.FactoryManagerImpl;
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
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：捕获 Athena 已接受模型并查询当前面；以 26.1.2 语义与 NeoForge 已修复实现为
 * 基线：missing 精灵 noMatch、predicate 前置、原生发射槽经 textures 表映射后按同名
 * 精灵判定、文档身份由 BuiltInRegistries 块 ID + Fabric FactoryManagerImpl.LOADERS
 * 键空间 + common resolver 解析，绝不发布 exact(empty)。
 *
 * <p>English: Captures Athena's accepted model and queries the current face, following the
 * 26.1.2 semantics and the NeoForge-fixed implementation: missing sprites are noMatch, the
 * predicate is checked first, native emitted slots are mapped through the textures table and
 * matched by same-name sprites, and the document identity is resolved from the
 * BuiltInRegistries block id plus the Fabric FactoryManagerImpl.LOADERS key space through
 * the common resolver; exact(empty) is never published.
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
                        .autoseamblend$getModel();
        Int2ObjectMap<TextureAtlasSprite> textures =
                ((AthenaBakedModelAccessor) (Object) athena)
                        .autoseamblend$getTextures();
        BiPredicate<BlockState, BlockState> predicate =
                null;
        if (nativeModel
                instanceof ConnectedBlockModel connected) {
            predicate =
                    ((ConnectedBlockModelAccessor) (Object) connected)
                            .autoseamblend$getConnectTo();
        }
        capturing.put(
                state,
                new QueryProbe(
                        predicate,
                        nativeModel,
                        textures,
                        resolveIdentity(state)));
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
        return observeProbe(
                probe,
                level,
                pos,
                state,
                quad,
                sprite);
    }

    /**
     * 中文：可单测的观察裁决：missing 精灵 noMatch；predicate 前置；原生槽映射后的
     * 候选精灵同名判定；owns 时身份可解析→精确文档，否则明确 unknown（绝不
     * exact(empty)）。与 NeoForge 已修复 observe 逐段同构。
     *
     * <p>English: Unit-testable observation adjudication: missing sprites are noMatch; the
     * predicate is checked first; ownership needs a same-named candidate among the mapped
     * native slots; owned states with a resolvable identity yield an exact document,
     * otherwise an explicit unknown (never exact(empty)). Isomorphic to the NeoForge-fixed
     * observe.
     */
    static NativeQueryObservation observeProbe(
            QueryProbe probe,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            BakedQuad quad,
            TextureAtlasSprite sprite) {
        Objects.requireNonNull(probe, "probe");
        // 中文：26.1.2 语义：缺失纹理精灵绝不视为已接受所有权。
        // English: 26.1.2 semantics: a missing-texture sprite is never accepted as owned.
        if (AthenaNativeOwnershipPolicy.missingSprite(
                sprite)) {
            return NativeQueryObservation.noMatch();
        }
        // 中文：predicate 前置：null 视为接受，否则按 (state, state) 自连接判定。
        // English: Predicate pre-check: null accepts, otherwise the (state, state)
        // self-connection decides.
        if (probe.predicate() != null
                && !probe.predicate().test(
                        state,
                        state)) {
            return NativeQueryObservation.noMatch();
        }
        List<TextureAtlasSprite> candidates = nativeSprites(
                probe.model(),
                probe.textures(),
                level,
                pos,
                state,
                quad.getDirection());
        // 中文：owns 且身份可解析→精确文档，否则明确 unknown；裁决在 common，绝不
        // 伪造 exact(empty)。
        // English: Owned with a resolvable identity yields an exact document, otherwise an
        // explicit unknown; adjudication stays in common and exact(empty) is never forged.
        return AthenaNativeOwnershipPolicy.resolveObservation(
                AthenaNativeOwnershipPolicy
                        .ownsByCandidateSprites(
                                candidates,
                                sprite),
                probe.identity());
    }

    /**
     * 中文：原生发射候选纹理：model.getQuads 的每个 quad 槽位经 textures 表映射为
     * 实际精灵，null 槽位丢弃；与 26.1.2 materials 映射同构，使用 Fabric WrappedGetter。
     *
     * <p>English: Native-emission candidate textures: each emitted quad's sprite slot is
     * resolved through the textures table, dropping null slots; isomorphic to the 26.1.2
     * materials mapping and uses the Fabric WrappedGetter.
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

    /** 中文：文档身份解析的块 ID 来源：BuiltInRegistries.BLOCK.getKey。 / English: Block-id source for document-identity resolution: BuiltInRegistries.BLOCK.getKey. */
    static ResourceLocation blockId(
            BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(
                state.getBlock());
    }

    /** 中文：4.0.6 Fabric loader 键空间：FactoryManagerImpl.LOADERS.keySet() 的稳定快照。 / English: Athena 4.0.6 Fabric loader key space: a stable snapshot of FactoryManagerImpl.LOADERS.keySet(). */
    static List<ResourceLocation> loaderIds() {
        return List.copyOf(
                FactoryManagerImpl.LOADERS.keySet());
    }

    /** 中文：blockId + loaderIds 经 common AthenaAcceptedDocumentIdentity 解析精确文档身份。 / English: Resolves the exact document identity through the common AthenaAcceptedDocumentIdentity from blockId plus loaderIds. */
    static Optional<NativeDocumentIdentity> resolveIdentity(
            BlockState state) {
        return AthenaAcceptedDocumentIdentity.resolve(
                blockId(state),
                loaderIds());
    }

    record QueryProbe(
            BiPredicate<BlockState, BlockState> predicate,
            AthenaBlockModel model,
            Int2ObjectMap<TextureAtlasSprite> textures,
            Optional<NativeDocumentIdentity> identity) {
        QueryProbe {
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(textures, "textures");
            Objects.requireNonNull(identity, "identity");
        }
    }
}
