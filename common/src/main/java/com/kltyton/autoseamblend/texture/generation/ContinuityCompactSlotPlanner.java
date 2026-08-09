package com.kltyton.autoseamblend.texture.generation;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntBinaryOperator;

/**
 * 中文：集中处理 Continuity compact 槽位选择器的确定性规划；Loader 只提供原生选择器。
 * English: Centralizes deterministic Continuity compact slot planning; a Loader only supplies the native selector.
 */
public final class ContinuityCompactSlotPlanner {
    private static final int CONNECTION_LIMIT = 0xFF;
    private static final int QUADRANT_COUNT = 4;
    private static final int SLOT_COUNT = 5;

    private ContinuityCompactSlotPlanner() {}

    /**
     * 中文：为五个 compact 槽位寻找四象限一致的代表连接掩码。
     * English: Finds a four-quadrant-uniform representative connection mask for each compact slot.
     */
    public static int[] representatives(
            String nativeEngine,
            IntBinaryOperator nativeSelector) {
        Objects.requireNonNull(nativeEngine, "nativeEngine");
        Objects.requireNonNull(nativeSelector, "nativeSelector");
        int[] representatives = new int[SLOT_COUNT];
        Arrays.fill(representatives, -1);
        for (int connections = 0;
                connections <= CONNECTION_LIMIT;
                connections++) {
            int slot = nativeSelector.applyAsInt(0, connections);
            if (slot < 0 || slot >= SLOT_COUNT) {
                throw new IllegalStateException(
                        nativeEngine
                                + " compact slot outside [0,4]: "
                                + slot);
            }
            boolean uniform = true;
            for (int quadrant = 1;
                    quadrant < QUADRANT_COUNT;
                    quadrant++) {
                if (nativeSelector.applyAsInt(quadrant, connections)
                        != slot) {
                    uniform = false;
                    break;
                }
            }
            if (uniform && representatives[slot] < 0) {
                representatives[slot] = connections;
            }
        }
        for (int representative : representatives) {
            if (representative < 0) {
                throw new IllegalStateException(
                        nativeEngine
                                + " compact selector does not cover every slot");
            }
        }
        return representatives;
    }

    /**
     * 中文：读取一个连接掩码的四个原生象限槽位。
     * English: Reads the four native quadrant slots for one connection mask.
     */
    public static int[] slots(
            String nativeEngine,
            int connections,
            IntBinaryOperator nativeSelector) {
        Objects.requireNonNull(nativeEngine, "nativeEngine");
        Objects.requireNonNull(nativeSelector, "nativeSelector");
        if (connections < 0 || connections > CONNECTION_LIMIT) {
            throw new IllegalArgumentException("compact connections must fit eight bits");
        }
        int[] slots = new int[QUADRANT_COUNT];
        for (int quadrant = 0;
                quadrant < QUADRANT_COUNT;
                quadrant++) {
            slots[quadrant] = nativeSelector.applyAsInt(quadrant, connections);
        }
        return slots;
    }
}
