package com.kltyton.autoseamblend.authoring.preview;

import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 中文：创作预览中由公共邻接判定选出的覆盖层供体。
 *
 * English: Overlay donor selected by common authoring-preview adjacency decisions.
 *
 * @param state 中文：供体方块状态。 / English: Donor block state.
 * @param surface 中文：供体面表面。 / English: Donor face surface.
 * @param method 中文：供体使用的连接方法。 / English: Connection method used by the donor.
 */
public record PreviewConnectionDonor(
        BlockState state,
        FaceSurface surface,
        ConnectionMethod method) {
    public PreviewConnectionDonor {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(method, "method");
    }
}
