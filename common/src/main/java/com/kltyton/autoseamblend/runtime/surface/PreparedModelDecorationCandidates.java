package com.kltyton.autoseamblend.runtime.surface;

import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：从同代预缝合方法表构建烘焙阶段的候选 state 集合，供各引擎生命周期共享。
 *
 * English:
 * Builds the bake-stage candidate state set from the same-generation pre-stitch method table,
 * shared by every engine lifecycle.
 */
public final class PreparedModelDecorationCandidates {
    private PreparedModelDecorationCandidates() {}

    /**
     * 中文：identity 集合收集 resolved 非 NONE 的方法 state 与 auto 代表 state。
     *
     * English:
     * An identity-backed set collecting non-NONE resolved method states and auto representative
     * states.
     */
    public static Set<BlockState> states(
            PreparedSurfaceMethods.Snapshot snapshot) {
        IdentityHashMap<BlockState, Boolean> candidates =
                new IdentityHashMap<>();
        Objects.requireNonNull(snapshot, "snapshot")
                .methods()
                .forEach((key, method) -> {
                    if (method.decision()
                            .resolvedMethod()
                            .filter(resolved ->
                                    resolved
                                            != ConnectionMethod.NONE)
                            .isPresent()) {
                        candidates.put(
                                key.state(),
                                Boolean.TRUE);
                    }
                });
        snapshot.autoMethods()
                .forEach((block, auto) ->
                        candidates.put(
                                auto.state(),
                                Boolean.TRUE));
        return Collections.unmodifiableSet(
                candidates.keySet());
    }
}
