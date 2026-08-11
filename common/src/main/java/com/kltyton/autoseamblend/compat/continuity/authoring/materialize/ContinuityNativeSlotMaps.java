package com.kltyton.autoseamblend.compat.continuity.authoring.materialize;

import com.kltyton.autoseamblend.texture.generation.ContinuityCompactSlotPlanner;
import me.pepperbell.continuity.client.processor.CompactCtmQuadProcessor;
import me.pepperbell.continuity.client.processor.simple.HorizontalSpriteProvider;
import me.pepperbell.continuity.client.processor.simple.HorizontalVerticalSpriteProvider;
import me.pepperbell.continuity.client.processor.simple.VerticalHorizontalSpriteProvider;
import me.pepperbell.continuity.client.processor.simple.VerticalSpriteProvider;

/**
 * 中文：对 Continuity 受保护槽位映射的只读精确版本桥接；继承保持安装引擎的表为权威，避免反射或复制第三方查找数据。
 *
 * English: Read-only exact-version bridge to Continuity's protected slot maps. Inheritance keeps
 * the installed engine authoritative without reflection or copied lookup tables.
 */
public final class ContinuityNativeSlotMaps {
    private static final String NATIVE_ENGINE = "Continuity";

    private ContinuityNativeSlotMaps() {}

    public static int[] horizontal() {
        return HorizontalBridge.snapshot();
    }

    public static int[] vertical() {
        return VerticalBridge.snapshot();
    }

    public static int[] horizontalVerticalPrimary() {
        return HorizontalVerticalBridge.primary();
    }

    public static int[] horizontalVerticalSecondary() {
        return HorizontalVerticalBridge.secondary();
    }

    public static int[] verticalHorizontalPrimary() {
        return VerticalHorizontalBridge.primary();
    }

    public static int[] verticalHorizontalSecondary() {
        return VerticalHorizontalBridge.secondary();
    }

    public static int[] compactRepresentatives() {
        CompactBridge bridge = new CompactBridge();
        return ContinuityCompactSlotPlanner.representatives(
                NATIVE_ENGINE,
                bridge::spriteIndex);
    }

    /**
     * 中文：直接调用 Continuity 的受保护 compact 选择器。
     * English: Calls Continuity's protected compact selector directly.
     */
    public static int[] compactSlots(int connections) {
        CompactBridge bridge = new CompactBridge();
        return ContinuityCompactSlotPlanner.slots(
                NATIVE_ENGINE,
                connections,
                bridge::spriteIndex);
    }

    public static final class CompactBridge extends CompactCtmQuadProcessor {
        private CompactBridge() {
            super(null, null, null, false, null, null);
        }

        private int spriteIndex(int quadrant, int connections) {
            return getSpriteIndex(quadrant, connections);
        }
    }

    public static final class HorizontalBridge extends HorizontalSpriteProvider {
        private HorizontalBridge() {
            super(null, null, false, null);
        }

        public static int[] snapshot() {
            return SPRITE_INDEX_MAP.clone();
        }
    }

    public static final class VerticalBridge extends VerticalSpriteProvider {
        private VerticalBridge() {
            super(null, null, false, null);
        }

        public static int[] snapshot() {
            return SPRITE_INDEX_MAP.clone();
        }
    }

    public static final class HorizontalVerticalBridge extends HorizontalVerticalSpriteProvider {
        private HorizontalVerticalBridge() {
            super(null, null, false, null);
        }

        public static int[] primary() {
            return SPRITE_INDEX_MAP.clone();
        }

        public static int[] secondary() {
            return SECONDARY_SPRITE_INDEX_MAP.clone();
        }
    }

    public static final class VerticalHorizontalBridge extends VerticalHorizontalSpriteProvider {
        private VerticalHorizontalBridge() {
            super(null, null, false, null);
        }

        public static int[] primary() {
            return SPRITE_INDEX_MAP.clone();
        }

        public static int[] secondary() {
            return SECONDARY_SPRITE_INDEX_MAP.clone();
        }
    }
}
