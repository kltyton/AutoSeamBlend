package com.kltyton.autoseamblend.engine.ownership;

import java.util.Objects;
import java.util.Optional;

/** 中文：一个方法槽位的归一化原生资源证据。 / English: Normalized native-resource evidence for one method slot. */
public record NativeSlot(int index, NativeSlotIntent intent, Optional<String> spriteId) {
    public NativeSlot {
        if (index < 0) throw new IllegalArgumentException("index must be non-negative");
        Objects.requireNonNull(intent, "intent");
        spriteId = Objects.requireNonNull(spriteId, "spriteId");
        if ((intent == NativeSlotIntent.PRESENT
                        || intent == NativeSlotIntent.DECLARED_MISSING)
                && spriteId.isEmpty()) {
            throw new IllegalArgumentException(
                    "present or declared-missing slots must retain their native sprite id");
        }
    }

    public boolean declaredByNativeDocument() {
        return intent.declaredByNativeDocument();
    }

    public boolean pngResourcePresent() {
        return intent.pngResourcePresent();
    }
}
