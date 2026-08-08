package com.kltyton.autoseamblend.fabric.runtime.culling;

import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.PreparedSurfaceMethods;
import com.kltyton.autoseamblend.runtime.culling.PaneSeamCullingPolicy;
import java.util.function.Predicate;
import java.util.List;
import java.util.Objects;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadTransform;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;

/**
 * 中文：让原版玻璃板端盖 Quad 使用与 Continuity 玻璃板剔除资源包相同的剔除桶。
 *
 * English:
 * Gives vanilla pane cap quads the same cull buckets as Continuity's
 * pane-culling resource pack.
 */
public final class FabricGlassPaneSeamCulling {
    private FabricGlassPaneSeamCulling() {}

    public static boolean applies(
            net.minecraft.world.level.block.Block block,
            RuleRuntime.Snapshot ruleSnapshot,
            PreparedSurfaceMethods.Snapshot preparedMethods,
            MinecraftSurfaceCatalog.Snapshot surfaces) {
        return PaneSeamCullingPolicy.applies(
                Objects.requireNonNull(block, "block"),
                Objects.requireNonNull(
                        ruleSnapshot,
                        "ruleSnapshot"),
                Objects.requireNonNull(
                        preparedMethods,
                        "preparedMethods"),
                Objects.requireNonNull(
                        surfaces,
                        "surfaces"));
    }

    public static BlockStateModel wrap(
            BlockStateModel model) {
        Objects.requireNonNull(model, "model");
        return model instanceof PaneCullingModel
                ? model
                : new PaneCullingModel(model);
    }

    private static final class PaneCullingModel
            extends WrapperBlockStateModel {
        private PaneCullingModel(BlockStateModel delegate) {
            super(Objects.requireNonNull(delegate, "delegate"));
        }

        @Override
        public void collectParts(
                RandomSource random,
                List<BlockStateModelPart> output) {
            ArrayList<BlockStateModelPart> source =
                    new ArrayList<>();
            super.collectParts(random, source);
            for (BlockStateModelPart part : source) {
                output.add(new PaneCullingPart(part));
            }
        }

        /**
         * 中文：Fabric 渲染器走 emitQuads 发射路径，WrapperBlockStateModel 的默认实现直接
         * 透传给内层模型并绕过 collectParts 重分桶；必须在发射层用 QuadTransform 把未剔除的
         * UP/DOWN 端盖 Quad 改写为对应 cullFace，与 1.21.1 已验证修复一致。
         *
         * English: The Fabric renderer emits through emitQuads, and WrapperBlockStateModel's
         * default implementation forwards straight to the wrapped model, bypassing the
         * collectParts re-bucketing; the unculled UP/DOWN cap quads must be rewritten to the
         * matching cullFace in a QuadTransform at emission time, matching the verified 1.21.1 fix.
         */
        @Override
        public void emitQuads(
                QuadEmitter emitter,
                BlockAndTintGetter level,
                BlockPos pos,
                BlockState state,
                RandomSource random,
                Predicate<Direction> cullTest) {
            emitter.pushTransform(
                    PaneCapCullTransform.INSTANCE);
            try {
                super.emitQuads(
                        emitter,
                        level,
                        pos,
                        state,
                        random,
                        cullTest);
            } finally {
                emitter.popTransform();
            }
        }
    }

    private static final class PaneCapCullTransform
            implements QuadTransform {
        private static final PaneCapCullTransform INSTANCE =
                new PaneCapCullTransform();

        @Override
        public boolean transform(
                MutableQuadView quad) {
            Direction face = quad.lightFace();
            if (quad.cullFace() == null
                    && (face == Direction.UP
                            || face == Direction.DOWN)) {
                quad.cullFace(face);
            }
            return true;
        }
    }

    private static final class PaneCullingPart
            implements BlockStateModelPart {
        private final BlockStateModelPart delegate;
        private final List<BakedQuad> up;
        private final List<BakedQuad> down;
        private final List<BakedQuad> unculled;

        private PaneCullingPart(
                BlockStateModelPart delegate) {
            this.delegate = Objects.requireNonNull(
                    delegate,
                    "delegate");
            ArrayList<BakedQuad> up = new ArrayList<>(
                    delegate.getQuads(Direction.UP));
            ArrayList<BakedQuad> down = new ArrayList<>(
                    delegate.getQuads(Direction.DOWN));
            ArrayList<BakedQuad> unculled =
                    new ArrayList<>();
            for (BakedQuad quad : delegate.getQuads(null)) {
                if (quad.direction() == Direction.UP) {
                    up.add(quad);
                } else if (quad.direction()
                        == Direction.DOWN) {
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
        public List<BakedQuad> getQuads(
                Direction direction) {
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
