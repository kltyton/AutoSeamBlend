package com.kltyton.autoseamblend.forge.compat.athena.runtime;

import com.kltyton.autoseamblend.forge.compat.athena.runtime.pane.AthenaGeneratedPaneModelFactory;
import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog;
import com.kltyton.autoseamblend.runtime.publication.ReloadPublication;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ModelEvent;
import java.util.function.Function;

/** 中文：共享表面快照发布后装饰非原生烘焙模型。 / English: Decorates non-native baked models after the shared surface snapshot was published. */
public final class AthenaModelLifecycle {
    private AthenaModelLifecycle() {}

    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<BlockState, BakedModel> models =
                blockStateModels(
                        event.getModels());
        MinecraftSurfaceCatalog.Snapshot surfaces =
                ReloadPublication.modelDecorationSurfaces();
        ReloadPublication.Generation generation =
                ReloadPublication.pendingPreparation()
                        .filter(candidate ->
                                candidate.generation() == surfaces.generation())
                        .orElseGet(ReloadPublication::current);
        models.replaceAll((state, model) -> {
            if (!surfaces.states().containsKey(state)) {
                return model;
            }
            BakedModel paneModel = AthenaGeneratedPaneModelFactory
                    .create(
                            textureGetter(),
                            generation,
                            surfaces,
                            state,
                            model)
                    .orElse(null);
            if (paneModel != null) {
                return paneModel;
            }
            return new AthenaConnectedBlockStateModel(
                    model,
                    state);
        });
        // 中文：1.20.1 的 blockStateModels() 返回副本，必须把装饰后的模型按
        // stateToModelLocation 写回 event.getModels()，否则游戏实际渲染仍使用未包装
        // 模型；与 26.1.2 对权威表原地 replaceAll 语义等价（同 CTM 生命周期写回语义）。
        // English: blockStateModels() returns a copy in 1.20.1; write the decorated
        // models back into event.getModels() keyed by stateToModelLocation, otherwise
        // the game renders the unwrapped models. Semantically equivalent to 26.1.2's
        // in-place replaceAll on the authoritative map (same write-back semantics as
        // the CTM model lifecycle).
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

    /**
     * 中文：1.20.1 ModifyBakingResult 无 getTextureGetter。优先使用本次模型烘焙持有的
     * StitchResult；只有事件作用域之外才回退到已发布图集。
     *
     * <p>English: Forge 1.20.1 ModifyBakingResult has no texture getter. Prefer the current
     * model bake's StitchResult and use the published atlas only outside that event scope.
     */
    private static Function<Material, TextureAtlasSprite> textureGetter() {
        Function<Material, TextureAtlasSprite> publishedAtlas =
                material -> Minecraft.getInstance()
                        .getModelManager()
                        .getAtlas(material.atlasLocation())
                        .getSprite(material.texture());
        return ForgeModelBakeTextureContext.currentOr(
                publishedAtlas);
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
