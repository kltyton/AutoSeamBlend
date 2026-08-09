package com.kltyton.autoseamblend.inference;

import com.kltyton.autoseamblend.inference.InferenceFacts.FactState;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 中文：基于可观察事实的纯有序策略；名称和资源路径被刻意排除。 / English: Pure, ordered policy over observable facts. Names and resource paths are deliberately absent. */
public final class InferencePolicy {
    public static final int ALGORITHM_VERSION = 9;

    private InferencePolicy() {}

    public static InferenceDecision decide(ConnectionMethod requested, InferenceFacts facts) {
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(facts, "facts");
        if (requested != ConnectionMethod.AUTO) {
            return new InferenceDecision(
                    requested, java.util.Optional.of(requested), true,
                    InferenceDecision.Confidence.CERTAIN,
                    List.of("manual_method_override"), List.of());
        }

        ArrayList<String> unknown = unknownFacts(facts);
        if (!unknown.isEmpty()) {
            return new InferenceDecision(
                    ConnectionMethod.AUTO,
                    java.util.Optional.empty(),
                    false,
                    InferenceDecision.Confidence.REJECTED,
                    List.of("auto_requires_complete_same_reload_facts"),
                    unknown);
        }
        if (facts.vanillaCuboidGeometry().isFalse()) {
            return inferred(ConnectionMethod.NONE, "non_vanilla_geometry_not_auto_eligible");
        }
        if (facts.axisAlignedGeometry().isFalse()) {
            return inferred(ConnectionMethod.NONE, "non_axis_aligned_geometry");
        }
        if (facts.validUv().isFalse()) {
            return inferred(ConnectionMethod.NONE, "invalid_uv_domain");
        }
        if (facts.topOnly().isTrue()) {
            return inferred(ConnectionMethod.TOP, "top_only_face_domain");
        }
        boolean horizontal = facts.allowedAxes().contains(ConnectionAxis.HORIZONTAL);
        boolean vertical = facts.allowedAxes().contains(ConnectionAxis.VERTICAL);
        if (horizontal && !vertical) {
            return inferred(ConnectionMethod.HORIZONTAL, "horizontal_axis_only");
        }
        if (vertical && !horizontal) {
            return inferred(ConnectionMethod.VERTICAL, "vertical_axis_only");
        }
        // 中文：tinted translucent 的完整方块表面是 overlay 层（如草侧 overlay 层：带透明
        // 边框+基底下层），必须先于“透明边框→CTM”判定为 OVERLAY 供体，否则供体候选被排除、
        // overlay 不进入发射分支。不依赖任何方块/精灵名。
        // English: A tinted translucent full-block face is an overlay layer (e.g. a grass-side
        // overlay layer: framed alpha over a base) and must infer OVERLAY before the
        // transparent-frame CTM rule, otherwise the donor candidate is dropped and the overlay
        // never enters emission. No block or sprite names are used.
        if (facts.alphaOpaque().isFalse()
                && facts.tintPresent().isTrue()
                && facts.fullBlock().isTrue()
                && facts.partialGeometry().isFalse()) {
            return inferred(
                    ConnectionMethod.OVERLAY,
                    "tinted_translucent_overlay_layer");
        }
        if (facts.alphaOpaque().isFalse()
                && facts.framedAlpha().isTrue()) {
            return inferred(
                    ConnectionMethod.CTM,
                    "transparent_perimeter_frame_native_ctm");
        }
        if (facts.alphaOpaque().isFalse()
                && facts.partialGeometry().isTrue()) {
            return inferred(
                    ConnectionMethod.NONE,
                    "transparent_unframed_partial_geometry_passthrough");
        }
        if (facts.animated().isTrue()
                || facts.alphaOpaque().isFalse()
                || facts.tintPresent().isTrue()) {
            return inferred(
                    ConnectionMethod.OVERLAY,
                    "animated_translucent_or_tinted_native_overlay");
        }
        if (facts.fullBlock().isTrue()
                && facts.axisAlignedGeometry().isTrue()
                && facts.spriteConsistent().isTrue()
                && facts.partialGeometry().isFalse()) {
            return inferred(
                    ConnectionMethod.OVERLAY,
                    "uniform_full_block_native_overlay");
        }
        if (horizontal && vertical && facts.partialGeometry().isTrue()) {
            return inferred(ConnectionMethod.HORIZONTAL_VERTICAL, "partial_geometry_both_axes");
        }
        return inferred(
                ConnectionMethod.OVERLAY,
                "general_known_two_axis_native_overlay");
    }

    private static InferenceDecision inferred(ConnectionMethod method, String evidence) {
        return new InferenceDecision(
                ConnectionMethod.AUTO,
                java.util.Optional.of(method),
                false,
                InferenceDecision.Confidence.CERTAIN,
                List.of(evidence),
                List.of());
    }

    private static ArrayList<String> unknownFacts(InferenceFacts facts) {
        ArrayList<String> unknown = new ArrayList<>();
        addUnknown(unknown, "vanilla_cuboid_geometry", facts.vanillaCuboidGeometry());
        addUnknown(unknown, "axis_aligned_geometry", facts.axisAlignedGeometry());
        addUnknown(unknown, "valid_uv", facts.validUv());
        addUnknown(unknown, "sprite_consistency", facts.spriteConsistent());
        addUnknown(unknown, "state_identity", facts.stateKnown());
        addUnknown(unknown, "alpha", facts.alphaOpaque());
        addUnknown(unknown, "framed_alpha", facts.framedAlpha());
        addUnknown(unknown, "animation", facts.animated());
        addUnknown(unknown, "tint", facts.tintPresent());
        addUnknown(unknown, "full_block", facts.fullBlock());
        addUnknown(unknown, "partial_geometry", facts.partialGeometry());
        addUnknown(unknown, "top_only", facts.topOnly());
        addUnknown(unknown, "native_ownership", facts.nativeOwnership());
        addUnknown(unknown, "allowed_axes", facts.allowedAxesKnown());
        return unknown;
    }

    private static void addUnknown(List<String> values, String name, FactState state) {
        if (state == FactState.UNKNOWN) values.add(name);
    }
}
