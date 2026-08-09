package com.kltyton.autoseamblend.frontend.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * 中文：合并绘画载体的不可变快照并拒绝同路径冲突；不依赖任何 Loader 类型。
 *
 * English: Merges immutable paint-carrier snapshots and rejects conflicting
 * values without linking any Loader type.
 */
public final class WorkbenchSourceConflictReducer {
    private WorkbenchSourceConflictReducer() {}

    public static <T> Map<String, T> merge(
            List<? extends Map<String, T>> sources,
            BiPredicate<T, T> equivalent,
            String diagnosticPrefix) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(equivalent, "equivalent");
        Objects.requireNonNull(diagnosticPrefix, "diagnosticPrefix");
        LinkedHashMap<String, T> result = new LinkedHashMap<>();
        for (Map<String, T> source : sources) {
            Objects.requireNonNull(source, "source").forEach((key, value) -> {
                Objects.requireNonNull(key, "source key");
                Objects.requireNonNull(value, "source value");
                T previous = result.putIfAbsent(key, value);
                if (previous != null && !equivalent.test(previous, value)) {
                    throw new IllegalArgumentException(
                            diagnosticPrefix + ":" + key);
                }
            });
        }
        return Map.copyOf(result);
    }

    /** 中文：Neo/Fabric 载体均可投影到此结构比较内容；比较算法保持在 common。 / English: Loader carriers project to this shape so comparison stays common. */
    public static boolean equivalent(
            TextureSourceShape left,
            TextureSourceShape right) {
        return left.sheetWidth() == right.sheetWidth()
                && left.sheetHeight() == right.sheetHeight()
                && left.frameWidth() == right.frameWidth()
                && left.frameHeight() == right.frameHeight()
                && java.util.Arrays.equals(left.frameIndices(), right.frameIndices())
                && java.util.Arrays.equals(left.firstFrameStraightArgb(), right.firstFrameStraightArgb())
                && java.util.Arrays.equals(left.sourceMetadata(), right.sourceMetadata());
    }

    public record TextureSourceShape(
            int sheetWidth,
            int sheetHeight,
            int frameWidth,
            int frameHeight,
            int[] frameIndices,
            int[] firstFrameStraightArgb,
            byte[] sourceMetadata) {
        public TextureSourceShape {
            if (sheetWidth < 0 || sheetHeight < 0 || frameWidth < 0 || frameHeight < 0) {
                throw new IllegalArgumentException("texture source dimensions must be nonnegative");
            }
            frameIndices = Objects.requireNonNull(frameIndices, "frameIndices").clone();
            firstFrameStraightArgb = Objects.requireNonNull(firstFrameStraightArgb, "firstFrameStraightArgb").clone();
            sourceMetadata = Objects.requireNonNull(sourceMetadata, "sourceMetadata").clone();
        }

        @Override public int[] frameIndices() { return frameIndices.clone(); }
        @Override public int[] firstFrameStraightArgb() { return firstFrameStraightArgb.clone(); }
        @Override public byte[] sourceMetadata() { return sourceMetadata.clone(); }
    }
}
