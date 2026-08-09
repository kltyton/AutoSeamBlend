package com.kltyton.autoseamblend.authoring.preview;

import com.kltyton.autoseamblend.runtime.surface.MinecraftSurfaceCatalog.FaceSurface;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.kltyton.autoseamblend.texture.mapping.NeighborConnections;
import java.util.Objects;
import net.minecraft.world.level.block.state.BlockState;

/** 中文：原生预览状态及其精确运行时纹理供体。 / English: Native preview state together with the exact runtime texture donor. */
public record PreviewSample(
        NeighborConnections connections,
        BlockState sourceState,
        FaceSurface sourceSurface,
        ConnectionMethod renderMethod) {
    public PreviewSample {
        Objects.requireNonNull(
                connections,
                "connections");
        Objects.requireNonNull(
                sourceState,
                "sourceState");
        Objects.requireNonNull(
                sourceSurface,
                "sourceSurface");
        Objects.requireNonNull(
                renderMethod,
                "renderMethod");
        if (renderMethod
                == ConnectionMethod.AUTO) {
            throw new IllegalArgumentException(
                    "preview render method must be resolved");
        }
    }
}
