package com.kltyton.autoseamblend.authoring.preview;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：Loader 无关的预览场景 Quad 处理器端口；实现保留在各自引擎的 compat 包，
 * 消费方只依赖本接口与 Minecraft 类型，不链接任何第三方引擎类型。
 *
 * English: Loader-neutral preview-scene quad processor port. Implementations live
 * in their engine compat package; consumers depend only on this interface and
 * Minecraft types, never linking third-party engine types.
 */
public interface PreviewSceneQuadProcessor {
    /**
     * 中文：引擎标识，与引擎选择路由的 engineId 一致。
     *
     * English: Engine id, matching the engine selection router's engineId.
     */
    String engineId();

    /**
     * 中文：把源 Quad 交给所选引擎的真实处理器链（含世界/邻接上下文）处理，
     * 返回最终可提交的 Quad 列表。
     *
     * English: Runs the source quads through the selected engine's real processor
     * chain (with world/adjacency context) and returns the final submit-ready
     * quads.
     */
    List<BakedQuad> process(
            BlockAndTintGetter level,
            BlockState state,
            BlockPos pos,
            long randomSeed,
            List<BakedQuad> sourceQuads);

    /**
     * 中文：实现必须保持输入不可变。
     *
     * English: Implementations must not mutate the input list.
     */
    static void requireUnmodifiable(
            List<BakedQuad> sourceQuads) {
        Objects.requireNonNull(
                sourceQuads,
                "sourceQuads");
    }
}
