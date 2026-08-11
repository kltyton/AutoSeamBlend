package com.kltyton.autoseamblend.authoring.preview;

import com.kltyton.autoseamblend.runtime.render.OverlayCoverageAllocator;
import com.kltyton.autoseamblend.runtime.render.ProceduralConnectionPlan;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.mapping.Overlay17Layout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 中文：使用运行时连接计划和 overlay 覆盖分配器组合 Loader 无关的面预览。
 *
 * English: Composes Loader-neutral face previews with the runtime connection
 * plan and overlay coverage allocator.
 */
public final class PreviewCompositionComposer {
    private PreviewCompositionComposer() {}

    public static PreviewCompositionPlan compose(
            String receiverSourceKey,
            int receiverTint,
            ConnectionMethod faceMethod,
            List<PreviewCompositionSample> samples) {
        if (receiverSourceKey == null
                || receiverSourceKey.isBlank()) {
            throw new IllegalArgumentException(
                    "receiverSourceKey must not be blank");
        }
        List<PreviewCompositionSample> checkedSamples =
                List.copyOf(Objects.requireNonNull(
                        samples,
                        "samples"));
        if (checkedSamples.isEmpty()) {
            throw new IllegalArgumentException(
                    "preview composition needs a sample");
        }
        PreviewCompositionSample first =
                checkedSamples.get(0);
        ConnectionMethod checkedFaceMethod =
                Objects.requireNonNull(
                        faceMethod,
                        "faceMethod");
        ProceduralConnectionPlan plan =
                connectionPlan(checkedSamples);
        List<PreviewFaceLayer> layers =
                checkedFaceMethod.overlayCapable()
                        ? overlayLayers(
                                receiverSourceKey,
                                receiverTint,
                                checkedSamples)
                        : layersForPlan(
                                first.sourceKey(),
                                first.tint(),
                                plan);
        return new PreviewCompositionPlan(plan, layers);
    }

    /**
     * 中文：接收面始终先绘制；供体按输入顺序共享一个覆盖分配器，保持各自来源与 tint。
     *
     * English: The receiver is always drawn first. Donors share one coverage
     * allocator in input order while retaining their own source and tint.
     */
    private static List<PreviewFaceLayer> overlayLayers(
            String receiverSourceKey,
            int receiverTint,
            List<PreviewCompositionSample> samples) {
        ArrayList<PreviewFaceLayer> layers =
                new ArrayList<>();
        layers.add(PreviewFaceLayer.full(
                receiverSourceKey,
                receiverTint));
        OverlayCoverageAllocator coverage =
                new OverlayCoverageAllocator();
        for (PreviewCompositionSample sample : samples) {
            List<Integer> slots = Overlay17Layout.selectedSlots(
                    sample.connections());
            if (slots.isEmpty()) {
                continue;
            }
            ProceduralConnectionPlan allocation =
                    coverage.claim(
                            sample.overlayProfile(),
                            slots);
            for (ProceduralConnectionPlan.Patch patch
                    : allocation.patches()) {
                layers.add(layer(
                        sample.sourceKey(),
                        sample.tint(),
                        patch));
            }
        }
        return List.copyOf(layers);
    }

    private static ProceduralConnectionPlan connectionPlan(
            List<PreviewCompositionSample> samples) {
        PreviewCompositionSample first = samples.get(0);
        if (!first.renderMethod().overlayCapable()) {
            return ProceduralConnectionPlan.forConnections(
                    first.renderMethod(),
                    first.connections(),
                    first.frameProfile(),
                    first.overlayProfile());
        }
        OverlayCoverageAllocator coverage =
                new OverlayCoverageAllocator();
        ArrayList<ProceduralConnectionPlan.Patch> patches =
                new ArrayList<>();
        for (PreviewCompositionSample sample : samples) {
            List<Integer> slots = Overlay17Layout.selectedSlots(
                    sample.connections());
            if (slots.isEmpty()) {
                continue;
            }
            patches.addAll(coverage.claim(
                            sample.overlayProfile(),
                            slots)
                    .patches());
        }
        return patches.isEmpty()
                ? new ProceduralConnectionPlan(
                        ProceduralConnectionPlan.Mode.PASSTHROUGH,
                        List.of())
                : new ProceduralConnectionPlan(
                        ProceduralConnectionPlan.Mode.OVERLAY,
                        patches);
    }

    private static List<PreviewFaceLayer> layersForPlan(
            String sourceKey,
            int tint,
            ProceduralConnectionPlan plan) {
        ArrayList<PreviewFaceLayer> layers =
                new ArrayList<>();
        if (plan.patches().isEmpty()
                || plan.mode()
                        == ProceduralConnectionPlan.Mode.REPLACE) {
            return List.of(PreviewFaceLayer.full(
                    sourceKey,
                    tint));
        }
        if (plan.mode()
                == ProceduralConnectionPlan.Mode.OVERLAY) {
            layers.add(PreviewFaceLayer.full(
                    sourceKey,
                    tint));
        }
        for (ProceduralConnectionPlan.Patch patch
                : plan.patches()) {
            layers.add(layer(sourceKey, tint, patch));
        }
        return List.copyOf(layers);
    }

    private static PreviewFaceLayer layer(
            String sourceKey,
            int tint,
            ProceduralConnectionPlan.Patch patch) {
        return new PreviewFaceLayer(
                sourceKey,
                patch.x0(),
                patch.y0(),
                patch.x1(),
                patch.y1(),
                patch.u0(),
                patch.v0(),
                patch.u1(),
                patch.v1(),
                tint);
    }
}
