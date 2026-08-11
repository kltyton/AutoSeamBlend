package com.kltyton.autoseamblend.compat.fusion.authoring.materialize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 中文：RED 合同——Fusion FULL 载体行数必须区分静态与动画：静态 FULL 保留两行 padding
 * （8 行），动画 FULL 必须输出 6 行，否则 Fusion 1.3.12 的 6:8 宽高比校验拒绝方形容器；
 * 其他布局不受动画标志影响。
 *
 * <p>English: RED contract -- Fusion FULL carrier rows must distinguish static from animated
 * sheets: static FULL keeps two padding rows (8), animated FULL must emit 6 rows or Fusion
 * 1.3.12 rejects the square sheet via its 6:8 aspect-ratio check; other layouts ignore the
 * animation flag.
 */
class FusionNativeCarrierPlanningContractTest {
    @Test
    void staticFullKeepsEightPaddingRows() {
        FusionNativeEvidenceLayout full = fullLayout();
        assertEquals(
                8,
                FusionNativeCarrierPlanning.carrierRows(
                        full,
                        false),
                "static FULL must keep the native 8x8 padded frame");
    }

    @Test
    void animatedFullDropsPaddingRows() {
        FusionNativeEvidenceLayout full = fullLayout();
        assertEquals(
                6,
                FusionNativeCarrierPlanning.carrierRows(
                        full,
                        true),
                "animated FULL must emit a loadable 8x6 sheet");
    }

    @Test
    void otherLayoutsIgnoreAnimationFlag() {
        FusionNativeEvidenceLayout horizontal =
                new FusionNativeEvidenceLayout(
                        4,
                        1,
                        0,
                        List.of(
                                List.of(0),
                                List.of(1),
                                List.of(2),
                                List.of(3)));
        assertEquals(
                1,
                FusionNativeCarrierPlanning.carrierRows(
                        horizontal,
                        false));
        assertEquals(
                1,
                FusionNativeCarrierPlanning.carrierRows(
                        horizontal,
                        true),
                "non-FULL layouts must ignore the animation flag");
    }

    @Test
    void connectingMetadataWithoutFramesWritesExplicitOriginalIndices() {
        byte[] sourceMetadata = (
                "{\"animation\":{\"width\":16,\"height\":16,\"frametime\":2}}")
                .getBytes(StandardCharsets.UTF_8);
        int[] originalIndices = new int[] {
                0, 1, 2, 3, 4, 5,
                6, 7, 8, 9, 10, 11
        };
        byte[] output =
                FusionNativeCarrierPlanning.generatedConnectingMetadata(
                        ConnectionMethod.CTM,
                        false,
                        "full",
                        sourceMetadata,
                        128,
                        96,
                        true,
                        originalIndices,
                        true);
        JsonObject animation = JsonParser.parseString(
                        new String(
                                output,
                                StandardCharsets.UTF_8))
                .getAsJsonObject()
                .getAsJsonObject("animation");
        assertEquals(
                128,
                animation.get("width").getAsInt());
        assertEquals(
                96,
                animation.get("height").getAsInt());
        assertEquals(
                2,
                animation.get("frametime").getAsInt(),
                "frametime must be preserved");
        JsonArray frames = animation.getAsJsonArray("frames");
        assertEquals(
                List.of(
                        0, 1, 2, 3, 4, 5,
                        6, 7, 8, 9, 10, 11),
                frames.asList().stream()
                        .map(JsonElement::getAsInt)
                        .toList(),
                "missing frames must be filled with the original indices");
    }

    @Test
    void connectingMetadataKeepsExistingFrameObjectsAndTimes() {
        String framesJson =
                "[{\"index\":0,\"time\":10},{\"index\":1,\"time\":20}]";
        byte[] sourceMetadata = (
                "{\"animation\":{\"width\":16,\"height\":16,\"frametime\":2,"
                        + "\"frames\":" + framesJson + "}}")
                .getBytes(StandardCharsets.UTF_8);
        byte[] output =
                FusionNativeCarrierPlanning.generatedConnectingMetadata(
                        ConnectionMethod.CTM,
                        false,
                        "full",
                        sourceMetadata,
                        128,
                        96,
                        true,
                        new int[] {0, 1},
                        true);
        JsonObject animation = JsonParser.parseString(
                        new String(
                                output,
                                StandardCharsets.UTF_8))
                .getAsJsonObject()
                .getAsJsonObject("animation");
        assertEquals(
                framesJson,
                animation.getAsJsonArray("frames").toString(),
                "existing per-frame time objects must be preserved");
        assertEquals(
                10,
                animation.getAsJsonArray("frames")
                        .get(0)
                        .getAsJsonObject()
                        .get("time")
                        .getAsInt(),
                "the first frame time object must survive");
        assertEquals(
                20,
                animation.getAsJsonArray("frames")
                        .get(1)
                        .getAsJsonObject()
                        .get("time")
                        .getAsInt(),
                "the second frame time object must survive");
    }

    @Test
    void connectingMetadataWithoutPaddingDoesNotAddFrames() {
        byte[] sourceMetadata = (
                "{\"animation\":{\"width\":16,\"height\":16,\"frametime\":2}}")
                .getBytes(StandardCharsets.UTF_8);
        byte[] output =
                FusionNativeCarrierPlanning.generatedConnectingMetadata(
                        ConnectionMethod.CTM,
                        false,
                        "full",
                        sourceMetadata,
                        128,
                        96,
                        true,
                        new int[] {0, 1},
                        false);
        JsonObject animation = JsonParser.parseString(
                        new String(
                                output,
                                StandardCharsets.UTF_8))
                .getAsJsonObject()
                .getAsJsonObject("animation");
        assertFalse(
                animation.has("frames"),
                "no padding means no explicit frames array is written");
    }

    private static FusionNativeEvidenceLayout
            fullLayout() {
        return new FusionNativeEvidenceLayout(
                8,
                6,
                0,
                List.of(
                        List.of(0),
                        List.of(1)));
    }
}
