package com.kltyton.autoseamblend.fabric.reload.contribution;

import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：引擎兼容边界向根 reload 生命周期注册的贡献契约；prepare 只提交冻结 DTO，
 * apply 之后才能访问 Minecraft/引擎对象。
 *
 * English: Contribution contract between engine compat boundaries and the root
 * reload lifecycle. Prepare stages only frozen DTOs; Minecraft and engine
 * objects are accessible only after apply.
 */
public interface FabricReloadContribution {
    String engineId();

    List<Identifier> nativeReloadListenerIds();

    default boolean prepared(
            long tokenOrdinal,
            long targetGeneration,
            FabricPreparedContribution prepared) {
        return true;
    }

    default void discard(
            long tokenOrdinal,
            long targetGeneration) {}

    default void onPublished(
            long tokenOrdinal,
            long targetGeneration) {}

    default void onUnselectedAfterPublication(
            long tokenOrdinal,
            long targetGeneration) {}

    /**
     * 中文：根 apply 阶段在模型捕获前装饰最终烘焙模型表（例如玻璃板端盖剔除包装）。
     * English: Decorates the final baked model table before ownership capture
     * during root apply (for example glass-pane cap culling wrappers).
     */
    default void decorateModels(
            Map<BlockState, BlockStateModel> models) {}
}
