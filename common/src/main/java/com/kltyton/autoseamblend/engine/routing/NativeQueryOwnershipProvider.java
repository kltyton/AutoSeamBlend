package com.kltyton.autoseamblend.engine.routing;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.query.NativeQueryObservation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：用于一个精确渲染表面的引擎隔离原生所有权探针；实现只在发现对应引擎后注册，因此可选引擎类不会泄漏到中立边界。
 *
 * English:
 * Engine-isolated native ownership probe for one exact rendered surface.
 *
 * <p>Implementations are registered only after their engine is discovered, so optional engine
 * classes never leak through this neutral boundary.
 */
public interface NativeQueryOwnershipProvider {
    String engineId();

    EngineFamily family();

    NativeQueryObservation observe(
            long generation,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            BakedQuad quad,
            TextureAtlasSprite sprite);
}
