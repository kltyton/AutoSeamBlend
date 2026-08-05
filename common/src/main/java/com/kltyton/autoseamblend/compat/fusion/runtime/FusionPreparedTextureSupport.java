package com.kltyton.autoseamblend.compat.fusion.runtime;

import com.kltyton.autoseamblend.compat.fusion.texture.generation.FusionNativeSheetPlan;
import com.kltyton.autoseamblend.selection.method.ConnectionMethod;
import com.mojang.blaze3d.platform.Transparency;
import com.supermartijn642.fusion.api.model.custom.quad.MutableQuad;
import com.supermartijn642.fusion.api.model.custom.quad.QuadAccess;
import com.supermartijn642.fusion.api.texture.custom.BlockStateQuadProcessor;
import com.supermartijn642.fusion.api.texture.custom.SpriteInstance;
import com.supermartijn642.fusion.api.texture.custom.TextureInstance;
import com.supermartijn642.fusion.api.texture.types.base.BaseTextureData;
import com.supermartijn642.fusion.api.texture.types.connecting.ConnectingTextureData;
import com.supermartijn642.fusion.api.texture.types.connecting.predicates.ConnectionPredicate;
import com.supermartijn642.fusion.api.util.PropertyStore;
import com.supermartijn642.fusion.texture.custom.SpriteInstanceImpl;
import com.supermartijn642.fusion.texture.custom.TextureInstanceImpl;
import com.supermartijn642.fusion.texture.types.connecting.StitchedConnectingTextureData;
import com.supermartijn642.fusion.texture.types.connecting.layouts.ConnectingTextureLayoutHandler;
import com.supermartijn642.fusion.util.FallbackPropertyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

/**
 * 中文：集中保存 bake 阶段的 Fusion 纹理和 Quad processor 构造，供 route 与 overlay 共用。
 * English: Centralizes bake-time Fusion texture and Quad processor construction shared by routes
 * and overlays.
 */
public final class FusionPreparedTextureSupport {
    private FusionPreparedTextureSupport() {}

    public static TextureAtlasSprite[] indexedSprites(
            Map<String, TextureAtlasSprite> slots,
            ConnectionMethod method) {
        var handler = ConnectingTextureLayoutHandler.get(
                FusionNativeSheetPlan.nativeLayout(method));
        int expected = Math.multiplyExact(handler.getWidth(), handler.getHeight());
        if (slots.size() != expected) {
            return null;
        }
        TextureAtlasSprite[] sprites = new TextureAtlasSprite[expected];
        for (Map.Entry<String, TextureAtlasSprite> entry : slots.entrySet()) {
            int slot;
            try {
                slot = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException exception) {
                return null;
            }
            if (slot < 0 || slot >= expected || sprites[slot] != null) {
                return null;
            }
            sprites[slot] = entry.getValue();
        }
        for (TextureAtlasSprite sprite : sprites) {
            if (sprite == null) {
                return null;
            }
        }
        return sprites;
    }

    public static PreparedBase prepareBase(
            TextureAtlasSprite target,
            Transparency transparency,
            QuadAccess sourceQuad,
            PropertyStore rootProperties) {
        BaseTextureData data = BaseTextureData.builder()
                .renderType(renderType(transparency))
                .build();
        TextureInstance<BaseTextureData> texture = baseTexture(target, data);
        SpriteInstance sprite = texture.getDefaultSprite();
        MutableQuad initialized = MutableQuad.create().copyFrom(sourceQuad);
        PropertyStore properties = FallbackPropertyStore.create(rootProperties);
        BlockStateQuadProcessor<?> processor = texture.initializeBlockStateModelQuad(
                initialized, sprite, properties);
        return processor == null ? null : new PreparedBase(
                initialized.createCopy(), sprite, castProcessor(processor), properties);
    }

    public static PreparedConnecting prepareConnecting(
            ConnectionMethod method,
            TextureAtlasSprite[] generated,
            Identifier textureIdentifier,
            TextureAtlasSprite sourceSprite,
            QuadAccess sourceQuad,
            Transparency transparency,
            ConnectionPredicate predicate,
            PropertyStore rootProperties,
            boolean transparentLayers) {
        BaseTextureData.RenderType renderType = renderType(transparency);
        BaseTextureData tileData = BaseTextureData.builder().renderType(renderType).build();
        ArrayList<TextureInstance<?>> tiles = new ArrayList<>(generated.length);
        for (TextureAtlasSprite sprite : generated) {
            tiles.add(baseTexture(sprite, tileData));
        }
        ConnectingTextureData data = ConnectingTextureData.builder()
                .layout(FusionNativeSheetPlan.nativeLayout(method))
                .renderType(renderType)
                .connectionPredicate(predicate)
                .build();
        var stitched = new StitchedConnectingTextureData(data, List.copyOf(tiles));
        var texture = new TextureInstanceImpl<StitchedConnectingTextureData>(
                FusionNativeTextureTypes.connectingType(), textureIdentifier, stitched);
        var sprite = new SpriteInstanceImpl(texture, sourceSprite, textureIdentifier);
        texture.setSprites(List.of(sprite), sprite);
        MutableQuad initialized = MutableQuad.create().copyFrom(sourceQuad);
        if (transparentLayers) {
            initialized.renderLayers(Transparency.TRANSPARENT);
        }
        PropertyStore properties = FallbackPropertyStore.create(rootProperties);
        BlockStateQuadProcessor<?> processor = texture.initializeBlockStateModelQuad(
                initialized, sprite, properties);
        return processor == null ? null : new PreparedConnecting(
                initialized.createCopy(), sprite, castProcessor(processor), properties);
    }

    private static TextureInstance<BaseTextureData> baseTexture(
            TextureAtlasSprite sprite,
            BaseTextureData data) {
        var texture = new TextureInstanceImpl<BaseTextureData>(
                FusionNativeTextureTypes.baseType(), sprite.contents().name(), data);
        var instance = new SpriteInstanceImpl(
                texture, sprite, sprite.contents().name());
        texture.setSprites(List.of(instance), instance);
        return texture;
    }

    private static BaseTextureData.RenderType renderType(Transparency transparency) {
        if (transparency.hasTranslucent()) return BaseTextureData.RenderType.TRANSLUCENT;
        if (transparency.hasTransparent()) return BaseTextureData.RenderType.CUTOUT;
        return BaseTextureData.RenderType.OPAQUE;
    }

    /** 中文：集中封装 Fusion 擦除态 processor 的安全桥接。 / English: Centralizes the safe bridge for Fusion's erased processor state. */
    @SuppressWarnings("unchecked")
    public static BlockStateQuadProcessor<Object> castProcessor(
            BlockStateQuadProcessor<?> processor) {
        Objects.requireNonNull(processor, "processor");
        return (BlockStateQuadProcessor<Object>) processor;
    }

    public record PreparedBase(
            QuadAccess quad,
            SpriteInstance sprite,
            BlockStateQuadProcessor<Object> processor,
            PropertyStore properties) {}

    public record PreparedConnecting(
            QuadAccess quad,
            SpriteInstance sprite,
            BlockStateQuadProcessor<Object> processor,
            PropertyStore properties) {}
}
