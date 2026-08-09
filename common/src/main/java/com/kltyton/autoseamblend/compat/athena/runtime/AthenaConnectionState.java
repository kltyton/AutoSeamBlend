package com.kltyton.autoseamblend.compat.athena.runtime;

import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import java.util.Objects;

/**
 * 中文：Athena 八方向状态的引擎无关值对象；字段顺序与 4.0.6 CtmState 合同一致。
 * English: Loader-neutral value object for Athena's eight-way state; field order matches the
 * 4.0.6 CtmState contract.
 */
public record AthenaConnectionState(
        boolean up,
        boolean down,
        boolean left,
        boolean right,
        boolean upLeft,
        boolean upRight,
        boolean downLeft,
        boolean downRight) {

    /**
     * 中文：按 Athena 原生字段顺序构造状态，集中维护跨 Loader 的布尔元组顺序。
     * English: Creates a state in Athena's native field order, centralizing the boolean tuple
     * order shared by both loaders.
     */
    public static AthenaConnectionState of(
            boolean up,
            boolean down,
            boolean left,
            boolean right,
            boolean upLeft,
            boolean upRight,
            boolean downLeft,
            boolean downRight) {
        return new AthenaConnectionState(
                up,
                down,
                left,
                right,
                upLeft,
                upRight,
                downLeft,
                downRight);
    }

    /**
     * 中文：把状态交给 Loader 的原生八布尔构造器，避免各适配器复制字段顺序。
     * English: Passes this state to a loader-native eight-boolean constructor without repeating
     * the field ordering in each adapter.
     */
    public <T> T map(OrderedStateFactory<T> factory) {
        Objects.requireNonNull(factory, "factory");
        return factory.create(
                up,
                down,
                left,
                right,
                upLeft,
                upRight,
                downLeft,
                downRight);
    }

    @FunctionalInterface
    public interface OrderedStateFactory<T> {
        T create(
                boolean up,
                boolean down,
                boolean left,
                boolean right,
                boolean upLeft,
                boolean upRight,
                boolean downLeft,
                boolean downRight);
    }

    /** 中文：从项目八位掩码构造 Athena 字段顺序。 / English: Creates Athena field order from the project's eight-bit mask. */
    public static AthenaConnectionState fromConnections(
            NeighborConnections connections) {
        Objects.requireNonNull(connections, "connections");
        int bits = connections.bits();
        return of(
                set(bits, 6),
                set(bits, 2),
                set(bits, 0),
                set(bits, 4),
                set(bits, 7),
                set(bits, 5),
                set(bits, 1),
                set(bits, 3));
    }

    /** 中文：投影回项目八位掩码。 / English: Projects the value back to the project's eight-bit mask. */
    public NeighborConnections toConnections() {
        int bits = 0;
        if (left) bits |= 1;
        if (downLeft) bits |= 1 << 1;
        if (down) bits |= 1 << 2;
        if (downRight) bits |= 1 << 3;
        if (right) bits |= 1 << 4;
        if (upRight) bits |= 1 << 5;
        if (up) bits |= 1 << 6;
        if (upLeft) bits |= 1 << 7;
        return NeighborConnections.fromBits(bits);
    }

    public int bits() {
        return toConnections().bits();
    }

    public boolean allTrue() {
        return up && down && left && right
                && upLeft && upRight && downLeft && downRight;
    }

    private static boolean set(int bits, int bit) {
        return (bits & 1 << bit) != 0;
    }
}
