package com.kltyton.autoseamblend.neoforge.runtime.culling;

import com.kltyton.autoseamblend.runtime.culling.PaneSeamCullingPolicy;
import com.kltyton.autoseamblend.runtime.selection.RuleRuntime;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.surface.PreparedSurfaceMethods;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * 中文：让原版玻璃板端盖 Quad 使用与连接纹理剔除资源包相同的剔除桶（纯 NeoForge 路径）。
 *
 * English:
 * Gives vanilla pane cap quads the same cull buckets as connected-texture pane-culling
 * resource packs (pure NeoForge path).
 *
 * <p>中文：1.21.1 NeoForge 上 CTM/Fusion/Athena 走 vanilla BakedModel#getQuads 渲染路径，
 * 因此包装器只需在 3 参（vanilla）与 5 参（NeoForge ModelData/RenderType）getQuads 中做
 * UP/DOWN/null 重分桶，其余方向直接委托。Continuity 走 Fabric Renderer API 的
 * emitBlockQuads 路径，对应实现见
 * compat/continuity/runtime/culling/ContinuityGlassPaneSeamCulling，由
 * NeoForgeModelLifecycle 按引擎链接发现选择。
 *
 * <p>English: On 1.21.1 NeoForge, CTM/Fusion/Athena render through the vanilla
 * BakedModel#getQuads path, so this wrapper only re-buckets UP/DOWN/null in both the 3-arg
 * (vanilla) and 5-arg (NeoForge ModelData/RenderType) getQuads and delegates every other
 * direction. Continuity renders through the Fabric Renderer API emitBlockQuads path; that
 * implementation lives in compat/continuity/runtime/culling/ContinuityGlassPaneSeamCulling
 * and is selected by NeoForgeModelLifecycle via engine linkage discovery.
 */
public final class GlassPaneSeamCulling {
    private GlassPaneSeamCulling() {}

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
            extends BakedModelWrapper<BakedModel> {
        private PaneCullingModel(BakedModel delegate) {
            // 中文：BakedModelWrapper 构造器直接接收委托模型；包装器除委托外无其他状态，
            // 可跨区块线程共享。
            // English: The BakedModelWrapper constructor takes the delegate directly; the
            // wrapper holds no other state and is safe to share across chunk-builder threads.
            super(Objects.requireNonNull(delegate, "delegate"));
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
                                originalModel.getQuads(
                                        state,
                                        direction,
                                        random));
                for (BakedQuad quad
                        : originalModel.getQuads(
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
                        : originalModel.getQuads(
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
            return originalModel.getQuads(
                    state,
                    direction,
                    random);
        }

        /**
         * 中文：NeoForge 5 参 getQuads 与 vanilla 3 参版本保持同样的 UP/DOWN/null 重分桶，
         * 并把 ModelData/RenderType 原样传给委托模型。
         *
         * English:
         * The NeoForge 5-arg getQuads keeps the same UP/DOWN/null re-bucketing as the vanilla
         * 3-arg overload and passes ModelData/RenderType through to the delegate unchanged.
         */
        @Override
        public List<BakedQuad> getQuads(
                BlockState state,
                Direction direction,
                RandomSource random,
                ModelData modelData,
                RenderType renderType) {
            if (direction == Direction.UP
                    || direction == Direction.DOWN) {
                ArrayList<BakedQuad> result =
                        new ArrayList<>(
                                originalModel.getQuads(
                                        state,
                                        direction,
                                        random,
                                        modelData,
                                        renderType));
                for (BakedQuad quad
                        : originalModel.getQuads(
                                state,
                                null,
                                random,
                                modelData,
                                renderType)) {
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
                        : originalModel.getQuads(
                                state,
                                null,
                                random,
                                modelData,
                                renderType)) {
                    if (quad.getDirection()
                                    != Direction.UP
                            && quad.getDirection()
                                    != Direction.DOWN) {
                        result.add(quad);
                    }
                }
                return List.copyOf(result);
            }
            return originalModel.getQuads(
                    state,
                    direction,
                    random,
                    modelData,
                    renderType);
        }
    }
}
