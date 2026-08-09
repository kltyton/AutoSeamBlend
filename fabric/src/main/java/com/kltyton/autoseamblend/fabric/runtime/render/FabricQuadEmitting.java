package com.kltyton.autoseamblend.fabric.runtime.render;

import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;

/**
 * 中文：把 vanilla BakedQuad 经 FRAPI 1.21.1 的 fromVanilla 复制到 QuadEmitter 的共享入口；
 * 26.1 的 fromBakedQuad 在 1.21.1 中不存在。2 参数入口保留给 Athena（沿用默认材质与
 * quad.getDirection()）；显式 4 参数入口按调用方传入的 RenderMaterial/cullFace 原样写回
 * 元数据。Fusion overlay 使用 cutoutMaterial()，但仍由调用方恢复捕获的 source
 * cullFace/nominalFace/tag（在模型 prepareEmission 中统一处理），不假定 null cullFace。
 *
 * English: Shared entry that copies a vanilla BakedQuad into a QuadEmitter through FRAPI
 * 1.21.1's fromVanilla; 26.1's fromBakedQuad does not exist in 1.21.1. The 2-arg entry is
 * kept for Athena (default material plus quad.getDirection()); the explicit 4-arg entry writes
 * back the caller-provided RenderMaterial and cullFace as-is. The Fusion overlay path uses
 * cutoutMaterial() but still restores the captured source cullFace/nominalFace/tag, coordinated
 * by the model's prepareEmission; it never assumes a null cullFace.
 */
public final class FabricQuadEmitting {
    private FabricQuadEmitting() {
    }

    // 中文：Athena 路径保留原调用语义，不得改变。
    // English: Athena path keeps its original call semantics, unchanged.
    public static QuadEmitter fromBakedQuad(
            QuadEmitter emitter,
            BakedQuad quad) {
        return emitter.fromVanilla(
                quad,
                material(),
                quad.getDirection());
    }

    /**
     * 中文：Fusion 回放入口：使用被捕获的 RenderMaterial 与 cullFace 直接调用 fromVanilla；
     * BakedQuad 本身丢失这两个事实（overlay 在 SOLID 上发黑、玻璃板转角面被错误剔除）。
     *
     * English: Fusion replay entry: calls fromVanilla directly with the captured
     * RenderMaterial and cullFace; BakedQuad loses both facts (overlay renders black on
     * SOLID and glass-pane corner faces are wrongly culled otherwise).
     */
    public static QuadEmitter fromBakedQuad(
            QuadEmitter emitter,
            BakedQuad quad,
            RenderMaterial material,
            Direction cullFace) {
        return emitter.fromVanilla(
                quad,
                material,
                cullFace);
    }

    public static RenderMaterial material() {
        return RendererAccess.INSTANCE
                .getRenderer()
                .materialFinder()
                .find();
    }

    /**
     * 中文：overlay 专用 CUTOUT 材质；透明像素在 SOLID 方块上不再渲染为黑色。
     * English: Overlay-specific CUTOUT material; transparent pixels no longer render black
     * on SOLID blocks.
     */
    public static RenderMaterial cutoutMaterial() {
        return RendererAccess.INSTANCE
                .getRenderer()
                .materialFinder()
                .blendMode(BlendMode.CUTOUT)
                .find();
    }
}
