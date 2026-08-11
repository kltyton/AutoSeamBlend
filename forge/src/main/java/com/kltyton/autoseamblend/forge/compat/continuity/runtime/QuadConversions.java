package com.kltyton.autoseamblend.forge.compat.continuity.runtime;

import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * 中文：1.20.1 Forge Continuity/Constancy 与 FRAPI 的共享 Quad 转换及缺失精灵判定；
 * 26.1 的 MutableQuad.setFrom 与 RenderUtil.isMissingSprite 在 1.20.1 中不存在。
 *
 * English: Shared quad conversion and missing-sprite detection for the 1.20.1 Forge
 * Continuity/Constancy and FRAPI surface; 26.1's MutableQuad.setFrom and
 * RenderUtil.isMissingSprite do not exist in 1.20.1.
 */
public final class QuadConversions {
    private QuadConversions() {
    }

    public static MutableQuadView fromBakedQuad(BakedQuad quad) {
        QuadEmitter emitter = RendererAccess.INSTANCE
                .getRenderer()
                .meshBuilder()
                .getEmitter();
        return emitter.fromVanilla(
                quad,
                material(),
                quad.getDirection());
    }

    public static RenderMaterial material() {
        return RendererAccess.INSTANCE
                .getRenderer()
                .materialFinder()
                .find();
    }

    public static boolean isMissingSprite(TextureAtlasSprite sprite) {
        return sprite == null
                || MissingTextureAtlasSprite.getLocation()
                        .equals(sprite.contents().name());
    }
}
