package com.kltyton.autoseamblend.forge.compat.ctm_mod.runtime;

import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.ModelEvent;

/** 中文：使用 CTM Lib 原生动态状态采样器装饰非原生烘焙模型。 / English: Decorates non-native baked models with CTM Lib's native dynamic state sampler. */
public final class CtmModModelLifecycle {
    private CtmModModelLifecycle() {}

    public static void onModifyBakingResult(
            ModelEvent.ModifyBakingResult event) {
        Map<BlockState, BakedModel> models =
                blockStateModels(
                        event.getModels());
        MinecraftSurfaceCatalog.Snapshot surfaces =
                ReloadPublication.modelDecorationSurfaces();
        models.replaceAll((state, model) -> {
            if (!surfaces.states().containsKey(state)) {
                return model;
            }
            return new CtmModConnectedBlockStateModel(
                    model,
                    state);
        });
        // 中文：1.20.1 的 blockStateModels() 返回副本，必须把装饰后的模型按
        // stateToModelLocation 写回 event.getModels()，否则游戏实际渲染仍使用未包装
        // 模型；与 26.1.2 对权威表原地 replaceAll 语义等价。
        // English: blockStateModels() returns a copy in 1.20.1; write the decorated
        // models back into event.getModels() keyed by stateToModelLocation, otherwise
        // the game renders the unwrapped models. Semantically equivalent to 26.1.2's
        // in-place replaceAll on the authoritative map.
        writeBackDecorated(
                models,
                event.getModels(),
                BlockModelShaper::stateToModelLocation);
    }

    /**
     * 中文：把按源键装饰后的副本值映射为目标键后写回权威 map；调用方（本类）
     * 负责提供语义正确的 keyMapper（BlockState -> ModelResourceLocation）。
     * 返回写回条数，仅用于诊断计数，不参与选择/渲染逻辑。
     *
     * <p>English: Writes each decorated copy value into the authoritative map under the
     * key produced by keyMapper; callers supply the semantically correct mapping
     * (BlockState -> ModelResourceLocation). Returns the number of entries written,
     * used only for diagnostic counting, never for selection/rendering.
     */
    static <S, V> int writeBackDecorated(
            Map<S, V> decorated,
            Map<ResourceLocation, V> authoritative,
            Function<S, ? extends ResourceLocation> keyMapper) {
        int written = 0;
        for (Map.Entry<S, V> entry : decorated.entrySet()) {
            authoritative.put(
                    keyMapper.apply(entry.getKey()),
                    entry.getValue());
            written++;
        }
        return written;
    }

    private static Map<BlockState, BakedModel> blockStateModels(
            Map<? extends ResourceLocation, BakedModel> baked) {
        Map<BlockState, BakedModel> models =
                new HashMap<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            for (BlockState state :
                    block.getStateDefinition()
                            .getPossibleStates()) {
                BakedModel model = baked.get(
                        BlockModelShaper
                                .stateToModelLocation(
                                        state));
                if (model != null) {
                    models.put(state, model);
                }
            }
        }
        return models;
    }
}
