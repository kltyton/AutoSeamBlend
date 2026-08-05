package com.kltyton.autoseamblend.compat.athena.runtime;

import com.kltyton.autoseamblend.compat.athena.authoring.AthenaPhysicalTilePlan;
import earth.terrarium.athena.api.client.models.AthenaQuad;
import earth.terrarium.athena.api.client.utils.CtmState;
import earth.terrarium.athena.impl.client.models.ctm.FourtySevenSliceCtmProvider;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * 中文：集中 Athena 4.7.3 原生 256→47 provider，避免两个 Loader 维护平行槽位算法。
 * English: Centralizes Athena 4.7.3's native 256-to-47 provider so the Loaders do not maintain
 * parallel slot-selection algorithms.
 */
public final class AthenaNativeProvider {
    private static final int TILE_COUNT =
            AthenaPhysicalTilePlan.Carrier.CTM_47.physicalSlots();
    private static final FourtySevenSliceCtmProvider PROVIDER =
            new FourtySevenSliceCtmProvider(IntStream.range(0, TILE_COUNT).toArray());

    private AthenaNativeProvider() {}

    /**
     * 中文：返回唯一原生槽，否则返回 -1。
     * English: Returns the unique native slot, or -1 when the provider does not select exactly one.
     */
    public static int select47(AthenaConnectionState state) {
        return select47(AthenaCtmStateBridge.toNative(
                Objects.requireNonNull(state, "state")));
    }

    /**
     * 中文：返回唯一原生槽，否则返回 -1。
     * English: Returns the unique native slot, or -1 when the native state is ambiguous.
     */
    public static int select47(CtmState state) {
        List<AthenaQuad> selected = quads(state);
        return selected.size() == 1 ? selected.getFirst().sprite() : -1;
    }

    /**
     * 中文：按锁定 provider 获取原生 Quad；调用方负责验证槽位和材质生命周期。
     * English: Gets native Quads from the locked provider; callers validate slots and material
     * lifecycle at their loader boundary.
     */
    public static List<AthenaQuad> quads(CtmState state) {
        return PROVIDER.get(Objects.requireNonNull(state, "state"), 0.0F);
    }
}
