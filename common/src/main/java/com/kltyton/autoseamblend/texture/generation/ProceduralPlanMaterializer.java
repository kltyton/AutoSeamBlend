package com.kltyton.autoseamblend.texture.generation;

import com.kltyton.autoseamblend.runtime.render.ProceduralConnectionPlan;
import com.kltyton.autoseamblend.runtime.render.ProceduralConnectionPlan.Patch;
import java.util.Objects;

/**
 * 中文：栅格化运行时渲染所使用的同一硬边补丁计划；采用最近邻采样，不引入插值、镜像边缘带或 alpha 羽化。
 *
 * English:
 * Rasterizes the same hard-edged patch plan used by runtime rendering.
 *
 * <p>Sampling is nearest-neighbor. No interpolation, mirrored edge band, or
 * alpha feathering is introduced.
 */
public final class ProceduralPlanMaterializer {
    private ProceduralPlanMaterializer() {}

    public static int[] materializeStraightArgb(
            int width,
            int height,
            int[] straightArgb,
            ProceduralConnectionPlan plan) {
        Objects.requireNonNull(straightArgb, "straightArgb");
        Objects.requireNonNull(plan, "plan");
        if (width <= 0
                || height <= 0
                || (long) width * height
                        != straightArgb.length) {
            throw new IllegalArgumentException(
                    "invalid source dimensions "
                            + width + 'x' + height);
        }
        if (plan.mode()
                == ProceduralConnectionPlan.Mode.PASSTHROUGH) {
            return straightArgb.clone();
        }
        int[] output = new int[Math.multiplyExact(
                width,
                height)];
        for (int y = 0; y < height; y++) {
            float v = (y + 0.5F) / height;
            for (int x = 0; x < width; x++) {
                float u = (x + 0.5F) / width;
                Patch patch = containing(
                        plan,
                        u,
                        v);
                if (patch == null) {
                    continue;
                }
                float amountX =
                        (u - patch.x0())
                                / (patch.x1() - patch.x0());
                float amountY =
                        (v - patch.y0())
                                / (patch.y1() - patch.y0());
                float sourceU = lerp(
                        patch.u0(),
                        patch.u1(),
                        amountX);
                float sourceV = lerp(
                        patch.v0(),
                        patch.v1(),
                        amountY);
                int sourceX = coordinate(
                        sourceU,
                        width);
                int sourceY = coordinate(
                        sourceV,
                        height);
                int coverage = coverage(
                        patch,
                        amountX,
                        amountY);
                output[y * width + x] =
                        applyCoverage(
                                straightArgb[
                                        sourceY * width
                                                + sourceX],
                                coverage);
            }
        }
        return output;
    }

    private static Patch containing(
            ProceduralConnectionPlan plan,
            float u,
            float v) {
        for (Patch patch : plan.patches()) {
            if (u >= patch.x0()
                    && u < patch.x1()
                    && v >= patch.y0()
                    && v < patch.y1()) {
                return patch;
            }
        }
        return null;
    }

    private static int coordinate(
            float normalized,
            int size) {
        return Math.min(
                size - 1,
                Math.max(
                        0,
                        (int) Math.floor(
                                normalized * size)));
    }

    private static float lerp(
            float start,
            float end,
            float amount) {
        return start + (end - start) * amount;
    }

    private static int coverage(
            Patch patch,
            float x,
            float y) {
        float top = lerp(
                patch.alpha00(),
                patch.alpha10(),
                x);
        float bottom = lerp(
                patch.alpha01(),
                patch.alpha11(),
                x);
        return Math.max(
                0,
                Math.min(
                        255,
                        Math.round(lerp(
                                top,
                                bottom,
                                y))));
    }

    private static int applyCoverage(
            int straightArgb,
            int coverage) {
        int alpha = straightArgb >>> 24;
        int coveredAlpha =
                (alpha * coverage + 127) / 255;
        return coveredAlpha << 24
                | straightArgb & 0x00FF_FFFF;
    }
}
