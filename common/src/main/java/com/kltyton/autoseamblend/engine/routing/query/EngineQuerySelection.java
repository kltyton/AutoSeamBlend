package com.kltyton.autoseamblend.engine.routing.query;

import com.kltyton.autoseamblend.engine.EngineFamily;
import com.kltyton.autoseamblend.engine.ownership.NativeSlot;
import com.kltyton.autoseamblend.reload.rule.ManagedRule;
import com.kltyton.autoseamblend.runtime.surface.PreparedSurfaceMethods;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.selection.query.QueryResolution;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：精确查询或摘要查询选出的引擎路由、Managed 文档和预准备方法快照。
 *
 * <p>English: Engine route, Managed document, and prepared-method snapshot selected by an exact or
 * summary query.
 */
public record EngineQuerySelection(
        EngineRouteSelection route,
        Optional<ManagedRule> managedRule,
        PreparedSurfaceMethods.Snapshot preparedMethods) {
    public EngineQuerySelection {
        Objects.requireNonNull(route, "route");
        managedRule = Objects.requireNonNull(managedRule, "managedRule");
        Objects.requireNonNull(preparedMethods, "preparedMethods");
    }

    public String engineId() {
        return route.engine().engineId();
    }

    public EngineFamily family() {
        return route.engine().family();
    }

    public EngineRouteSource source() {
        return route.provenance().source();
    }

    public List<NativeSlot> nativeSlots() {
        return route.nativeSlots();
    }

    public Optional<QueryResolution> resolution() {
        return route.resolution();
    }

    public ConnectionMethod method() {
        return route.method();
    }

    public boolean runsAutoBlend() {
        return route.runsAutoBlend();
    }

    public boolean fillsSlot(int slot) {
        return route.fillsSlot(slot);
    }

    public List<NativeSlot> protectedSlots() {
        return route.protectedSlots();
    }

    public ConnectionMethod resolveMethod(
            BlockState state,
            Direction direction,
            ResourceLocation spriteId) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(spriteId, "spriteId");
        return route.method() == ConnectionMethod.AUTO
                ? preparedMethods.method(state, direction, spriteId).orElse(ConnectionMethod.NONE)
                : route.method();
    }
}
