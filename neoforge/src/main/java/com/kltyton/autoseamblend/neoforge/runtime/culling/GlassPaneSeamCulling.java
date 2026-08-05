package com.kltyton.autoseamblend.neoforge.runtime.culling;

import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.PreparedSurfaceMethods;
import com.kltyton.autoseamblend.runtime.culling.PaneSeamCullingPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;

/**
 * 中文：让原版玻璃板端盖 Quad 使用与 Continuity 玻璃板剔除资源包相同的剔除桶。
 *
 * English:
 * Gives vanilla pane cap quads the same cull buckets as Continuity's pane-culling resource pack.
 */
public final class GlassPaneSeamCulling {
    private GlassPaneSeamCulling() {}

    public static int install(
            Map<BlockState, BlockStateModel> models,
            RuleRuntime.Snapshot ruleSnapshot,
            PreparedSurfaceMethods.Snapshot preparedMethods,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        Objects.requireNonNull(models, "models");
        Objects.requireNonNull(ruleSnapshot, "ruleSnapshot");
        Objects.requireNonNull(preparedMethods, "preparedMethods");
        Objects.requireNonNull(surfaces, "surfaces");
        int[] installed = {0};
        models.replaceAll((state, model) -> {
            if (!(state.getBlock() instanceof IronBarsBlock)
                    || !PaneSeamCullingPolicy.applies(
                            state.getBlock(),
                            ruleSnapshot,
                            preparedMethods,
                            surfaces)
                    || model instanceof PaneCullingModel) {
                return model;
            }
            installed[0]++;
            return new PaneCullingModel(model);
        });
        return installed[0];
    }

    private static final class PaneCullingModel
            extends DelegateBlockStateModel {
        private PaneCullingModel(BlockStateModel delegate) {
            super(Objects.requireNonNull(delegate, "delegate"));
        }

        @Override
        public void collectParts(
                BlockAndTintGetter level,
                BlockPos pos,
                BlockState state,
                RandomSource random,
                List<BlockStateModelPart> output) {
            ArrayList<BlockStateModelPart> source = new ArrayList<>();
            super.collectParts(level, pos, state, random, source);
            for (BlockStateModelPart part : source) {
                output.add(new PaneCullingPart(part));
            }
        }
    }

    private static final class PaneCullingPart
            implements BlockStateModelPart {
        private final BlockStateModelPart delegate;
        private final List<BakedQuad> up;
        private final List<BakedQuad> down;
        private final List<BakedQuad> unculled;

        private PaneCullingPart(BlockStateModelPart delegate) {
            this.delegate =
                    Objects.requireNonNull(delegate, "delegate");
            ArrayList<BakedQuad> up =
                    new ArrayList<>(delegate.getQuads(Direction.UP));
            ArrayList<BakedQuad> down =
                    new ArrayList<>(delegate.getQuads(Direction.DOWN));
            ArrayList<BakedQuad> unculled = new ArrayList<>();
            for (BakedQuad quad : delegate.getQuads(null)) {
                if (quad.direction() == Direction.UP) {
                    up.add(quad);
                } else if (quad.direction() == Direction.DOWN) {
                    down.add(quad);
                } else {
                    unculled.add(quad);
                }
            }
            this.up = List.copyOf(up);
            this.down = List.copyOf(down);
            this.unculled = List.copyOf(unculled);
        }

        @Override
        public List<BakedQuad> getQuads(Direction direction) {
            if (direction == null) {
                return unculled;
            }
            return switch (direction) {
                case UP -> up;
                case DOWN -> down;
                default -> delegate.getQuads(direction);
            };
        }

        @Override
        public boolean useAmbientOcclusion() {
            return delegate.useAmbientOcclusion();
        }

        @Override
        public Material.Baked particleMaterial() {
            return delegate.particleMaterial();
        }

        @Override
        public int materialFlags() {
            return delegate.materialFlags();
        }
    }
}
