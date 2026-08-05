package com.kltyton.autoseamblend.runtime.surface;

import com.kltyton.autoseamblend.engine.query.SurfaceFace;

/**
 * 中文：提供完整表面判定所需的四边形事实，不暴露 Loader 的 BakedQuad 类型。
 *
 * English: Supplies quad facts needed for full-surface classification without exposing a
 * Loader-specific BakedQuad type.
 */
public interface SurfaceQuadView {
    SurfaceFace face();

    int vertexCount();

    float position(int vertex, int component);

    float u(int vertex);

    float v(int vertex);

    float spriteMinU();

    float spriteMaxU();

    float spriteMinV();

    float spriteMaxV();
}
