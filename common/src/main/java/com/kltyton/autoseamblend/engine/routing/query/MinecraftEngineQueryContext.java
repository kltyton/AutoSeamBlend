package com.kltyton.autoseamblend.engine.routing.query;

import com.kltyton.autoseamblend.engine.query.ConnectionQuery;
import com.kltyton.autoseamblend.engine.query.EngineQueryContext;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import java.util.Objects;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：一个精确 Minecraft Quad 查询的不可变句柄；只携带本次查询已经解析的世界、位置、状态、面与精灵事实。
 *
 * English:
 * Immutable handle for one exact Minecraft quad query. It carries only the
 * world, position, state, face, sprite, and prepared surface facts already
 * resolved for this query.
 */
public record MinecraftEngineQueryContext(
        BlockAndTintGetter level,
        BlockPos pos,
        BlockState state,
        BakedQuad quad,
        TextureAtlasSprite sprite,
        FaceSurface surface,
        ReloadPublication.Generation reloadGeneration)
        implements EngineQueryContext {
    public MinecraftEngineQueryContext {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(quad, "quad");
        Objects.requireNonNull(sprite, "sprite");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(
                reloadGeneration,
                "reloadGeneration");
        if (quad.getDirection() != surface.direction()
                || surface.sprite() != sprite) {
            throw new IllegalArgumentException(
                    "quad, sprite, and prepared surface must identify one exact face");
        }
    }

    /** 中文：拒绝把另一查询的原生句柄复用于当前适配器观察。 / English: Rejects reusing a native handle for a different query. */
    public void requireMatches(ConnectionQuery query) {
        Objects.requireNonNull(query, "query");
        if (!query.blockId().equals(blockId())
                || !query.face().name().equals(quad.getDirection().name())
                || !query.spriteId().equals(sprite.contents().name().toString())) {
            throw new IllegalArgumentException(
                    "native query context does not match the engine-neutral query");
        }
    }

    public String blockId() {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(state.getBlock())
                .toString();
    }
}
