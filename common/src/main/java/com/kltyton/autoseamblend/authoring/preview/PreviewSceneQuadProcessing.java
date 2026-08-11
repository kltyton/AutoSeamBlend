package com.kltyton.autoseamblend.authoring.preview;

import com.kltyton.autoseamblend.engine.routing.EngineQueryRouter;
import com.kltyton.autoseamblend.engine.routing.query.EngineQuerySelection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：预览场景 Quad 的 Loader 中立处理入口：按当前引擎选择把源 Quad 交给已注册的
 * 引擎处理器链（真实原生 processor，含世界/邻接上下文），未注册时原样返回；处理器
 * 产出空列表或抛异常时保留 raw quads，绝不把"处理后为空"冒充成功。
 *
 * English: Loader-neutral entry that routes preview-scene quads through the
 * selected engine's registered processor chain (real native processors with
 * world/adjacency context) and returns them unchanged when no processor is
 * registered; empty or exceptional processor results keep the raw quads and
 * never treat "processed empty" as success.
 */
public final class PreviewSceneQuadProcessing {
    private PreviewSceneQuadProcessing() {}

    /**
     * 中文：报告当前所选引擎是否已注册预览场景 Quad 处理器；未选中引擎或未注册时
     * 返回 false。此只读查询复用 EngineQueryRouter.current 与
     * PreviewSceneQuadProcessorRegistry.find，不引入状态、缓存或新注册表。
     *
     * English: Reports whether the currently selected engine has a registered
     * preview-scene quad processor; returns false when no engine is selected or
     * none is registered. This read-only query reuses EngineQueryRouter.current
     * and PreviewSceneQuadProcessorRegistry.find without adding state, cache, or
     * a new registry.
     *
     * @return 中文：已注册返回 true，否则 false。 / English: true when registered, false otherwise.
     */
    public static boolean currentEngineHasSceneProcessor() {
        return EngineQueryRouter.current(
                        Minecraft.getInstance())
                .map(EngineQuerySelection::engineId)
                .flatMap(
                        PreviewSceneQuadProcessorRegistry
                                ::find)
                .isPresent();
    }

    public static List<BakedQuad> process(
            BlockAndTintGetter level,
            BlockState state,
            BlockPos worldPosition,
            List<BakedQuad> quads) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(worldPosition, "worldPosition");
        Optional<EngineQuerySelection> selection =
                EngineQueryRouter.current(
                        Minecraft.getInstance());
        Optional<String> engineId =
                selection.map(
                        EngineQuerySelection::engineId);
        Optional<PreviewSceneQuadProcessor> processor =
                engineId.flatMap(
                        PreviewSceneQuadProcessorRegistry
                                ::find);
        if (processor.isEmpty()) {
            return quads;
        }
        try {
            List<BakedQuad> processed =
                    processor
                            .orElseThrow()
                            .process(
                                    level,
                                    state,
                                    worldPosition,
                                    worldPosition.asLong(),
                                    quads);
            if (processed.isEmpty()
                    && !quads.isEmpty()) {
                // 中文：处理器产出空列表时绝不提交空结果；保留 raw quads。
                // English: Never submit an empty processor result; keep the raw quads.
                return quads;
            }
            return processed;
        } catch (RuntimeException exception) {
            return quads;
        }
    }
}
