package com.kltyton.autoseamblend.engine.ownership;

import java.util.List;

/** 中文：一个运行时代次中所有适配器已接受规则所有权的不可变聚合。 / English: Immutable aggregate of every adapter's accepted-rule ownership in one runtime generation. */
public record NativeOwnershipSnapshot(long generation, int holderCount, List<NativeOwnership> rules) {
    public NativeOwnershipSnapshot {
        if (generation < 0 || holderCount < 0) throw new IllegalArgumentException("counts must be non-negative");
        rules = List.copyOf(rules);
    }

    public static NativeOwnershipSnapshot empty() {
        return new NativeOwnershipSnapshot(0, 0, List.of());
    }

    public NativeOwnershipSnapshot withGeneration(long nextGeneration) {
        return new NativeOwnershipSnapshot(nextGeneration, holderCount, rules);
    }
}
