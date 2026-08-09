package com.kltyton.autoseamblend.runtime.surface;

import com.kltyton.autoseamblend.selection.compiled.ConnectionRuleSet;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** 中文：共享的 top 方法查询；各适配器仍执行自身原生 Quad 变换。 / English: Shared top-method query; each adapter still performs its own native quad mutation. */
public final class MinecraftTopSurfaceResolver {
    private MinecraftTopSurfaceResolver() {}

    public static Optional<TextureAtlasSprite>
            resolve(
                    BlockAndTintGetter level,
                    BlockPos pos,
                    BlockState state,
                    Direction face,
                    ConnectionRuleSet<Block> rules,
                    MinecraftSurfaceCatalog.Snapshot
                            surfaces) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(face, "face");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(
                surfaces,
                "surfaces");
        return TopSurfaceConnectionPolicy.resolve(
                level,
                pos,
                state,
                face,
                rules)
                .flatMap(top -> surfaces.preferredFace(
                        state,
                        top)
                        .map(value -> value.sprite()));
    }
}
