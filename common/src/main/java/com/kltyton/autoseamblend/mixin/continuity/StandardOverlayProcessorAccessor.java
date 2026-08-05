package com.kltyton.autoseamblend.mixin.continuity;

import java.util.Set;
import java.util.function.Predicate;
import me.pepperbell.continuity.client.processor.ConnectionPredicate;
import me.pepperbell.continuity.client.processor.overlay.StandardOverlayQuadProcessor;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 中文：读取标记槽位重放所需的 Continuity overlay 字段。 / English: Reads Continuity overlay fields needed to replay marker-slot selection. */
@Mixin(value = StandardOverlayQuadProcessor.class, remap = false)
public interface StandardOverlayProcessorAccessor {
    @Accessor(value = "matchTilesSet", remap = false)
    Set<Identifier> autoseamblend$matchTilesSet();

    @Accessor(value = "matchBlocksPredicate", remap = false)
    Predicate<BlockState> autoseamblend$matchBlocksPredicate();

    @Accessor(value = "connectTilesSet", remap = false)
    Set<Identifier> autoseamblend$connectTilesSet();

    @Accessor(value = "connectBlocksPredicate", remap = false)
    Predicate<BlockState> autoseamblend$connectBlocksPredicate();

    @Accessor(value = "connectionPredicate", remap = false)
    ConnectionPredicate autoseamblend$connectionPredicate();

    @Accessor(value = "tintIndex", remap = false)
    int autoseamblend$tintIndex();

    @Accessor(value = "tintBlock", remap = false)
    BlockState autoseamblend$tintBlock();

    @Accessor(value = "layer", remap = false)
    ChunkSectionLayer autoseamblend$layer();
}
