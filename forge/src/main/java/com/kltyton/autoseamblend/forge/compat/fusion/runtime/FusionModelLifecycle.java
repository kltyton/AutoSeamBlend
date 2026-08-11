package com.kltyton.autoseamblend.forge.compat.fusion.runtime;

import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** 中文：共享表面快照发布后装饰已完成的烘焙模型映射。 / English: Decorates the completed baked-model map after the shared surface snapshot was published. */
public final class FusionModelLifecycle {
    private FusionModelLifecycle() {}

    public static void onFusionModifiersApplied(ModelBakery bakery) {
        Map<ResourceLocation, BakedModel> authoritative =
                bakery.getBakedTopLevelModels();
        Map<BlockState, BakedModel> models =
                blockStateModels(
                        authoritative);
        MinecraftSurfaceCatalog.Snapshot surfaces =
                ReloadPublication.modelDecorationSurfaces();
        Map<BlockState, BakedModel> decorated = new HashMap<>();
        models.forEach((state, model) -> {
            if (surfaces.states().containsKey(state)
                    && !(model instanceof FusionConnectedBlockStateModel)) {
                decorated.put(
                        state,
                        new FusionConnectedBlockStateModel(
                                model,
                                state));
            }
        });
        // 中文：1.20.1 的 state 模型映射是从权威烘焙 map 恢复的视图；只把实际 Fusion
        // 候选按 stateToModelLocation 写回，避免重写所有无关模型。本方法不再是事件监听，
        // 而是由 modifier 生命周期钩子在 Fusion 应用完 modifier 后直接调用，语义对应
        // Fabric 已验收的 WRAP_LAST_PHASE。
        // English: The 1.20.1 state-model map is a recovered view of the authoritative baked
        // map. Write back only actual Fusion candidates by stateToModelLocation instead of
        // rewriting unrelated models. This method is no longer an event listener; the modifier
        // lifecycle hook invokes it after Fusion has applied modifiers, matching the accepted
        // Fabric WRAP_LAST_PHASE semantics.
        writeBackDecorated(
                decorated,
                authoritative,
                BlockModelShaper::stateToModelLocation);
    }

    /**
     * 中文：把按方块状态装饰的副本映射回权威模型键；返回写回条数仅供诊断。
     *
     * <p>English: Maps the state-keyed decorated copy back to authoritative model keys;
     * the returned write count is diagnostic only.
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
