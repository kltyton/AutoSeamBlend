package com.kltyton.autoseamblend.engine.ownership;

/** 中文：归一化的原生槽位证据；原生声明与 PNG 资源存在性是两个独立事实。 / English: Normalized native-slot evidence. Native declaration and PNG resource presence are independent facts. */
public enum NativeSlotIntent {
    PRESENT(false, true, true),
    DEFAULT(false, true, false),
    SKIP(false, true, false),
    DECLARED_MISSING(true, true, false),
    OMITTED(true, false, false),
    UNKNOWN(false, false, false);

    private final boolean fillable;
    private final boolean declaredByNativeDocument;
    private final boolean pngResourcePresent;

    NativeSlotIntent(
            boolean fillable,
            boolean declaredByNativeDocument,
            boolean pngResourcePresent) {
        this.fillable = fillable;
        this.declaredByNativeDocument = declaredByNativeDocument;
        this.pngResourcePresent = pngResourcePresent;
    }

    public boolean fillable() {
        return fillable;
    }

    public boolean protectedIntent() {
        return !fillable;
    }

    public boolean declaredByNativeDocument() {
        return declaredByNativeDocument;
    }

    public boolean pngResourcePresent() {
        return pngResourcePresent;
    }
}
