package com.kltyton.autoseamblend.authoring.preview;

import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import java.util.Objects;
import net.minecraft.world.level.block.state.BlockState;

/** 中文：创作预览中由公共邻接判定选出的覆盖层供体。 / English: Overlay donor selected by common authoring-preview adjacency decisions. */
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
