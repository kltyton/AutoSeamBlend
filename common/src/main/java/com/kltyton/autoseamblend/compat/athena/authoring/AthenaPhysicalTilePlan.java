package com.kltyton.autoseamblend.compat.athena.authoring;

import com.kltyton.autoseamblend.compat.athena.runtime.AthenaConnectionState;
import com.kltyton.autoseamblend.compat.athena.runtime.AthenaCtmStateBridge;
import com.kltyton.autoseamblend.compat.athena.runtime.AthenaNativeProvider;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.geometry.TextureEdge;
import com.kltyton.autoseamblend.texture.generation.GeneratedStateRecipe;
import com.kltyton.autoseamblend.texture.generation.GeneratedTileRecipe;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import earth.terrarium.athena.api.client.utils.CtmState;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * 中文：把 Athena 原生 256→47 载体和较小逻辑域的预合成冻结为共同计划。
 * English: Freezes Athena's native 256-to-47 carrier and precomposition of smaller logical
 * domains into one shared plan.
 *
 * <p>The common plan owns the locked Athena provider and native {@code CtmState} bridge. Loader
 * callers therefore consume one deterministic 47-slot plan without parallel resolver facades.</p>
 */
public record AthenaPhysicalTilePlan(
        ConnectionMethod method,
        Carrier carrier,
        List<GeneratedTileRecipe> recipes,
        List<Boolean> topSourceTiles) {

    public AthenaPhysicalTilePlan {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(carrier, "carrier");
        recipes = List.copyOf(Objects.requireNonNull(recipes, "recipes"));
        topSourceTiles = List.copyOf(Objects.requireNonNull(topSourceTiles, "topSourceTiles"));
        if (method == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException("auto must be resolved before Athena planning");
        }
        if (recipes.size() != carrier.physicalSlots()
                || topSourceTiles.size() != carrier.physicalSlots()) {
            throw new IllegalArgumentException("recipe count differs from Athena carrier slots");
        }
    }

    /**
     * 中文：项目逻辑域计划；FIXED/TOP 保留单槽，其他需要连接的域预合成到 47 槽。
     * English: Project logical-domain plan; FIXED/TOP stay single-slot while other connecting
     * domains are precomposed into the 47-slot carrier.
     */
    public static AthenaPhysicalTilePlan forMethod(ConnectionMethod method) {
        Objects.requireNonNull(method, "method");
        return switch (method) {
            case AUTO -> throw new IllegalArgumentException(
                    "auto must be resolved before Athena planning");
            case NONE -> new AthenaPhysicalTilePlan(
                    method,
                    Carrier.PASSTHROUGH,
                    List.of(),
                    List.of());
            case FIXED -> singleSlot(method, false);
            case TOP -> singleSlot(method, true);
            default -> native47(method);
        };
    }

    /**
     * 中文：NeoForge 已接受原生导出载体；非 NONE 方法均按 Athena 47 槽输出，保留既有资源合同。
     * English: User-accepted NeoForge native export carrier; every non-NONE method uses Athena's
     * 47 slots, preserving the existing resource contract.
     */
    public static AthenaPhysicalTilePlan forNativeCarrier(ConnectionMethod method) {
        Objects.requireNonNull(method, "method");
        if (method == ConnectionMethod.AUTO || method == ConnectionMethod.NONE) {
            throw new IllegalArgumentException(method + " has no Athena native tile plan");
        }
        return native47(method);
    }

    /** 中文：解析一个 47 槽的原生代表状态。 / English: Resolves one representative state for a 47-slot native carrier. */
    public static AthenaConnectionState stateForSlot(int physicalSlot) {
        if (physicalSlot < 0 || physicalSlot >= Carrier.CTM_47.physicalSlots()) {
            throw new IllegalArgumentException("Athena physical slot must be inside [0,46]");
        }
        return AthenaConnectionState.fromConnections(
                representatives()[physicalSlot]);
    }

    /** 中文：把 47 槽代表状态转换为 Athena 原生状态。 / English: Converts one 47-slot representative to Athena's native state. */
    public static CtmState nativeState(int physicalSlot) {
        return AthenaCtmStateBridge.toNative(stateForSlot(physicalSlot));
    }

    /** 中文：让锁定的 Athena provider 选择一个物理槽。 / English: Lets the locked Athena provider select one physical slot. */
    public static int selectSlot(NeighborConnections connections) {
        Objects.requireNonNull(connections, "connections");
        return AthenaNativeProvider.select47(
                AthenaConnectionState.fromConnections(connections));
    }

    private static AthenaPhysicalTilePlan singleSlot(
            ConnectionMethod method,
            boolean topSource) {
        return new AthenaPhysicalTilePlan(
                method,
                Carrier.FIXED_1,
                List.of(GeneratedTileRecipe.Source.INSTANCE),
                List.of(topSource));
    }

    private static AthenaPhysicalTilePlan native47(ConnectionMethod method) {
        NeighborConnections[] representatives = representatives();
        return new AthenaPhysicalTilePlan(
                method,
                Carrier.CTM_47,
                IntStream.range(0, Carrier.CTM_47.physicalSlots())
                        .mapToObj(slot -> GeneratedStateRecipe.forConnections(
                                method,
                                recipeConnections(method, representatives[slot])))
                        .toList(),
                Arrays.stream(representatives)
                        .map(value -> method == ConnectionMethod.TOP
                                && value.connected(TextureEdge.UP))
                        .toList());
    }

    private static NeighborConnections recipeConnections(
            ConnectionMethod method,
            NeighborConnections nativeConnections) {
        return switch (method) {
            case RUNTIME_BLEND, OVERLAY, OVERLAY_CTM ->
                    NeighborConnections.fromBits(nativeConnections.bits() ^ 0xFF);
            default -> nativeConnections;
        };
    }

    private static NeighborConnections[] representatives() {
        NeighborConnections[] representatives =
                new NeighborConnections[Carrier.CTM_47.physicalSlots()];
        for (int bits = 0; bits <= 0xFF; bits++) {
            NeighborConnections candidate = NeighborConnections.fromBits(bits);
            if (candidate.normalizedCtmBits() != bits) {
                continue;
            }
            int slot = AthenaNativeProvider.select47(
                    AthenaConnectionState.fromConnections(candidate));
            if (slot < 0 || slot >= representatives.length) {
                throw new IllegalStateException(
                        "Athena native provider returned a slot outside [0,46]: " + slot);
            }
            if (representatives[slot] == null) {
                representatives[slot] = candidate;
            }
        }
        if (Arrays.stream(representatives).anyMatch(Objects::isNull)) {
            throw new IllegalStateException(
                    "Athena native provider does not cover every 47-state slot");
        }
        return representatives;
    }

    public enum Carrier {
        PASSTHROUGH(0),
        FIXED_1(1),
        CTM_47(47);

        private final int physicalSlots;

        Carrier(int physicalSlots) {
            this.physicalSlots = physicalSlots;
        }

        public int physicalSlots() {
            return physicalSlots;
        }
    }
}
