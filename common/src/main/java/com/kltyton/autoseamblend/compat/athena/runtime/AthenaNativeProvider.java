package com.kltyton.autoseamblend.compat.athena.runtime;

import earth.terrarium.athena.api.client.models.AthenaQuad;
import earth.terrarium.athena.api.client.utils.CtmState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/**
 * 中文：集中 Athena 4.0.6 原生 corner 选择（CtmUtils 五角色布局槽 + AthenaQuad.withState），
 * 避免两个 Loader 维护平行槽位算法。直接使用 7 参 withState（javap 证实其内部调用
 * CtmUtils.getTexture(up,left,upLeft) 并返回 0..4 布局槽），不经 ConnectedTextureMap：
 * 4.0.6 的 getTexture 依赖只在 getTextures() 内填充的 textureMap，未填充时直接 NPE，
 * 且 map 内部态对纯槽选择没有必要。
 *
 * English: Centralizes Athena 4.0.6's native corner selection (CtmUtils five-role layout
 * slots + AthenaQuad.withState) so the Loaders do not maintain parallel slot-selection
 * algorithms. Uses the 7-arg withState directly (javap proves it calls
 * CtmUtils.getTexture(up,left,upLeft) internally and returns the 0..4 layout slot),
 * bypassing ConnectedTextureMap: 4.0.6's getTexture depends on the textureMap that only
 * getTextures() fills and throws NPE when unfilled, and the map's internal state is
 * unnecessary for pure slot selection.
 */
public final class AthenaNativeProvider {
    /** 中文：Athena 4.0.6 原生五角色材质数量（particle/empty/center/vertical/horizontal）。 / English: Athena 4.0.6's native five-role material count (particle/empty/center/vertical/horizontal). */
    public static final int ROLE_COUNT = 5;

    private AthenaNativeProvider() {}

    /**
     * 中文：按 4.0.6 原生 corner 选择获取四个半面 Quad；调用方负责验证槽位和材质生命周期。
     *
     * English: Gets the four half-face Quads via 4.0.6's native corner selection; callers
     * validate slots and material lifecycle at their loader boundary.
     */
    public static List<AthenaQuad> quads(
            CtmState state,
            Direction face,
            TextureAtlasSprite[] stateSprites) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(face, "face");
        if (stateSprites.length != ROLE_COUNT) {
            throw new IllegalArgumentException(
                    "Athena native carrier must provide exactly five role sprites: "
                            + stateSprites.length);
        }
        // 中文：4.0.6 官方 ConnectedBlockModel 字节码合同：allTrue 快路径返回单个
        // AthenaQuad.withSprite(1) 完整面（槽 1、bounds 0..1），而非四个象限；四个
        // 象限仅用于非 allTrue 状态。仍使用 7 参原生 withState，不引入 ConnectedTextureMap。
        // English: 4.0.6 official ConnectedBlockModel bytecode contract: the allTrue fast
        // path returns a single AthenaQuad.withSprite(1) full face (slot 1, bounds 0..1)
        // instead of four quadrants; quadrants are only used for non-allTrue states. Still
        // uses the 7-arg native withState and never reintroduces ConnectedTextureMap.
        if (state.allTrue()) {
            return List.of(AthenaQuad.withSprite(1));
        }
        ArrayList<AthenaQuad> output = new ArrayList<>(4);
        output.add(AthenaQuad.withState(
                state.up(),
                state.left(),
                state.upLeft(),
                0.0F,
                0.5F,
                1.0F,
                0.5F));
        output.add(AthenaQuad.withState(
                state.up(),
                state.right(),
                state.upRight(),
                0.5F,
                1.0F,
                1.0F,
                0.5F));
        output.add(AthenaQuad.withState(
                state.down(),
                state.left(),
                state.downLeft(),
                0.0F,
                0.5F,
                0.5F,
                0.0F));
        output.add(AthenaQuad.withState(
                state.down(),
                state.right(),
                state.downRight(),
                0.5F,
                1.0F,
                0.5F,
                0.0F));
        return List.copyOf(output);
    }
}
