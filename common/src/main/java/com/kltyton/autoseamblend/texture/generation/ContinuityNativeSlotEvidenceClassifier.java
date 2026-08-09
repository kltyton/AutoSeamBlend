package com.kltyton.autoseamblend.texture.generation;

import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import com.kltyton.autoseamblend.engine.ownership.NativeSlotIntent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 中文：把 Loader 提供的无第三方槽位观测归一化为原生资源证据；不读取资源或解释 Continuity 文档。
 * English: Normalizes Loader-provided third-party-free slot observations into native-resource evidence without reading resources or interpreting Continuity documents.
 */
public final class ContinuityNativeSlotEvidenceClassifier {
    private ContinuityNativeSlotEvidenceClassifier() {}

    public static List<NativeSlot> classify(List<Observation> observations) {
        Objects.requireNonNull(observations, "observations");
        ArrayList<NativeSlot> slots = new ArrayList<>(observations.size());
        for (Observation observation : observations) {
            Objects.requireNonNull(observation, "observation");
            slots.add(
                    switch (observation.marker()) {
                        case DEFAULT ->
                                new NativeSlot(
                                        observation.index(),
                                        NativeSlotIntent.DEFAULT,
                                        Optional.empty());
                        case SKIP ->
                                new NativeSlot(
                                        observation.index(),
                                        NativeSlotIntent.SKIP,
                                        Optional.empty());
                        case SPRITE ->
                                new NativeSlot(
                                        observation.index(),
                                        observation.pngPresent()
                                                ? NativeSlotIntent.PRESENT
                                                : NativeSlotIntent.DECLARED_MISSING,
                                        observation.spriteId());
                    });
        }
        return List.copyOf(slots);
    }

    public static Observation sprite(
            int index,
            String spriteId,
            boolean pngPresent) {
        return new Observation(
                index,
                Marker.SPRITE,
                Optional.of(Objects.requireNonNull(spriteId, "spriteId")),
                pngPresent);
    }

    public static Observation defaultMarker(int index) {
        return new Observation(
                index,
                Marker.DEFAULT,
                Optional.empty(),
                false);
    }

    public static Observation skipMarker(int index) {
        return new Observation(
                index,
                Marker.SKIP,
                Optional.empty(),
                false);
    }

    public enum Marker {
        SPRITE,
        DEFAULT,
        SKIP
    }

    public record Observation(
            int index,
            Marker marker,
            Optional<String> spriteId,
            boolean pngPresent) {
        public Observation {
            if (index < 0) {
                throw new IllegalArgumentException("index must be non-negative");
            }
            Objects.requireNonNull(marker, "marker");
            spriteId = Objects.requireNonNull(spriteId, "spriteId");
            switch (marker) {
                case SPRITE -> {
                    if (spriteId.isEmpty() || spriteId.orElseThrow().isBlank()) {
                        throw new IllegalArgumentException(
                                "sprite observations must retain their native sprite id");
                    }
                }
                case DEFAULT, SKIP -> {
                    if (spriteId.isPresent() || pngPresent) {
                        throw new IllegalArgumentException(
                                "special native markers cannot carry sprite evidence");
                    }
                }
            }
        }
    }
}
