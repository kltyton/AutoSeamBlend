package com.kltyton.autoseamblend.frontend.paint;

import java.util.List;
import java.util.Objects;

/**
 * 中文：公共绘画核心按原生 PNG 载体冻结的不可变直通 ARGB 修改计划。
 *
 * English: Immutable straight-ARGB edit plan frozen by the common paint core
 * for one native PNG carrier.
 */
public record CarrierEditPlan(
        String outputPath,
        String carrierContentKey,
        List<RegionEdit> regions) {
    public CarrierEditPlan {
        outputPath = requireText(outputPath, "outputPath");
        carrierContentKey = requireText(
                carrierContentKey,
                "carrierContentKey");
        regions = List.copyOf(
                Objects.requireNonNull(regions, "regions"));
        if (regions.isEmpty()) {
            throw new IllegalArgumentException(
                    "carrier edit plan must contain a region");
        }
    }

    /** 中文：首动画帧内的不可变矩形修改。 / English: Immutable rectangular edit in the first animation frame. */
    public record RegionEdit(
            int x,
            int y,
            int width,
            int height,
            int[] straightArgb) {
        public RegionEdit {
            straightArgb = Objects.requireNonNull(
                            straightArgb,
                            "straightArgb")
                    .clone();
            if (x < 0
                    || y < 0
                    || width <= 0
                    || height <= 0
                    || straightArgb.length
                            != Math.multiplyExact(
                                    width,
                                    height)) {
                throw new IllegalArgumentException(
                        "invalid paint region edit");
            }
        }

        @Override
        public int[] straightArgb() {
            return straightArgb.clone();
        }
    }

    private static String requireText(
            String value,
            String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    name + " must not be blank");
        }
        return value;
    }
}
