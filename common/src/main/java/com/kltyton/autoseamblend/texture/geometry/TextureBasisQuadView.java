package com.kltyton.autoseamblend.texture.geometry;

/**
 * 中文：提供纹理基向量推导所需的四边形顶点数据，不暴露具体 Loader 或渲染引擎类型。
 *
 * English: Supplies the quad vertex data needed to derive a texture basis without exposing a
 * loader or renderer-specific type.
 */
public interface TextureBasisQuadView {
    /**
     * 中文：返回四边形的世界法线方向。 / English: Returns the quad's world-facing direction.
     */
    WorldDirection face();

    /**
     * 中文：返回顶点的 U 坐标。 / English: Returns the vertex U coordinate.
     */
    float u(int vertex);

    /**
     * 中文：返回顶点的 V 坐标。 / English: Returns the vertex V coordinate.
     */
    float v(int vertex);

    /**
     * 中文：返回顶点位置的轴分量（0=x、1=y、2=z）。
     *
     * English: Returns a position component (0=x, 1=y, 2=z) for the vertex.
     */
    float position(int vertex, int component);
}
