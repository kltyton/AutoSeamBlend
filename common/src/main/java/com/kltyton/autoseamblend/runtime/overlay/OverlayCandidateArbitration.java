package com.kltyton.autoseamblend.runtime.overlay;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * 中文：集中 overlay 候选的严格 winner 判断和稳定排序，Loader 适配器只提供候选比较事实。
 * English: Centralizes strict overlay winner checks and stable sorting while Loader adapters
 * provide only candidate comparison facts.
 */
public final class OverlayCandidateArbitration {
    private OverlayCandidateArbitration() {}

    /**
     * 中文：按公共 priority 产生供体顺序；列表顺序保持由调用方控制的稳定平局语义。
     * English: Builds donor ordering from the shared priority while preserving caller-controlled
     * stable tie semantics.
     */
    public static <T> Comparator<T> orderBy(
            Function<? super T, ? extends OverlayCandidatePriority> priority) {
        Objects.requireNonNull(priority, "priority");
        return (left, right) -> priority.apply(left).compareTo(priority.apply(right));
    }

    /**
     * 中文：只有严格更高者可覆盖接收体；没有接收体时首个候选直接胜出。
     * English: Only a strictly higher candidate may cover a receiver; a candidate wins directly
     * when no receiver exists.
     */
    public static <T> boolean winsOver(
            T donor,
            Optional<T> receiver,
            Comparator<? super T> order) {
        Objects.requireNonNull(donor, "donor");
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(order, "order");
        return receiver.map(value -> order.compare(donor, value) > 0).orElse(true);
    }

    /**
     * 中文：用同一 winner comparator 就地排序，供体列表最终保持最低到最高绘制顺序。
     * English: Sorts in place with the same winner comparator, leaving donors in low-to-high
     * painter order.
     */
    public static <T> void sortInPlace(
            List<T> candidates,
            Comparator<? super T> order) {
        Objects.requireNonNull(candidates, "candidates");
        candidates.sort(Objects.requireNonNull(order, "order"));
    }
}
