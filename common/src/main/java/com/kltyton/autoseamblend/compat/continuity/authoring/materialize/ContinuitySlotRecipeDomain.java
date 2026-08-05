package com.kltyton.autoseamblend.compat.continuity.authoring.materialize;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.generation.ContinuitySlotRecipePlanner;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe;
import java.util.List;
import me.pepperbell.continuity.client.processor.simple.CtmSpriteProvider;

/**
 * 中文：把锁定 Continuity ABI 的精确原生槽位快照接入公共配方规划器。
 * English: Connects the locked Continuity ABI's exact native slot snapshot to the shared recipe
 * planner.
 */
public final class ContinuitySlotRecipeDomain {
    private static final ContinuitySlotRecipePlanner PLANNER =
            ContinuitySlotRecipePlanner.create(
                    CtmSpriteProvider.SPRITE_INDEX_MAP,
                    ContinuityNativeSlotMaps.compactRepresentatives(),
                    ContinuityNativeSlotMaps.horizontal(),
                    ContinuityNativeSlotMaps.vertical(),
                    ContinuityNativeSlotMaps.horizontalVerticalPrimary(),
                    ContinuityNativeSlotMaps.horizontalVerticalSecondary(),
                    ContinuityNativeSlotMaps.verticalHorizontalPrimary(),
                    ContinuityNativeSlotMaps.verticalHorizontalSecondary());

    private ContinuitySlotRecipeDomain() {}

    public static GeneratedTileRecipe recipe(ConnectionMethod method, int slot) {
        return PLANNER.recipe(method, slot);
    }

    public static List<Integer> slots(ConnectionMethod method) {
        return PLANNER.slots(method);
    }
}
