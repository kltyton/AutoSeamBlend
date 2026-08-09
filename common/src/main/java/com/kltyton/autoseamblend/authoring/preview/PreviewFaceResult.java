package com.kltyton.autoseamblend.authoring.preview;

import com.kltyton.autoseamblend.runtime.render.ProceduralConnectionPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * 中文：右侧精确面预览的不可变精灵图层；图层可以表达替换、透明叠加和分区选槽。
 *
 * English:
 * Immutable sprite layers for the exact face preview. Layers can represent
 * replacement, transparent overlays, and region-specific native slot choices.
 *
 * @param layers 中文：不可变精灵图层列表。 / English: Immutable sprite layer list.
 */
public record PreviewFaceResult(
        List<Layer> layers) {
    public PreviewFaceResult {
        layers = List.copyOf(
                Objects.requireNonNull(
                        layers,
                        "layers"));
        if (layers.isEmpty()) {
            throw new IllegalArgumentException(
                    "preview face result needs at least one layer");
        }
    }

    public static PreviewFaceResult full(
            TextureAtlasSprite sprite) {
        return full(sprite, -1);
    }

    public static PreviewFaceResult full(
            TextureAtlasSprite sprite,
            int color) {
        return new PreviewFaceResult(
                List.of(Layer.full(sprite, color)));
    }

    /**
     * 中文：仅作为尚未提供精确选槽 SPI 的引擎回退；已适配引擎应直接返回其原生最终图层。
     *
     * English:
     * Fallback only for engines that have not supplied exact slot selection.
     * Adapted engines should return their native final layers directly.
     */
    public static PreviewFaceResult fromPlan(
            TextureAtlasSprite sprite,
            ProceduralConnectionPlan plan) {
        return fromPlan(sprite, plan, -1);
    }

    public static PreviewFaceResult fromPlan(
            TextureAtlasSprite sprite,
            ProceduralConnectionPlan plan,
            int color) {
        Objects.requireNonNull(sprite, "sprite");
        Objects.requireNonNull(plan, "plan");
        ArrayList<Layer> layers =
                new ArrayList<>();
        if (plan.patches().isEmpty()
                || plan.mode()
                        == ProceduralConnectionPlan.Mode
                                .REPLACE) {
            layers.add(Layer.full(sprite, color));
            return new PreviewFaceResult(
                    layers);
        }
        if (plan.mode()
                == ProceduralConnectionPlan.Mode.OVERLAY) {
            layers.add(Layer.full(sprite, color));
        }
        for (ProceduralConnectionPlan.Patch patch
                : plan.patches()) {
            layers.add(new Layer(
                    sprite,
                    patch.x0(),
                    patch.y0(),
                    patch.x1(),
                    patch.y1(),
                    patch.u0(),
                    patch.v0(),
                    patch.u1(),
                    patch.v1(),
                    color));
        }
        return new PreviewFaceResult(
                layers);
    }

    /**
     * 中文：以 matchBlocks 接收面为底层，并只把 connectBlocks 供体用于叠加 patch。
     *
     * English:
     * Uses the matchBlocks receiver face as the base and the connectBlocks
     * donor only for overlay patches.
     */
    public static PreviewFaceResult overlayFromPlan(
            TextureAtlasSprite receiver,
            TextureAtlasSprite donor,
            ProceduralConnectionPlan plan) {
        return overlayFromPlan(
                receiver,
                -1,
                donor,
                -1,
                plan);
    }

    public static PreviewFaceResult overlayFromPlan(
            TextureAtlasSprite receiver,
            int receiverColor,
            TextureAtlasSprite donor,
            int donorColor,
            ProceduralConnectionPlan plan) {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(donor, "donor");
        Objects.requireNonNull(plan, "plan");
        ArrayList<Layer> layers = new ArrayList<>();
        layers.add(Layer.full(receiver, receiverColor));
        if (plan.mode()
                == ProceduralConnectionPlan.Mode.OVERLAY) {
            for (ProceduralConnectionPlan.Patch patch
                    : plan.patches()) {
                layers.add(new Layer(
                        donor,
                        patch.x0(),
                        patch.y0(),
                        patch.x1(),
                        patch.y1(),
                        patch.u0(),
                        patch.v0(),
                        patch.u1(),
                        patch.v1(),
                        donorColor));
            }
        }
        return new PreviewFaceResult(layers);
    }

    public record Layer(
            TextureAtlasSprite sprite,
            float x0,
            float y0,
            float x1,
            float y1,
            float u0,
            float v0,
            float u1,
            float v1,
            int color) {
        public Layer {
            Objects.requireNonNull(sprite, "sprite");
            validateRange(x0, x1, "x");
            validateRange(y0, y1, "y");
            validateRange(u0, u1, "u");
            validateRange(v0, v1, "v");
        }

        public static Layer full(
                TextureAtlasSprite sprite) {
            return full(sprite, -1);
        }

        public static Layer full(
                TextureAtlasSprite sprite,
                int color) {
            return new Layer(
                    sprite,
                    0.0F,
                    0.0F,
                    1.0F,
                    1.0F,
                    0.0F,
                    0.0F,
                    1.0F,
                    1.0F,
                    color);
        }

        private static void validateRange(
                float start,
                float end,
                String axis) {
            if (!Float.isFinite(start)
                    || !Float.isFinite(end)
                    || start < 0.0F
                    || end > 1.0F
                    || start >= end) {
                throw new IllegalArgumentException(
                        axis
                                + " coordinates must form a normalized range");
            }
        }
    }
}
