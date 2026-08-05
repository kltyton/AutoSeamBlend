package com.kltyton.autoseamblend.fabric.compat.athena.runtime;

import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeProvider;
import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeStateSampler;
import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;
import earth.terrarium.athena.api.client.fabric.WrappedGetter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：使用 Athena 4.7.3 的 CtmState 与 47 切片 provider 选择预生成物理槽。
 * English: Selects a pre-generated physical slot through Athena 4.7.3's
 * CtmState and 47-slice provider.
 */
final class FabricAthenaNativeQuadProcessor {
    private static final float UV_EPSILON = 1.0e-6F;

    private FabricAthenaNativeQuadProcessor() {}

    static boolean emitReplacement(
            MutableQuadView source,
            TextureAtlasSprite sourceSprite,
            Map<String, TextureAtlasSprite> physicalSprites,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            ConnectionRuleSet<Block> rules,
            BiPredicate<BlockState, BlockState> nativePredicate,
            QuadEmitter output) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sourceSprite, "sourceSprite");
        Objects.requireNonNull(physicalSprites, "physicalSprites");
        Objects.requireNonNull(output, "output");
        int slot = AthenaNativeProvider.select47(
                AthenaNativeStateSampler.sample(
                        new WrappedGetter(level),
                        state,
                        pos,
                        source.lightFace(),
                        rules,
                        java.util.Set.of()));
        if (slot < 0) {
            return false;
        }
        TextureAtlasSprite target =
                physicalSprites.get(
                        Integer.toString(slot));
        if (target == null) {
            return false;
        }
        output.copyFrom(source);
        remapLocalUv(output, sourceSprite, target);
        output.animated(target.contents().isAnimated());
        output.emit();
        return true;
    }

    static boolean emitDirectReplacement(
            MutableQuadView source,
            TextureAtlasSprite sourceSprite,
            TextureAtlasSprite target,
            QuadEmitter output) {
        if (target == null) {
            return false;
        }
        output.copyFrom(source);
        remapLocalUv(output, sourceSprite, target);
        output.animated(target.contents().isAnimated());
        output.emit();
        return true;
    }

    private static void remapLocalUv(
            MutableQuadView quad,
            TextureAtlasSprite source,
            TextureAtlasSprite target) {
        float width = source.getU1() - source.getU0();
        float height = source.getV1() - source.getV0();
        if (Math.abs(width) <= UV_EPSILON
                || Math.abs(height) <= UV_EPSILON) {
            return;
        }
        for (int vertex = 0;
                vertex < 4;
                vertex++) {
            float u = (quad.u(vertex) - source.getU0())
                    / width;
            float v = (quad.v(vertex) - source.getV0())
                    / height;
            quad.uv(
                    vertex,
                    target.getU(u),
                    target.getV(v));
        }
    }
}
