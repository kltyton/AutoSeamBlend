package com.kltyton.autoseamblend.authoring.preview;

import com.kltyton.autoseamblend.runtime.render.ProceduralConnectionPlan;
import java.util.List;
import java.util.Objects;

/**
 * 中文：连接计划与最终面图层顺序共享的不可变公共预览答案。
 *
 * English: Immutable common preview answer sharing one connection plan and
 * final face-layer order.
 *
 * @param connectionPlan 中文：共享连接计划。 / English: Shared connection plan.
 * @param layers 中文：最终面图层顺序。 / English: Final face-layer order.
 */
public record PreviewCompositionPlan(
        ProceduralConnectionPlan connectionPlan,
        List<PreviewFaceLayer> layers) {
    public PreviewCompositionPlan {
        connectionPlan = Objects.requireNonNull(
                connectionPlan,
                "connectionPlan");
        layers = List.copyOf(
                Objects.requireNonNull(layers, "layers"));
        if (layers.isEmpty()) {
            throw new IllegalArgumentException(
                    "preview composition needs at least one layer");
        }
    }
}
