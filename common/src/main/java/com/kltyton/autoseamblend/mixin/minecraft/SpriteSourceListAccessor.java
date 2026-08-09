package com.kltyton.autoseamblend.mixin.minecraft;

import java.util.List;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 中文：在同一次将要缝合来源的重载中公开有序 Atlas 来源。 / English: Exposes ordered atlas sources during the same reload that will stitch them. */
@Mixin(SpriteSourceList.class)
public interface SpriteSourceListAccessor {
    @Accessor("sources")
    List<SpriteSource> autoseamblend$sources();
}
