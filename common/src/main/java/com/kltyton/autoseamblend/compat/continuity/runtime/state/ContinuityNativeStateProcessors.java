package com.kltyton.autoseamblend.compat.continuity.runtime.state;

import com.kltyton.autoseamblend.compat.continuity.runtime.ContinuityNativeStatePolicy;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import me.pepperbell.continuity.api.client.QuadProcessor;
import me.pepperbell.continuity.client.processor.CompactCtmQuadProcessor;
import me.pepperbell.continuity.client.processor.ConnectionPredicate;
import me.pepperbell.continuity.client.processor.OrientationMode;
import me.pepperbell.continuity.client.processor.ProcessingPredicate;
import me.pepperbell.continuity.client.processor.TopQuadProcessor;
import me.pepperbell.continuity.client.processor.overlay.SimpleOverlayQuadProcessor;
import me.pepperbell.continuity.client.processor.overlay.StandardOverlayQuadProcessor;
import me.pepperbell.continuity.client.processor.simple.CtmSpriteProvider;
import me.pepperbell.continuity.client.processor.simple.FixedSpriteProvider;
import me.pepperbell.continuity.client.processor.simple.HorizontalSpriteProvider;
import me.pepperbell.continuity.client.processor.simple.HorizontalVerticalSpriteProvider;
import me.pepperbell.continuity.client.processor.simple.SimpleQuadProcessor;
import me.pepperbell.continuity.client.processor.simple.SpriteProvider;
import me.pepperbell.continuity.client.processor.simple.VerticalHorizontalSpriteProvider;
import me.pepperbell.continuity.client.processor.simple.VerticalSpriteProvider;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：构造两 Loader 共用的 Continuity 原生状态处理器。
 *
 * English: Constructs the Continuity native state processors shared by both loaders.
 *
 * <p>This class owns only the ABI-stable Continuity construction and method dispatch. Processor
 * inspection, Mixin accessors, and mutable-quad completion remain in the Loader state adapters.</p>
 */
public final class ContinuityNativeStateProcessors {
    private ContinuityNativeStateProcessors() {}

    public static QuadProcessor replacement(
            ConnectionMethod method,
            TextureAtlasSprite[] sprites,
            ConnectionPredicate connectionPredicate,
            ProcessingPredicate processingPredicate) {
        Objects.requireNonNull(method, "method");
        sprites = Objects.requireNonNull(sprites, "sprites").clone();
        Objects.requireNonNull(connectionPredicate, "connectionPredicate");
        Objects.requireNonNull(processingPredicate, "processingPredicate");
        ContinuityNativeStatePolicy.ReplacementKind kind =
                ContinuityNativeStatePolicy.replacementKind(method)
                        .orElseThrow(() -> new IllegalArgumentException(
                                method + " is not a Continuity replacement method"));
        if (kind == ContinuityNativeStatePolicy.ReplacementKind.CTM_COMPACT) {
            return new CompactCtmQuadProcessor(
                    sprites,
                    processingPredicate,
                    connectionPredicate,
                    false,
                    OrientationMode.TEXTURE,
                    null);
        }
        SpriteProvider provider = switch (kind) {
            case CTM -> new CtmSpriteProvider(
                    sprites,
                    connectionPredicate,
                    false,
                    OrientationMode.TEXTURE);
            case HORIZONTAL -> new HorizontalSpriteProvider(
                    sprites,
                    connectionPredicate,
                    false,
                    OrientationMode.TEXTURE);
            case VERTICAL -> new VerticalSpriteProvider(
                    sprites,
                    connectionPredicate,
                    false,
                    OrientationMode.TEXTURE);
            case HORIZONTAL_VERTICAL -> new HorizontalVerticalSpriteProvider(
                    sprites,
                    connectionPredicate,
                    false,
                    OrientationMode.TEXTURE);
            case VERTICAL_HORIZONTAL -> new VerticalHorizontalSpriteProvider(
                    sprites,
                    connectionPredicate,
                    false,
                    OrientationMode.TEXTURE);
            case CTM_COMPACT -> throw new IllegalStateException(
                    "compact method handled before provider selection");
        };
        return new SimpleQuadProcessor(provider, processingPredicate);
    }

    public static QuadProcessor overlayCtm(
            TextureAtlasSprite[] sprites,
            ConnectionPredicate connectionPredicate,
            ProcessingPredicate processingPredicate,
            int tintIndex,
            BlockState tintState) {
        return new SimpleOverlayQuadProcessor(
                new CtmSpriteProvider(
                        Objects.requireNonNull(sprites, "sprites").clone(),
                        Objects.requireNonNull(connectionPredicate, "connectionPredicate"),
                        false,
                        OrientationMode.TEXTURE),
                Objects.requireNonNull(processingPredicate, "processingPredicate"),
                tintIndex,
                Objects.requireNonNull(tintState, "tintState"),
                ChunkSectionLayer.CUTOUT);
    }

    public static QuadProcessor top(
            TextureAtlasSprite topSprite,
            ConnectionPredicate connectionPredicate,
            ProcessingPredicate processingPredicate) {
        return new TopQuadProcessor(
                new TextureAtlasSprite[] {Objects.requireNonNull(topSprite, "topSprite")},
                Objects.requireNonNull(processingPredicate, "processingPredicate"),
                Objects.requireNonNull(connectionPredicate, "connectionPredicate"),
                false);
    }

    public static QuadProcessor fixed(
            TextureAtlasSprite sprite,
            ProcessingPredicate processingPredicate) {
        return new SimpleQuadProcessor(
                new FixedSpriteProvider(Objects.requireNonNull(sprite, "sprite")),
                Objects.requireNonNull(processingPredicate, "processingPredicate"));
    }

    public static QuadProcessor standardOverlay(
            TextureAtlasSprite[] sprites,
            ProcessingPredicate processingPredicate,
            Set<Identifier> matchTiles,
            Predicate<BlockState> matchBlocks,
            Set<Identifier> connectTiles,
            Predicate<BlockState> connectBlocks,
            ConnectionPredicate connectionPredicate,
            int tintIndex,
            BlockState tintState,
            ChunkSectionLayer layer) {
        return new StandardOverlayQuadProcessor(
                Objects.requireNonNull(sprites, "sprites").clone(),
                Objects.requireNonNull(processingPredicate, "processingPredicate"),
                matchTiles,
                matchBlocks,
                connectTiles,
                connectBlocks,
                Objects.requireNonNull(connectionPredicate, "connectionPredicate"),
                tintIndex,
                tintState,
                Objects.requireNonNull(layer, "layer"));
    }
}
