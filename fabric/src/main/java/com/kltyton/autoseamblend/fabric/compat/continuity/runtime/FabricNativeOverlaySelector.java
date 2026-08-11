package com.kltyton.autoseamblend.fabric.compat.continuity.runtime;

import com.kltyton.autoseamblend.mixin.continuity.OverlayEmitterAccessor;
import com.kltyton.autoseamblend.mixin.continuity.StandardOverlayProcessorAccessor;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import me.pepperbell.continuity.api.client.QuadProcessor;
import me.pepperbell.continuity.client.processor.ConnectionPredicate;
import me.pepperbell.continuity.client.processor.DirectionMaps;
import me.pepperbell.continuity.client.processor.overlay.StandardOverlayQuadProcessor;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：把全部 17 状态边与角选择委托给 Continuity；不同的非渲染标记精灵在不复制原生表
 * 的前提下揭示原生槽位 ID。
 *
 * English: Delegates all 17-state edge/corner selection to Continuity. Distinct
 * non-rendered marker sprites reveal the native slot ids without copying its table.
 */
final class FabricNativeOverlaySelector
        extends StandardOverlayQuadProcessor {
    private final IdentityHashMap<
                    TextureAtlasSprite,
                    Integer>
            markerSlots =
                    new IdentityHashMap<>();

    FabricNativeOverlaySelector(
            TextureAtlasSprite donorSprite,
            Predicate<BlockState> receiverPredicate,
            Predicate<BlockState> donorPredicate,
            ConnectionPredicate connectionPredicate,
            int tintIndex,
            BlockState tintState) {
        this(
                markers(donorSprite),
                receiverPredicate,
                donorPredicate,
                connectionPredicate,
                tintIndex,
                tintState);
    }

    static FabricNativeOverlaySelector copyOf(
            StandardOverlayQuadProcessor nativeProcessor,
            TextureAtlasSprite markerSource) {
        StandardOverlayProcessorAccessor fields =
                (StandardOverlayProcessorAccessor)
                        nativeProcessor;
        return new FabricNativeOverlaySelector(
                markers(markerSource),
                fields.autoseamblend$matchTilesSet(),
                fields.autoseamblend$matchBlocksPredicate(),
                fields.autoseamblend$connectTilesSet(),
                fields.autoseamblend$connectBlocksPredicate(),
                fields.autoseamblend$connectionPredicate(),
                fields.autoseamblend$tintIndex(),
                fields.autoseamblend$tintBlock());
    }

    private FabricNativeOverlaySelector(
            TextureAtlasSprite[] markers,
            Predicate<BlockState> receiverPredicate,
            Predicate<BlockState> donorPredicate,
            ConnectionPredicate connectionPredicate,
            int tintIndex,
            BlockState tintState) {
        super(
                markers,
                (quad, sprite, level, pos,
                        appearanceState, state, data) -> true,
                null,
                receiverPredicate,
                null,
                donorPredicate,
                connectionPredicate,
                tintIndex,
                tintState,
                BlendMode.CUTOUT);
        indexMarkers(markers);
    }

    private FabricNativeOverlaySelector(
            TextureAtlasSprite[] markers,
            Set<ResourceLocation> matchTiles,
            Predicate<BlockState> matchBlocks,
            Set<ResourceLocation> connectTiles,
            Predicate<BlockState> connectBlocks,
            ConnectionPredicate connectionPredicate,
            int tintIndex,
            BlockState tintState) {
        super(
                markers,
                (quad, sprite, level, pos,
                        appearanceState, state, data) -> true,
                matchTiles,
                matchBlocks,
                connectTiles,
                connectBlocks,
                connectionPredicate,
                tintIndex,
                tintState,
                BlendMode.CUTOUT);
        indexMarkers(markers);
    }

    private void indexMarkers(
            TextureAtlasSprite[] markers) {
        for (int slot = 0;
                slot < markers.length;
                slot++) {
            markerSlots.put(
                    markers[slot],
                    slot);
        }
    }

    List<Integer> select(
            MutableQuadView quad,
            TextureAtlasSprite receiverSprite,
            BlockAndTintGetter level,
            BlockState appearanceState,
            BlockState state,
            BlockPos pos,
            QuadProcessor.ProcessingContext context) {
        OverlayEmitter collector = getEmitter(
                level,
                appearanceState,
                state,
                pos,
                quad.lightFace(),
                receiverSprite,
                DirectionMaps.getMap(
                        quad.lightFace())[0],
                context);
        if (collector == null) {
            return List.of();
        }
        OverlayEmitterAccessor accessor =
                (OverlayEmitterAccessor) collector;
        TextureAtlasSprite[] selected =
                accessor.autoseamblend$sprites();
        int amount =
                accessor.autoseamblend$spriteAmount();
        if (selected == null
                || amount < 0
                || amount > selected.length) {
            return List.of();
        }
        ArrayList<Integer> slots =
                new ArrayList<>(amount);
        for (int index = 0;
                index < amount;
                index++) {
            Integer slot =
                    markerSlots.get(selected[index]);
            if (slot != null) {
                slots.add(slot);
            }
        }
        return List.copyOf(slots);
    }

    @Override
    public ProcessingResult processQuadInner(
            MutableQuadView quad,
            TextureAtlasSprite sprite,
            BlockAndTintGetter level,
            BlockState appearanceState,
            BlockState state,
            BlockPos pos,
            Supplier<RandomSource> random,
            int pass,
            ProcessingContext context) {
        return ProcessingResult.NEXT_PROCESSOR;
    }

    private static TextureAtlasSprite[] markers(
            TextureAtlasSprite donor) {
        TextureAtlasSprite[] markers =
                new TextureAtlasSprite[17];
        int markerWidth =
                donor.contents().width();
        int atlasWidth =
                Math.multiplyExact(
                        markerWidth,
                        markers.length);
        int atlasHeight =
                donor.contents().height();
        for (int slot = 0;
                slot < markers.length;
                slot++) {
            markers[slot] = new MarkerSprite(
                    donor,
                    atlasWidth,
                    atlasHeight,
                    Math.multiplyExact(
                            markerWidth,
                            slot));
        }
        return markers;
    }

    private static final class MarkerSprite
            extends TextureAtlasSprite {
        private MarkerSprite(
                TextureAtlasSprite donor,
                int atlasWidth,
                int atlasHeight,
                int x) {
            super(
                    donor.atlasLocation(),
                    donor.contents(),
                    atlasWidth,
                    atlasHeight,
                    x,
                    0);
        }
    }
}
