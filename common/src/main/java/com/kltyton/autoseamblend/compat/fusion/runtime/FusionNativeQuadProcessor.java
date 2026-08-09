package com.kltyton.autoseamblend.compat.fusion.runtime;

import com.kltyton.autoseamblend.compat.fusion.texture.generation.FusionNativeSheetPlan;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.SpriteTransparency;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import com.supermartijn642.fusion.api.model.custom.quad.EmittableQuad;
import com.supermartijn642.fusion.api.model.custom.quad.MutableQuad;
import com.supermartijn642.fusion.api.texture.custom.QuadProcessor;
import com.supermartijn642.fusion.api.texture.custom.SpriteInstance;
import com.supermartijn642.fusion.api.texture.custom.TextureInstance;
import com.supermartijn642.fusion.api.texture.types.base.BaseTextureData;
import com.supermartijn642.fusion.api.texture.types.connecting.ConnectingTextureData;
import com.supermartijn642.fusion.api.texture.types.connecting.predicates.ConnectionPredicate;
import com.supermartijn642.fusion.api.texture.types.connecting.predicates.DefaultConnectionPredicates;
import com.supermartijn642.fusion.api.util.Pair;
import com.supermartijn642.fusion.api.util.PropertyStore;
import com.supermartijn642.fusion.texture.custom.SpriteInstanceImpl;
import com.supermartijn642.fusion.texture.custom.TextureInstanceImpl;
import com.supermartijn642.fusion.texture.types.connecting.StitchedConnectingTextureData;
import com.supermartijn642.fusion.texture.types.connecting.TextureConnections;
import com.supermartijn642.fusion.texture.types.connecting.layouts.ConnectingTextureLayoutHandler;
import com.supermartijn642.fusion.util.FallbackPropertyStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * 中文：把方向、邻接、状态选择和 Quad 输出委托给 Fusion 连接纹理处理器的精确版本桥接。
 * <p>
 * English:
 * Exact-version bridge that delegates orientation, adjacency, state selection, and quad emission
 * to Fusion's connecting texture processor.
 */
public final class FusionNativeQuadProcessor {
    private static final float UV_EPSILON = 1.0e-6F;
    private static final float OVERLAY_OFFSET = 1.0F / 2048.0F;

    private final MutableQuad initializedQuad;
    private final SpriteInstance sourceSprite;
    private final QuadProcessor<Object> processor;
    private final PropertyStore initializedProperties;
    private final boolean overlay;

    private FusionNativeQuadProcessor(
            MutableQuad initializedQuad,
            SpriteInstance sourceSprite,
            QuadProcessor<Object> processor,
            PropertyStore initializedProperties,
            boolean overlay) {
        this.initializedQuad =
                Objects.requireNonNull(initializedQuad, "initializedQuad");
        this.sourceSprite =
                Objects.requireNonNull(sourceSprite, "sourceSprite");
        this.processor =
                Objects.requireNonNull(processor, "processor");
        this.initializedProperties =
                Objects.requireNonNull(
                        initializedProperties,
                        "initializedProperties");
        this.overlay = overlay;
    }

    public static Optional<FusionNativeQuadProcessor> create(
            BakedQuad sourceQuad,
            TextureAtlasSprite sourceSprite,
            TextureAtlasSprite[] stateSprites,
            Block originBlock,
            ConnectionRuleSet<Block> rules,
            ConnectionMethod method,
            Optional<Integer> overlayTint) {
        return create(
                sourceQuad,
                sourceSprite,
                stateSprites,
                originBlock,
                rules,
                method,
                overlayTint,
                Set.of());
    }

    public static Optional<FusionNativeQuadProcessor> create(
            BakedQuad sourceQuad,
            TextureAtlasSprite sourceSprite,
            TextureAtlasSprite[] stateSprites,
            Block originBlock,
            ConnectionRuleSet<Block> rules,
            ConnectionMethod method,
            Optional<Integer> overlayTint,
            Set<Block> documentConnectionBlocks) {
        Objects.requireNonNull(sourceQuad, "sourceQuad");
        Objects.requireNonNull(sourceSprite, "sourceSprite");
        stateSprites =
                Objects.requireNonNull(
                                stateSprites,
                                "stateSprites")
                        .clone();
        Objects.requireNonNull(originBlock, "originBlock");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(overlayTint, "overlayTint");
        documentConnectionBlocks = Set.copyOf(
                Objects.requireNonNull(
                        documentConnectionBlocks,
                        "documentConnectionBlocks"));

        ConnectingTextureData.Layout layout =
                FusionNativeSheetPlan.nativeLayout(method);
        ConnectingTextureLayoutHandler layoutHandler =
                ConnectingTextureLayoutHandler.get(layout);
        int expectedTiles = Math.multiplyExact(
                layoutHandler.getWidth(),
                layoutHandler.getHeight());
        if (stateSprites.length != expectedTiles) {
            return Optional.empty();
        }

        boolean overlay = overlayTint.isPresent();
        // 中文：Fusion 的 overlay 输出使用带透明遮罩的生成精灵；即使供体原图不透明，也必须走 CUTOUT，不能把透明遮罩送入 SOLID。
        // English: Fusion overlay outputs use generated sprites with transparent masks; even an opaque donor must use CUTOUT instead of sending that mask through SOLID.
        SpriteTransparency outputTransparency = overlay
                ? SpriteTransparency.TRANSPARENT
                : SpriteTransparency.of(sourceSprite);
        BaseTextureData.RenderType renderType =
                renderType(outputTransparency);
        BaseTextureData tileData = BaseTextureData.builder()
                .renderType(renderType)
                .build();
        ArrayList<TextureInstance<?>> tiles =
                new ArrayList<>(stateSprites.length);
        for (TextureAtlasSprite stateSprite : stateSprites) {
            if (stateSprite == null) {
                return Optional.empty();
            }
            tiles.add(baseTexture(stateSprite, tileData));
        }

        ConnectionPredicate connectionPredicate = predicate(
                originBlock,
                rules,
                documentConnectionBlocks);
        ConnectingTextureData data = ConnectingTextureData.builder()
                .layout(layout)
                .renderType(renderType)
                .connectionPredicate(connectionPredicate)
                .build();
        StitchedConnectingTextureData stitched =
                new StitchedConnectingTextureData(
                        data,
                        List.copyOf(tiles));
        TextureInstanceImpl<StitchedConnectingTextureData>
                sourceTexture = new TextureInstanceImpl<>(
                FusionNativeTextureTypes.connectingType(),
                sourceSprite.contents().name(),
                stitched);
        SpriteInstanceImpl sourceInstance =
                new SpriteInstanceImpl(
                        sourceTexture,
                        sourceSprite,
                        sourceSprite.contents().name());
        sourceTexture.setSprites(
                List.of(sourceInstance),
                sourceInstance);

        MutableQuad initialized =
                MutableQuad.create(sourceQuad);
        remapSprite(
                initialized,
                sourceQuad.getSprite(),
                sourceSprite);
        if (overlay) {
            initialized.tintIndex(-1);
            FusionMutableQuadHooks.color(
                    initialized,
                    overlayTint.orElseThrow());
            initialized.renderTypes(
                    RenderType.cutout(),
                    RenderType.cutout());
        }
        PropertyStore properties =
                PropertyStore.create();
        QuadProcessor<?> nativeProcessor =
                sourceTexture.initializeModelQuad(
                        initialized,
                        sourceInstance,
                        properties);
        if (nativeProcessor == null) {
            return Optional.empty();
        }
        return Optional.of(
                new FusionNativeQuadProcessor(
                        initialized.createCopy(),
                        sourceInstance,
                        FusionPreparedTextureSupport.castProcessor(nativeProcessor),
                        properties,
                        overlay));
    }

    public List<BakedQuad> process(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            long randomSeed) {
        Supplier<RandomSource> random =
                () -> RandomSource.create(randomSeed);
        PropertyStore properties =
                FallbackPropertyStore.create(
                        initializedProperties);
        Object nativeState = processor.extractState(
                level, pos, state, random, properties);
        ArrayList<BakedQuad> output =
                new ArrayList<>(4);
        EmittableQuad emitter =
                EmittableQuad.create(quad -> {
                    if (overlay) {
                        offsetOverlay(quad);
                    }
                    output.add(quad.toBakedQuad());
                });
        emitter.copyFrom(initializedQuad);
        processor.processQuad(
                emitter,
                sourceSprite,
                nativeState,
                properties);
        return List.copyOf(output);
    }

    public Optional<NeighborConnections> connections(
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            long randomSeed) {
        PropertyStore properties =
                FallbackPropertyStore.create(
                        initializedProperties);
        Object nativeState = processor.extractState(
                level,
                pos,
                state,
                () -> RandomSource.create(randomSeed),
                properties);
        Optional<TextureConnections> connections =
                connectionState(nativeState);
        if (connections.isEmpty()) {
            return Optional.empty();
        }
        TextureConnections value = connections.orElseThrow();
        int bits = 0;
        if (value.left) bits |= 1;
        if (value.bottomLeft) bits |= 1 << 1;
        if (value.bottom) bits |= 1 << 2;
        if (value.bottomRight) bits |= 1 << 3;
        if (value.right) bits |= 1 << 4;
        if (value.topRight) bits |= 1 << 5;
        if (value.top) bits |= 1 << 6;
        if (value.topLeft) bits |= 1 << 7;
        return Optional.of(
                NeighborConnections.fromBits(bits));
    }

    private static Optional<TextureConnections> connectionState(
            Object nativeState) {
        if (!(nativeState instanceof Pair<?, ?> pair)
                || !(pair.left()
                instanceof TextureConnections
                connections)) {
            return Optional.empty();
        }
        return Optional.of(connections);
    }

    public static BakedQuad retexture(
            BakedQuad source,
            TextureAtlasSprite target) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        MutableQuad output =
                MutableQuad.create(source);
        remapSprite(
                output,
                source.getSprite(),
                target);
        return output.toBakedQuad();
    }

    private static TextureInstance<BaseTextureData> baseTexture(
            TextureAtlasSprite sprite,
            BaseTextureData data) {
        ResourceLocation identifier =
                sprite.contents().name();
        TextureInstanceImpl<BaseTextureData> texture =
                new TextureInstanceImpl<>(
                        FusionNativeTextureTypes.baseType(),
                        identifier,
                        data);
        SpriteInstanceImpl instance =
                new SpriteInstanceImpl(
                        texture,
                        sprite,
                        identifier);
        texture.setSprites(
                List.of(instance),
                instance);
        return texture;
    }

    private static BaseTextureData.RenderType renderType(
            SpriteTransparency transparency) {
        if (transparency.hasTranslucent()) {
            return BaseTextureData.RenderType.TRANSLUCENT;
        }
        if (transparency.hasTransparent()) {
            return BaseTextureData.RenderType.CUTOUT;
        }
        return BaseTextureData.RenderType.OPAQUE;
    }

    private static ConnectionPredicate predicate(
            Block originBlock,
            ConnectionRuleSet<Block> rules,
            Set<Block> documentConnectionBlocks) {
        if (!documentConnectionBlocks.isEmpty()) {
            return DefaultConnectionPredicates.matchBlock(
                    documentConnectionBlocks.toArray(Block[]::new));
        }
        if (!rules.isTarget(originBlock)) {
            return DefaultConnectionPredicates.isSameBlock();
        }
        List<Block> group = rules.selector(originBlock)
                .map(selector ->
                        selector.targets()
                                .stream()
                                .toList())
                .orElseGet(() ->
                        List.of(originBlock));
        return group.size() == 1
                ? DefaultConnectionPredicates.isSameBlock()
                : DefaultConnectionPredicates.matchBlock(
                group.toArray(Block[]::new));
    }

    private static void remapSprite(
            MutableQuad quad,
            TextureAtlasSprite source,
            TextureAtlasSprite target) {
        float sourceWidth =
                source.getU1() - source.getU0();
        float sourceHeight =
                source.getV1() - source.getV0();
        if (Math.abs(sourceWidth) <= UV_EPSILON
                || Math.abs(sourceHeight)
                <= UV_EPSILON) {
            quad.sprite(target);
            return;
        }
        for (int vertex = 0;
             vertex < 4;
             vertex++) {
            float u =
                    (quad.u(vertex) - source.getU0())
                            / sourceWidth;
            float v =
                    (quad.v(vertex) - source.getV0())
                            / sourceHeight;
            quad.uv(
                    vertex,
                    target.getU(u),
                    target.getV(v));
        }
        quad.sprite(target);
    }

    private static void offsetOverlay(
            MutableQuad quad) {
        Vector3fc normal =
                new Vector3f(quad.facing().step());
        for (int vertex = 0;
             vertex < 4;
             vertex++) {
            quad.position(
                    vertex,
                    new Vector3f(
                            quad.position(vertex))
                            .fma(
                                    OVERLAY_OFFSET,
                                    normal));
        }
    }

}
