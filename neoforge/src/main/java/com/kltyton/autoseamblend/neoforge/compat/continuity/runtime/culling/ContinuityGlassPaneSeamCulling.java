package com.kltyton.autoseamblend.neoforge.compat.continuity.runtime.culling;

import com.kltyton.autoseamblend.runtime.culling.PaneSeamCullingPolicy;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.PreparedSurfaceMethods;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：让原版玻璃板端盖 Quad 使用与 Continuity 玻璃板剔除资源包相同的剔除桶。
 *
 * English:
 * Gives vanilla pane cap quads the same cull buckets as Continuity's pane-culling resource pack.
 *
 * <p>中文：1.21.1 NeoContinuity 的 CtmBakedModel 走 Fabric Renderer API 的
 * emitBlockQuads 路径（isVanillaAdapter=false），因此除 getQuads 重分桶外，还必须在
 * QuadTransform 中把未剔除的 UP/DOWN 端盖 Quad 改写为对应方向的 cullFace。
 *
 * <p>English: On 1.21.1 NeoContinuity's CtmBakedModel renders through the Fabric Renderer
 * API emitBlockQuads path (isVanillaAdapter=false), so besides re-bucketing getQuads, the
 * wrapper must also rewrite unculled UP/DOWN cap quads to the matching cull face in a
 * QuadTransform.
 */
public final class ContinuityGlassPaneSeamCulling {
    private ContinuityGlassPaneSeamCulling() {}

    public static int install(
            Map<BlockState, BakedModel> models,
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
            extends ForwardingBakedModel {
        private PaneCullingModel(BakedModel delegate) {
            // 中文：NeoContinuity Jar-in-Jar 的 Forgified renderer API 3.4.0 只有无参
            // 构造器，编译面的 3.4.1 才有 (BakedModel) 构造器；统一用无参构造并直接
            // 赋值受保护的 wrapped 字段，避免 NoSuchMethodError。
            // English: NeoContinuity's Jar-in-Jar Forgified renderer API 3.4.0 only has
            // the no-arg constructor while the compile surface 3.4.1 adds the (BakedModel)
            // constructor; use the no-arg constructor and assign the protected wrapped
            // field directly to avoid NoSuchMethodError.
            super();
            this.wrapped = Objects.requireNonNull(
                    delegate,
                    "delegate");
        }

        @Override
        public List<BakedQuad> getQuads(
                BlockState state,
                Direction direction,
                RandomSource random) {
            if (direction == Direction.UP
                    || direction == Direction.DOWN) {
                ArrayList<BakedQuad> result =
                        new ArrayList<>(
                                wrapped.getQuads(
                                        state,
                                        direction,
                                        random));
                for (BakedQuad quad
                        : wrapped.getQuads(
                                state,
                                null,
                                random)) {
                    if (quad.getDirection()
                            == direction) {
                        result.add(quad);
                    }
                }
                return List.copyOf(result);
            }
            if (direction == null) {
                ArrayList<BakedQuad> result =
                        new ArrayList<>();
                for (BakedQuad quad
                        : wrapped.getQuads(
                                state,
                                null,
                                random)) {
                    if (quad.getDirection()
                                    != Direction.UP
                            && quad.getDirection()
                                    != Direction.DOWN) {
                        result.add(quad);
                    }
                }
                return List.copyOf(result);
            }
            return wrapped.getQuads(
                    state,
                    direction,
                    random);
        }

        /**
         * 中文：保留 Fabric Renderer API 发射路径，并在 Continuity 处理前后把端盖
         * Quad 的 cullFace 从 null 改写为 UP/DOWN；变换无状态，可跨区块线程共享。
         *
         * English:
         * Keeps the Fabric Renderer API emission path and rewrites the cap quads'
         * cullFace from null to UP/DOWN around Continuity processing; the transform is
         * stateless and safe to share across chunk-builder threads.
         */
        @Override
        public void emitBlockQuads(
                BlockAndTintGetter level,
                BlockState state,
                BlockPos pos,
                Supplier<RandomSource> randomSupplier,
                RenderContext context) {
            context.pushTransform(
                    PaneCapCullTransform.INSTANCE);
            try {
                super.emitBlockQuads(
                        level,
                        state,
                        pos,
                        randomSupplier,
                        context);
            } finally {
                context.popTransform();
            }
        }
    }

    private static final class PaneCapCullTransform
            implements RenderContext.QuadTransform {
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
}
