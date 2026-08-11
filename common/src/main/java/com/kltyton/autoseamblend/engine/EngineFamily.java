package com.kltyton.autoseamblend.engine;

/** 中文：按确定性选择顺序排列的不同资源格式家族。 / English: Distinct resource-format families in deterministic selection order. */
public enum EngineFamily {
    MCPATCHER(0, "mcpatcher"),
    CTM_MOD(1, "ctm_mod"),
    FUSION(2, "fusion"),
    ATHENA(3, "athena");

    private final int stableOrder;
    private final String formatId;

    EngineFamily(int stableOrder, String formatId) {
        this.stableOrder = stableOrder;
        this.formatId = formatId;
    }

    public int stableOrder() {
        return stableOrder;
    }

    public String formatId() {
        return formatId;
    }
}
